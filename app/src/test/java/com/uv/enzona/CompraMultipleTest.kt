package com.uv.enzona

import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.model.ErrorCompra
import com.uv.enzona.data.model.EstadoAsiento
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.EstadoPago
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.SolicitudCompra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * CP-MULTI-*: compra de varios boletos en una sola orden, contra el
 * repositorio en memoria (no contra backend ni pasarela real).
 *
 * Historial: antes de la corrección, `comprarOConfirmar` emitía un boleto por
 * llamada y la regla "ya tienes boleto" bloqueaba la segunda compra del mismo
 * evento; esa reproducción se documentó en la primera versión de este archivo.
 * CP-MULTI-09 conserva esa comprobación invertida: ahora la segunda compra de
 * pago sí se permite y la política de una confirmación gratuita por persona se
 * mantiene.
 */
class CompraMultipleTest {

    private val asistente = 4L   // asistente@uv.mx en los datos de muestra
    private val otroAsistente = 3L

    private var contador = 0

    private fun eventoSinAsiento(aforo: Int = 10, precio: Double = 50.0): Evento = MockRepository.crearEvento(
        organizadorId = 1, nombre = "CP-MULTI sin asiento ${contador++}", descripcion = "", lugar = "Sede",
        direccion = "", categoria = "Música", fecha = LocalDateTime.of(2026, 10, 3, 19, 0), latitud = 18.851, longitud = -97.1,
        esDePago = true, precio = precio, aforo = aforo, publicar = true,
    )

    private fun eventoConAsientos(filas: Int = 2, porFila: Int = 3, precio: Double = 120.0): Evento = MockRepository.crearEvento(
        organizadorId = 1, nombre = "CP-MULTI con asientos ${contador++}", descripcion = "", lugar = "Teatro",
        direccion = "", categoria = "Teatro", fecha = LocalDateTime.of(2026, 10, 15, 18, 30), latitud = 18.851, longitud = -97.1,
        esDePago = true, precio = precio, aforo = filas * porFila, publicar = true,
        requiereAsiento = true, filas = filas, asientosPorFila = porFila, seccion = "Luneta",
    )

    private fun eventoGratuito(): Evento = MockRepository.crearEvento(
        organizadorId = 1, nombre = "CP-MULTI gratuito ${contador++}", descripcion = "", lugar = "Plaza",
        direccion = "", categoria = "Cultural", fecha = LocalDateTime.of(2026, 9, 25, 10, 0), latitud = 18.851, longitud = -97.1,
        esDePago = false, precio = 0.0, aforo = 5, publicar = true,
    )

    private fun tipoDe(evento: Evento) = MockRepository.tiposDe(evento.id).first()

    @Test
    fun `CP-MULTI-01 comprar 2 boletos sin asiento emite una orden con dos QR y descuenta 2`() {
        val evento = eventoSinAsiento(aforo = 10, precio = 50.0)
        val tipo = tipoDe(evento)

        val r = MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, cantidad = 2, claveOperacion = "op-01"))
        val compra = r.getOrThrow()

        assertEquals(2, compra.boletos.size)
        assertEquals(100.0, compra.total, 0.0001)
        assertEquals(EstadoOrden.PAGADA, compra.orden.estado)
        assertEquals(EstadoPago.APROBADO, compra.pago!!.estado)
        assertEquals(100.0, compra.pago!!.monto, 0.0001)
        assertEquals(1, compra.boletos.map { it.orden.id }.distinct().size)
        assertEquals(2, compra.boletos.map { it.codigo }.distinct().size)
        assertEquals(2, compra.boletos.map { it.contenidoQr }.distinct().size)
        assertEquals(8, MockRepository.obtenerEvento(evento.id)!!.disponibles)
        assertEquals(8, MockRepository.tiposDe(evento.id).first().cantidadDisponible)
        assertEquals(10, MockRepository.obtenerEvento(evento.id)!!.aforo) // aforo = capacidad, no saldo
    }

    @Test
    fun `CP-MULTI-02 comprar 3 boletos con asiento ocupa tres localidades distintas en la misma orden`() {
        val evento = eventoConAsientos()
        val tipo = tipoDe(evento)
        val libres = MockRepository.asientosDe(evento.id).filter { it.libre }.take(3).map { it.id }

        val compra = MockRepository.comprar(
            asistente, SolicitudCompra(evento.id, tipo.id, cantidad = 3, asientosIds = libres, claveOperacion = "op-02")
        ).getOrThrow()

        assertEquals(3, compra.boletos.size)
        assertEquals(360.0, compra.total, 0.0001)
        assertEquals(3, compra.boletos.map { it.asiento!!.id }.distinct().size)
        assertEquals(libres.toSet(), compra.boletos.map { it.asiento!!.id }.toSet())
        assertEquals(1, compra.boletos.map { it.orden.id }.distinct().size)
        libres.forEach { assertEquals(EstadoAsiento.OCUPADO, MockRepository.obtenerAsiento(it)!!.estado) }
        assertEquals(3, MockRepository.asientosLibres(evento.id))
        assertEquals(3, MockRepository.obtenerEvento(evento.id)!!.disponibles)
    }

    @Test
    fun `CP-MULTI-03 pedir 3 boletos con solo 2 asientos no continua y dice cuantos faltan`() {
        val evento = eventoConAsientos()
        val tipo = tipoDe(evento)
        val dos = MockRepository.asientosDe(evento.id).filter { it.libre }.take(2).map { it.id }
        val antes = MockRepository.obtenerEvento(evento.id)!!.disponibles

        val r = MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, cantidad = 3, asientosIds = dos))

        val error = r.exceptionOrNull()
        assertTrue(error is ErrorCompra.AsientosInvalidos)
        assertTrue(error!!.message!!.contains("Faltan 1"))
        assertEquals(antes, MockRepository.obtenerEvento(evento.id)!!.disponibles)
        assertEquals(0, MockRepository.boletosDeEvento(evento.id).size)
        dos.forEach { assertTrue(MockRepository.obtenerAsiento(it)!!.libre) }
    }

    @Test
    fun `CP-MULTI-04 la seleccion con asientos repetidos se rechaza sin emitir nada`() {
        val evento = eventoConAsientos()
        val tipo = tipoDe(evento)
        val a = MockRepository.asientosDe(evento.id).first { it.libre }.id

        val r = MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, cantidad = 2, asientosIds = listOf(a, a)))

        assertTrue(r.exceptionOrNull() is ErrorCompra.AsientosInvalidos)
        assertEquals(0, MockRepository.boletosDeEvento(evento.id).size)
    }

    @Test
    fun `CP-MULTI-05 solicitar 3 cuando quedan 2 se impide sin emision parcial`() {
        val evento = eventoSinAsiento(aforo = 2)
        val tipo = tipoDe(evento)

        val r = MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, cantidad = 3))

        val error = r.exceptionOrNull()
        assertTrue(error is ErrorCompra.SinCupo)
        assertEquals(2, (error as ErrorCompra.SinCupo).disponibles)
        assertEquals(0, MockRepository.boletosDeEvento(evento.id).size)
        assertEquals(2, MockRepository.obtenerEvento(evento.id)!!.disponibles)
        assertEquals(2, MockRepository.tiposDe(evento.id).first().cantidadDisponible)
    }

    @Test
    fun `CP-MULTI-06 pago rechazado de 3 boletos no emite ni descuenta y permite reintentar`() {
        val evento = eventoConAsientos()
        val tipo = tipoDe(evento)
        val tres = MockRepository.asientosDe(evento.id).filter { it.libre }.take(3).map { it.id }
        val solicitud = SolicitudCompra(evento.id, tipo.id, cantidad = 3, asientosIds = tres, claveOperacion = "op-06")

        val rechazo = MockRepository.comprar(asistente, solicitud, pagoAprobado = false)

        assertTrue(rechazo.exceptionOrNull() is ErrorCompra.PagoRechazado)
        assertEquals(0, MockRepository.boletosDeEvento(evento.id).size)
        assertEquals(6, MockRepository.obtenerEvento(evento.id)!!.disponibles)
        tres.forEach { assertTrue(MockRepository.obtenerAsiento(it)!!.libre) }
        val ordenRechazada = MockRepository.ordenes.last { it.eventoId == evento.id }
        assertEquals(EstadoOrden.CANCELADA, ordenRechazada.estado)
        assertEquals(EstadoPago.RECHAZADO, MockRepository.pagos.last { it.ordenId == ordenRechazada.id }.estado)

        // Reintento con la misma selección: ahora aprobado
        val ok = MockRepository.comprar(asistente, solicitud, pagoAprobado = true).getOrThrow()
        assertEquals(3, ok.boletos.size)
        assertNotEquals(ordenRechazada.id, ok.orden.id)
        assertEquals(3, MockRepository.obtenerEvento(evento.id)!!.disponibles)
    }

    @Test
    fun `CP-MULTI-07 doble toque con la misma clave de operacion emite una sola vez`() {
        val evento = eventoSinAsiento(aforo = 10)
        val tipo = tipoDe(evento)
        val solicitud = SolicitudCompra(evento.id, tipo.id, cantidad = 2, claveOperacion = "op-07-doble")

        val primera = MockRepository.comprar(asistente, solicitud).getOrThrow()
        val segunda = MockRepository.comprar(asistente, solicitud).getOrThrow()

        assertEquals(primera.orden.id, segunda.orden.id)
        assertEquals(2, MockRepository.boletosDeEvento(evento.id).size)
        assertEquals(8, MockRepository.obtenerEvento(evento.id)!!.disponibles)
        assertEquals(1, MockRepository.pagos.count { it.ordenId == primera.orden.id })
    }

    @Test
    fun `CP-MULTI-08 dos compras que compiten por un asiento - solo una lo conserva y la otra no recibe compra parcial`() {
        val evento = eventoConAsientos()
        val tipo = tipoDe(evento)
        val libres = MockRepository.asientosDe(evento.id).filter { it.libre }.map { it.id }
        val disputado = libres[0]

        val primera = MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, 2, listOf(disputado, libres[1]))).getOrThrow()
        val segunda = MockRepository.comprar(otroAsistente, SolicitudCompra(evento.id, tipo.id, 2, listOf(disputado, libres[2])))

        assertEquals(2, primera.boletos.size)
        val error = segunda.exceptionOrNull()
        assertTrue(error is ErrorCompra.AsientosOcupados)
        assertEquals(1, (error as ErrorCompra.AsientosOcupados).etiquetas.size)
        // El asiento no disputado de la segunda compra sigue libre: no hubo compra parcial
        assertTrue(MockRepository.obtenerAsiento(libres[2])!!.libre)
        assertEquals(2, MockRepository.boletosDeEvento(evento.id).size)
    }

    @Test
    fun `CP-MULTI-09 una compra adicional de pago se permite y la confirmacion gratuita sigue siendo una por persona`() {
        val pago = eventoSinAsiento(aforo = 10)
        val tipo = tipoDe(pago)
        MockRepository.comprar(asistente, SolicitudCompra(pago.id, tipo.id, 1)).getOrThrow()
        val adicional = MockRepository.comprar(asistente, SolicitudCompra(pago.id, tipo.id, 2))
        assertTrue(adicional.isSuccess)
        assertEquals(3, MockRepository.boletosDe(asistente).count { it.evento.id == pago.id })

        val gratis = eventoGratuito()
        MockRepository.comprar(asistente, SolicitudCompra(gratis.id, null, 1)).getOrThrow()
        val repetida = MockRepository.comprar(asistente, SolicitudCompra(gratis.id, null, 1))
        assertTrue(repetida.exceptionOrNull() is ErrorCompra.PoliticaUsuario)
        val dosGratis = MockRepository.comprar(otroAsistente, SolicitudCompra(gratis.id, null, 2))
        assertTrue(dosGratis.exceptionOrNull() is ErrorCompra.PoliticaUsuario)
    }

    @Test
    fun `CP-MULTI-10 validar uno de los tres QR consume solo ese boleto y el reintento se rechaza`() {
        val evento = eventoSinAsiento(aforo = 10)
        val tipo = tipoDe(evento)
        val compra = MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, 3)).getOrThrow()
        val (b1, b2, b3) = compra.boletos

        val r1 = MockRepository.validarQr(b1.contenidoQr, validadorId = 2)
        assertTrue(r1.permitido)
        assertEquals(EstadoBoleto.USADO, MockRepository.detalleDeBoleto(b1.id)!!.estado)
        assertEquals(EstadoBoleto.VALIDO, MockRepository.detalleDeBoleto(b2.id)!!.estado)
        assertEquals(EstadoBoleto.VALIDO, MockRepository.detalleDeBoleto(b3.id)!!.estado)

        val r1bis = MockRepository.validarQr(b1.contenidoQr, validadorId = 2)
        assertFalse(r1bis.permitido)
        assertEquals("Boleto ya utilizado", r1bis.titulo)
        assertEquals(EstadoBoleto.VALIDO, MockRepository.detalleDeBoleto(b2.id)!!.estado)
        assertEquals(EstadoBoleto.VALIDO, MockRepository.detalleDeBoleto(b3.id)!!.estado)

        assertTrue(MockRepository.validarQr(b2.contenidoQr, validadorId = 2).permitido)
        assertEquals(3, MockRepository.boletosDeOrden(compra.orden.id).size)
    }

    @Test
    fun `CP-MULTI-11 el total se calcula con decimales exactos en MXN`() {
        val evento = eventoSinAsiento(aforo = 10, precio = 33.33)
        val tipo = tipoDe(evento)
        val compra = MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, 3)).getOrThrow()
        assertEquals(99.99, compra.total, 0.0)
        assertEquals(99.99, compra.pago!!.monto, 0.0)
    }

    @Test
    fun `CP-MULTI-12 cupoDisponible es el minimo entre cupo, stock y asientos libres`() {
        val evento = eventoConAsientos(filas = 1, porFila = 4)
        val tipo = tipoDe(evento)
        assertEquals(4, MockRepository.cupoDisponible(evento.id, tipo.id))
        val tres = MockRepository.asientosDe(evento.id).take(3).map { it.id }
        MockRepository.comprar(asistente, SolicitudCompra(evento.id, tipo.id, 3, tres)).getOrThrow()
        assertEquals(1, MockRepository.cupoDisponible(evento.id, tipo.id))
    }
}
