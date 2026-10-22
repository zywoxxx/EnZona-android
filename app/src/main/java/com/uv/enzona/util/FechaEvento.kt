package com.uv.enzona.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Fecha y hora del evento (v3).
 *
 * `Evento.fecha` es un `LocalDateTime` en la zona del área de estudio
 * (America/Mexico_City), alineado con `fecha_hora_inicio TIMESTAMPTZ` del
 * esquema. Esta es la ÚNICA función de formato: las pantallas no formatean por
 * su cuenta. java.time está disponible desde API 26 sin desugaring.
 */
object FechaEvento {

    val ZONA: ZoneId = ZoneId.of("America/Mexico_City")
    val LOCALE: Locale = Locale("es", "MX")

    // Abreviaturas fijas: las de la plataforma cambian con su versión ("sep" en unas, "sept" en otras)
    // y el texto debe ser el mismo en todos los teléfonos y en el servidor.
    private val DIAS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val MESES = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    private fun diaYMes(f: LocalDate) = "${DIAS[f.dayOfWeek.value - 1]} ${f.dayOfMonth} ${MESES[f.monthValue - 1]}"

    private val fmtHora = DateTimeFormatter.ofPattern("HH:mm", LOCALE)
    private val fmtIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun ahora(): LocalDateTime = LocalDateTime.now(ZONA)

    /** "Vie 25 Sep · 19:00" (formato de tarjetas, detalle y boleto). */
    fun corto(fecha: LocalDateTime): String = "${diaYMes(fecha.toLocalDate())} · ${hora(fecha)}"

    /** "Vie 25 Sep 2026 · 19:00" (resumen del formulario). */
    fun largo(fecha: LocalDateTime): String = "${diaYMes(fecha.toLocalDate())} ${fecha.year} · ${hora(fecha)}"

    /** "Vie 25 Sep 2026" (campo de fecha del formulario). */
    fun soloFecha(fecha: LocalDate): String = "${diaYMes(fecha)} ${fecha.year}"

    /** "19:00". */
    fun hora(fecha: LocalDateTime): String = fmtHora.format(fecha)
    fun hora(hora: LocalTime): String = fmtHora.format(hora)

    /** Día del mes ("25") para el bloque tipográfico de la portada. */
    fun dia(fecha: LocalDateTime): String = fecha.dayOfMonth.toString()

    /** Mes abreviado en minúsculas ("sep"). */
    fun mesCorto(fecha: LocalDateTime): String = MESES[fecha.monthValue - 1].lowercase()

    /** Intervalo "Vie 25 Sep · 19:00 – 21:30" o con fecha de fin distinta. */
    fun intervalo(inicio: LocalDateTime, fin: LocalDateTime?): String = when {
        fin == null -> corto(inicio)
        fin.toLocalDate() == inicio.toLocalDate() -> "${corto(inicio)} – ${hora(fin)}"
        else -> "${corto(inicio)} – ${corto(fin)}"
    }

    /** ISO-8601 con desplazamiento, p. ej. 2026-09-25T19:00:00-06:00 (TIMESTAMPTZ). */
    fun iso(fecha: LocalDateTime): String = fmtIso.format(fecha.atZone(ZONA).toOffsetDateTime())

    /** Lee un ISO-8601 con zona (lo que devuelve la API) y lo lleva a la zona del evento. */
    fun desdeIso(texto: String): LocalDateTime? = try {
        OffsetDateTime.parse(texto, fmtIso).atZoneSameInstant(ZONA).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        null
    }
}
