package com.uv.enzona

import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoNotificacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Notificaciones del asistente (v4): compra confirmada, recordatorio de evento
 * próximo, evento cancelado y cambio de fecha. Casos CP-NOTIF-01..05.
 */
class NotificacionesTest {

    private val asistente = 4L                                   // Ana Estudiante (semilla)
    private val ahora = LocalDateTime.of(2026, 9, 22, 9, 0)

    private fun eventoDePago(nombre: String, fecha: LocalDateTime) = MockRepository.crearEvento(
        organizadorId = 1, nombre = nombre, descripcion = "", lugar = "Sala", direccion = "",
        categoria = "Música", fecha = fecha, esDePago = true, precio = 50.0, aforo = 20, publicar = true,
        latitud = 18.851, longitud = -97.1,
    )

    private fun eventoGratis(nombre: String, fecha: LocalDateTime) = MockRepository.crearEvento(
        organizadorId = 1, nombre = nombre, descripcion = "", lugar = "Patio", direccion = "",
        categoria = "Académico", fecha = fecha, esDePago = false, precio = 0.0, aforo = 20, publicar = true,
        latitud = 18.851, longitud = -97.1,
    )

    private fun comprar(eventoId: Long, cantidad: Int) {
        val tipo = MockRepository.tiposDe(eventoId).first()
        MockRepository.comprar(asistente, SolicitudCompra(eventoId, tipo.id, cantidad)).getOrThrow()
    }

    private fun de(eventoId: Long, tipo: TipoNotificacion) =
        MockRepository.notificaciones.filter { it.usuarioId == asistente && it.eventoId == eventoId && it.tipo == tipo }

    @Test
    fun cpNotif01_compra_generaAvisoConAccesoAlBoleto() {
        val e = eventoDePago("Compra avisada", ahora.plusDays(20))
        val antes = MockRepository.notificacionesNoLeidas(asistente)
        comprar(e.id, 2)

        val avisos = de(e.id, TipoNotificacion.COMPRA_CONFIRMADA)
        assertEquals(1, avisos.size)
        assertTrue(avisos[0].texto.contains("2 boletos"))
        assertTrue(avisos[0].texto.contains("Compra avisada"))
        assertEquals(MockRepository.boletosDe(asistente).first { it.evento.id == e.id }.id, avisos[0].boletoId)
        assertEquals(antes + 1, MockRepository.notificacionesNoLeidas(asistente))
    }

    @Test
    fun cpNotif02_recordatorio_soloParaEventosDentroDeSieteDias_yUnaSolaVez() {
        val cercano = eventoDePago("Cercano", ahora.plusDays(3))
        val lejano = eventoDePago("Lejano", ahora.plusDays(30))
        comprar(cercano.id, 1)
        comprar(lejano.id, 1)

        // (el repositorio es compartido entre pruebas: se comprueban solo los eventos de esta)
        MockRepository.generarRecordatorios(asistente, ahora)
        assertEquals(1, de(cercano.id, TipoNotificacion.RECORDATORIO).size)
        assertEquals(0, de(lejano.id, TipoNotificacion.RECORDATORIO).size)
        assertTrue(de(cercano.id, TipoNotificacion.RECORDATORIO)[0].texto.contains("es en 3 días"))

        // Volver a pedirlo no duplica
        assertEquals(0, MockRepository.generarRecordatorios(asistente, ahora))
        assertEquals(1, de(cercano.id, TipoNotificacion.RECORDATORIO).size)

        // Cuando el lejano entra en la ventana, aparece su recordatorio
        MockRepository.generarRecordatorios(asistente, ahora.plusDays(25))
        assertEquals(1, de(lejano.id, TipoNotificacion.RECORDATORIO).size)
        assertTrue(de(lejano.id, TipoNotificacion.RECORDATORIO)[0].texto.contains("es en 5 días"))
    }

    @Test
    fun cpNotif03_eventoCancelado_avisaConReembolso_ySeListaComoCancelado() {
        val e = eventoDePago("Se cancela", ahora.plusDays(10))
        comprar(e.id, 1)
        MockRepository.cancelarEvento(e.id, ejecutadoPor = 1, aceptaComision = true, ahora = ahora).getOrThrow()

        val avisos = de(e.id, TipoNotificacion.EVENTO_CANCELADO)
        assertEquals(1, avisos.size)
        assertTrue(avisos[0].texto.contains("reembolso"))
        // Un evento cancelado ya no genera recordatorio aunque esté cerca
        MockRepository.generarRecordatorios(asistente, ahora.plusDays(8))
        assertEquals(0, de(e.id, TipoNotificacion.RECORDATORIO).size)
    }

    @Test
    fun cpNotif04_cambioDeFecha_avisaSoloAQuienTieneBoleto() {
        val e = eventoGratis("Se mueve", ahora.plusDays(12))
        MockRepository.comprar(asistente, SolicitudCompra(e.id, null, 1)).getOrThrow()
        val otro = eventoGratis("No se mueve", ahora.plusDays(12))

        MockRepository.modificarEvento(
            e.id, e.nombre, e.descripcion, e.lugar, e.direccion, e.categoria, ahora.plusDays(14), e.aforo,
            latitud = e.latitud, longitud = e.longitud,
        ).getOrThrow()
        // Guardar sin cambiar la fecha no avisa
        MockRepository.modificarEvento(
            otro.id, otro.nombre, otro.descripcion, otro.lugar, otro.direccion, otro.categoria, otro.fecha, otro.aforo,
            latitud = otro.latitud, longitud = otro.longitud,
        ).getOrThrow()

        assertEquals(1, de(e.id, TipoNotificacion.FECHA_CAMBIADA).size)
        assertEquals(0, de(otro.id, TipoNotificacion.FECHA_CAMBIADA).size)
        assertTrue(de(e.id, TipoNotificacion.FECHA_CAMBIADA)[0].texto.contains("cambió de fecha"))
    }

    @Test
    fun cpNotif05_marcarLeidas_dejaElContadorEnCero_yElOrdenEsDeMasRecienteAMasAntigua() {
        val e = eventoDePago("Leer", ahora.plusDays(2))
        comprar(e.id, 1)
        MockRepository.generarRecordatorios(asistente, ahora.plusDays(1))
        assertTrue(MockRepository.notificacionesNoLeidas(asistente) >= 2)

        val lista = MockRepository.notificacionesDe(asistente, ahora.plusDays(1))
        assertTrue(lista.zipWithNext().all { (a, b) -> !a.fecha.isBefore(b.fecha) })

        MockRepository.marcarNotificacionLeida(lista.first().id)
        assertTrue(MockRepository.notificaciones.first { it.id == lista.first().id }.leida)

        MockRepository.marcarNotificacionesLeidas(asistente)
        assertEquals(0, MockRepository.notificacionesNoLeidas(asistente))
    }
}
