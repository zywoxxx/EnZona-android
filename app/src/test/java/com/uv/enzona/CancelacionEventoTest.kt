package com.uv.enzona

import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.model.EstadoAsiento
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.EstadoPago
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.Orden
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.data.model.TipoCancelacion
import com.uv.enzona.data.model.politicaCancelacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * CP-CANCEL-*: regla de negocio de cancelación (RF-14, v3) contra la función
 * pura `politicaCancelacion` y contra el repositorio en memoria. El reembolso
 * real con la pasarela y la concurrencia requieren backend y NO se prueban aquí.
 */
class CancelacionEventoTest {

    private val ahora: LocalDateTime = LocalDateTime.of(2026, 9, 17, 12, 0)
    private val organizador = 1L
    private val admin = 3L
    private val asistente = 4L
    private val otroAsistente = 2L   // validador@uv.mx también puede asistir en la demo
    private var n = 0

    private fun evento(esDePago: Boolean, precio: Double, fecha: LocalDateTime = ahora.plusDays(10), publicar: Boolean = true, aforo: Int = 20): Evento =
        MockRepository.crearEvento(
            organizadorId = organizador, nombre = "CP-CANCEL ${n++}", descripcion = "", lugar = "Sede", direccion = "",
            categoria = "Música", fecha = fecha, esDePago = esDePago, precio = precio, aforo = aforo, publicar = publicar,
            latitud = 18.851, longitud = -97.1,
        )

    private fun comprar(usuario: Long, evento: Evento, cantidad: Int) =
        MockRepository.comprar(usuario, SolicitudCompra(evento.id, MockRepository.tiposDe(evento.id).firstOrNull()?.id, cantidad)).getOrThrow()

    // ------------------------------------------------------------ función pura

    private fun ev(esDePago: Boolean, estado: EstadoEvento = EstadoEvento.PUBLICADO, fecha: LocalDateTime = ahora.plusDays(1)) = Evento(
        id = 99, organizadorId = 1, nombre = "x", descripcion = "", lugar = "", direccion = "", categoria = "", fecha = fecha,
        esDePago = esDePago, precioDesde = if (esDePago) 500.0 else 0.0, aforo = 10, disponibles = 7, estado = estado,
    )
    private fun tipos(precio: Double) = listOf(TipoBoleto(1, 99, "General", precio, 10, 7))
    private fun ordenes(vararg totales: Double) = totales.mapIndexed { i, t -> Orden(i.toLong(), 4, 99, t, EstadoOrden.PAGADA, "") }

    @Test
    fun `CP-CANCEL-01 politica - gratuito con 3 confirmaciones se cancela sin comision`() {
        val p = politicaCancelacion(ev(false), emptyList(), ordenes(0.0, 0.0, 0.0), boletosVigentes = 3, ahora = ahora)
        assertTrue(p.permitida)
        assertEquals(TipoCancelacion.GRATUITO, p.tipo)
        assertEquals(BigDecimal("0.00"), p.comision)
        assertEquals(3, p.boletosAfectados)
        assertFalse(p.cobraComision)
    }

    @Test
    fun `CP-CANCEL-02 politica - de pago con 1500 cobrados reembolsa 1500 y cobra 150 de comision`() {
        val p = politicaCancelacion(ev(true), tipos(500.0), ordenes(500.0, 1000.0), boletosVigentes = 3, ahora = ahora)
        assertTrue(p.permitida)
        assertEquals(TipoCancelacion.DE_PAGO_CON_COMISION, p.tipo)
        assertEquals(BigDecimal("1500.00"), p.importeCobrado)
        assertEquals(BigDecimal("1500.00"), p.reembolso)
        assertEquals(BigDecimal("150.00"), p.comision)
        assertTrue(p.cobraComision)
    }

    @Test
    fun `la comision se redondea HALF_UP a 2 decimales`() {
        // 10 % de 333.35 = 33.335 → 33.34 ; 10 % de 99.99 = 9.999 → 10.00
        assertEquals(BigDecimal("33.34"), politicaCancelacion(ev(true), tipos(1.0), ordenes(333.35), 1, ahora).comision)
        assertEquals(BigDecimal("10.00"), politicaCancelacion(ev(true), tipos(1.0), ordenes(99.99), 1, ahora).comision)
        assertEquals(BigDecimal("0.01"), politicaCancelacion(ev(true), tipos(1.0), ordenes(0.05), 1, ahora).comision)
    }

    @Test
    fun `CP-CANCEL-04 politica - de pago sin ventas procede con comision 0 y lo indica`() {
        val p = politicaCancelacion(ev(true), tipos(500.0), emptyList(), boletosVigentes = 0, ahora = ahora)
        assertTrue(p.permitida)
        assertEquals(TipoCancelacion.DE_PAGO_SIN_VENTAS, p.tipo)
        assertEquals(BigDecimal("0.00"), p.comision)
    }

    @Test
    fun `CP-CANCEL-05 politica - un evento iniciado o realizado no se cancela`() {
        val iniciado = politicaCancelacion(ev(true, fecha = ahora.minusMinutes(1)), tipos(500.0), ordenes(500.0), 1, ahora)
        assertFalse(iniciado.permitida)
        assertNotNull(iniciado.motivoBloqueo)
        assertTrue(iniciado.motivoBloqueo!!.contains("ya comenzó"))
        val realizado = politicaCancelacion(ev(false, estado = EstadoEvento.REALIZADO), emptyList(), emptyList(), 0, ahora)
        assertFalse(realizado.permitida)
        val yaCancelado = politicaCancelacion(ev(false, estado = EstadoEvento.CANCELADO), emptyList(), emptyList(), 0, ahora)
        assertFalse(yaCancelado.permitida)
        // Exactamente a la hora de inicio también cuenta como iniciado
        assertFalse(politicaCancelacion(ev(false, fecha = ahora), emptyList(), emptyList(), 0, ahora).permitida)
    }

    @Test
    fun `politica - borrador se cancela sin comision y administrativa nunca cobra comision`() {
        val borrador = politicaCancelacion(ev(true, estado = EstadoEvento.BORRADOR), tipos(500.0), emptyList(), 0, ahora)
        assertEquals(TipoCancelacion.BORRADOR, borrador.tipo)
        assertEquals(BigDecimal("0.00"), borrador.comision)
        val admin = politicaCancelacion(ev(true), tipos(500.0), ordenes(1500.0), 3, ahora, administrativa = true)
        assertEquals(TipoCancelacion.ADMINISTRATIVA, admin.tipo)
        assertEquals(BigDecimal("0.00"), admin.comision)
        assertEquals(BigDecimal("1500.00"), admin.reembolso)
        assertTrue(admin.requiereMotivo)
    }

    // ------------------------------------------------------------ repositorio (operación completa)

    @Test
    fun `CP-CANCEL-01 repositorio - cancelar gratuito anula 3 confirmaciones y avisa a 3 asistentes`() {
        val e = evento(esDePago = false, precio = 0.0)
        comprar(asistente, e, 1); comprar(otroAsistente, e, 1); comprar(admin, e, 1)
        val r = MockRepository.cancelarEvento(e.id, ejecutadoPor = organizador, ahora = ahora).getOrThrow()
        assertEquals(3, r.boletosAnulados)
        assertEquals(BigDecimal("0.00"), r.comision)
        assertEquals(3, r.asistentesNotificados)
        assertEquals(EstadoEvento.CANCELADO, MockRepository.obtenerEvento(e.id)!!.estado)
        assertTrue(MockRepository.boletosDeEvento(e.id).all { it.estado == EstadoBoleto.CANCELADO })
        assertEquals(3, MockRepository.notificaciones.count { it.eventoId == e.id && it.tipo == com.uv.enzona.data.model.TipoNotificacion.EVENTO_CANCELADO })
        assertEquals(0.0, MockRepository.obtenerEvento(e.id)!!.comisionCancelacion, 0.0)
    }

    @Test
    fun `CP-CANCEL-02 repositorio - cancelar de pago con 1500 cobrados reembolsa y registra comision 150`() {
        val e = evento(esDePago = true, precio = 500.0)
        comprar(asistente, e, 2)          // $1,000
        comprar(otroAsistente, e, 1)      // $500
        val politica = MockRepository.politicaCancelacionDe(e.id, ahora = ahora)!!
        assertEquals(BigDecimal("1500.00"), politica.reembolso)
        assertEquals(BigDecimal("150.00"), politica.comision)

        val r = MockRepository.cancelarEvento(e.id, ejecutadoPor = organizador, aceptaComision = true, ahora = ahora).getOrThrow()

        assertEquals(3, r.boletosAnulados)
        assertEquals(BigDecimal("1500.00"), r.importeReembolsado)
        assertEquals(BigDecimal("150.00"), r.comision)
        val ev = MockRepository.obtenerEvento(e.id)!!
        assertEquals(EstadoEvento.CANCELADO, ev.estado)
        assertEquals(150.0, ev.comisionCancelacion, 0.0)
        assertEquals(organizador, ev.canceladoPor)
        assertEquals(ahora, ev.fechaCancelacion)
        assertTrue(MockRepository.ordenes.filter { it.eventoId == e.id && it.total > 0 }.all { it.estado == EstadoOrden.REEMBOLSADA })
        val ordenIds = MockRepository.ordenes.filter { it.eventoId == e.id }.map { it.id }.toSet()
        assertTrue(MockRepository.pagos.filter { it.ordenId in ordenIds }.all { it.estado == EstadoPago.REEMBOLSADO })
        val cargo = MockRepository.cargosOrganizador.single { it.eventoId == e.id }
        assertEquals(150.0, cargo.importe, 0.0)
        assertEquals(organizador, cargo.organizadorId)
        assertFalse(cargo.pagado)
        // Ningún boleto vigente queda en un evento cancelado
        assertTrue(MockRepository.boletosDeEvento(e.id).none { it.estado == EstadoBoleto.VALIDO })
    }

    @Test
    fun `CP-CANCEL-03 rechazar la comision no cambia nada`() {
        val e = evento(esDePago = true, precio = 500.0)
        comprar(asistente, e, 1)
        val r = MockRepository.cancelarEvento(e.id, ejecutadoPor = organizador, aceptaComision = false, ahora = ahora)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("aceptar la comisión"))
        assertEquals(EstadoEvento.PUBLICADO, MockRepository.obtenerEvento(e.id)!!.estado)
        assertTrue(MockRepository.boletosDeEvento(e.id).all { it.estado == EstadoBoleto.VALIDO })
        assertTrue(MockRepository.ordenes.filter { it.eventoId == e.id }.all { it.estado == EstadoOrden.PAGADA })
        assertTrue(MockRepository.cargosOrganizador.none { it.eventoId == e.id })
    }

    @Test
    fun `CP-CANCEL-04 repositorio - evento de pago sin ventas se cancela con comision 0`() {
        val e = evento(esDePago = true, precio = 500.0)
        val r = MockRepository.cancelarEvento(e.id, ejecutadoPor = organizador, ahora = ahora).getOrThrow()
        assertEquals(0, r.boletosAnulados)
        assertEquals(BigDecimal("0.00"), r.comision)
        assertTrue(MockRepository.cargosOrganizador.none { it.eventoId == e.id })
    }

    @Test
    fun `CP-CANCEL-05 repositorio - un evento ya iniciado rechaza la cancelacion`() {
        val e = evento(esDePago = false, precio = 0.0, fecha = ahora.minusHours(1))
        val r = MockRepository.cancelarEvento(e.id, ejecutadoPor = organizador, ahora = ahora)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("ya comenzó"))
        assertEquals(EstadoEvento.PUBLICADO, MockRepository.obtenerEvento(e.id)!!.estado)
        MockRepository.marcarRealizado(e.id)
        assertEquals(EstadoEvento.REALIZADO, MockRepository.obtenerEvento(e.id)!!.estado)
    }

    @Test
    fun `CP-CANCEL-06 el QR de un evento cancelado se rechaza con Evento cancelado`() {
        val e = evento(esDePago = true, precio = 100.0)
        val boleto = comprar(asistente, e, 1).boletos.first()
        MockRepository.cancelarEvento(e.id, ejecutadoPor = organizador, aceptaComision = true, ahora = ahora).getOrThrow()
        val r = MockRepository.validarQr(boleto.contenidoQr, validadorId = 2)
        assertFalse(r.permitido)
        assertEquals("Evento cancelado", r.titulo)
        // Y en la cuenta del asistente el boleto ya no es válido
        assertEquals(EstadoBoleto.CANCELADO, MockRepository.detalleDeBoleto(boleto.id)!!.estado)
    }

    @Test
    fun `CP-CANCEL-07 la cancelacion administrativa reembolsa 100 por ciento, exige motivo y no cobra comision`() {
        val e = evento(esDePago = true, precio = 500.0)
        comprar(asistente, e, 2)
        val sinMotivo = MockRepository.cancelarEvento(e.id, ejecutadoPor = admin, administrativa = true, ahora = ahora)
        assertTrue(sinMotivo.isFailure)
        assertEquals(EstadoEvento.PUBLICADO, MockRepository.obtenerEvento(e.id)!!.estado)

        val r = MockRepository.cancelarEvento(e.id, ejecutadoPor = admin, motivo = "Contenido reportado", administrativa = true, ahora = ahora).getOrThrow()
        assertEquals(BigDecimal("1000.00"), r.importeReembolsado)
        assertEquals(BigDecimal("0.00"), r.comision)
        val ev = MockRepository.obtenerEvento(e.id)!!
        assertEquals(admin, ev.canceladoPor)
        assertEquals("Contenido reportado", ev.motivoCancelacion)
        assertEquals(0.0, ev.comisionCancelacion, 0.0)
        assertTrue(MockRepository.cargosOrganizador.none { it.eventoId == e.id })
    }

    @Test
    fun `solo el organizador del evento puede cancelarlo (no administrativa)`() {
        val e = evento(esDePago = false, precio = 0.0)
        val r = MockRepository.cancelarEvento(e.id, ejecutadoPor = asistente, ahora = ahora)
        assertTrue(r.isFailure)
        assertEquals(EstadoEvento.PUBLICADO, MockRepository.obtenerEvento(e.id)!!.estado)
    }

    @Test
    fun `al cancelar un evento con asientos se liberan las butacas`() {
        val e = MockRepository.crearEvento(
            organizadorId = organizador, nombre = "CP-CANCEL asientos", descripcion = "", lugar = "Teatro", direccion = "",
            categoria = "Teatro", fecha = ahora.plusDays(3), esDePago = true, precio = 60.0, aforo = 6, publicar = true,
            requiereAsiento = true, filas = 1, asientosPorFila = 6, seccion = "Luneta", latitud = 18.851, longitud = -97.1,
        )
        val libres = MockRepository.asientosDe(e.id).take(2).map { it.id }
        MockRepository.comprar(asistente, SolicitudCompra(e.id, MockRepository.tiposDe(e.id).first().id, 2, libres)).getOrThrow()
        libres.forEach { assertEquals(EstadoAsiento.OCUPADO, MockRepository.obtenerAsiento(it)!!.estado) }
        MockRepository.cancelarEvento(e.id, ejecutadoPor = organizador, aceptaComision = true, ahora = ahora).getOrThrow()
        libres.forEach { assertEquals(EstadoAsiento.DISPONIBLE, MockRepository.obtenerAsiento(it)!!.estado) }
    }

    @Test
    fun `publicar exige coordenadas y un borrador puede no tenerlas (CP-UBIC-02)`() {
        val borrador = MockRepository.crearEvento(
            organizadorId = organizador, nombre = "Borrador sin mapa", descripcion = "", lugar = "Sede", direccion = "",
            categoria = "Música", fecha = ahora.plusDays(5), esDePago = false, precio = 0.0, aforo = 10, publicar = false,
        )
        assertNull(borrador.latitud)
        assertTrue(MockRepository.publicarEvento(borrador.id).isFailure)
        assertEquals(EstadoEvento.BORRADOR, MockRepository.obtenerEvento(borrador.id)!!.estado)
        val error = runCatching {
            MockRepository.crearEvento(
                organizadorId = organizador, nombre = "Publicado sin mapa", descripcion = "", lugar = "Sede", direccion = "",
                categoria = "Música", fecha = ahora.plusDays(5), esDePago = false, precio = 0.0, aforo = 10, publicar = true,
            )
        }
        assertTrue(error.isFailure)
        // Coordenadas guardadas con 5 decimales y dentro de rango (CP-UBIC-01)
        val conMapa = MockRepository.crearEvento(
            organizadorId = organizador, nombre = "Con mapa", descripcion = "", lugar = "Sede", direccion = "",
            categoria = "Música", fecha = ahora.plusDays(5), esDePago = false, precio = 0.0, aforo = 10, publicar = true,
            latitud = com.uv.enzona.ui.components.redondear5(18.851184321), longitud = com.uv.enzona.ui.components.redondear5(-97.101029876),
        )
        assertEquals(18.85118, conMapa.latitud!!, 0.0)
        assertEquals(-97.10103, conMapa.longitud!!, 0.0)
        assertTrue(conMapa.latitud!! in -90.0..90.0 && conMapa.longitud!! in -180.0..180.0)
    }
}
