package com.uv.enzona.data.model

import java.time.LocalDateTime

/**
 * Notificaciones del asistente (v4). Se guardan en `notificacion_asistente`
 * (ver docs/migracion_v4_incremental.sql) y se muestran en la campana de Inicio.
 *
 *  - COMPRA_CONFIRMADA: se crea al emitir los boletos de una orden.
 *  - RECORDATORIO: evento con boleto vigente que ocurre en los próximos días
 *    (lo genera `MockRepository.generarRecordatorios`; en producción, un job diario).
 *  - EVENTO_CANCELADO: la cancelación del evento (RF-14) avisa a cada asistente.
 *  - FECHA_CAMBIADA: el organizador movió la fecha de un evento con boletos (RF-13).
 */
enum class TipoNotificacion(val titulo: String) {
    COMPRA_CONFIRMADA("Compra confirmada"),
    RECORDATORIO("Evento próximo"),
    EVENTO_CANCELADO("Evento cancelado"),
    FECHA_CAMBIADA("Cambio de fecha"),
}

data class NotificacionAsistente(
    val id: Long,
    val usuarioId: Long,
    val eventoId: Long,
    val tipo: TipoNotificacion,
    val texto: String,
    val fecha: LocalDateTime,
    val leida: Boolean = false,
    /** Boleto relacionado (compra o recordatorio) para abrirlo directamente. */
    val boletoId: Long? = null,
) {
    val titulo: String get() = tipo.titulo
}
