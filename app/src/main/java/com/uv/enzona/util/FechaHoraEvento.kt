package com.uv.enzona.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Validación pura de la fecha y la hora del formulario de evento (v3).
 *
 * La hora se captura con un selector numérico de 24 h o con una máscara
 * HH:mm de solo dígitos; nunca con texto libre. Estas funciones no dependen
 * de Compose para poder probarse en JVM (CP-FECHA-*, CP-HORA-*).
 */
object FechaHoraEvento {

    /** Deja solo dígitos y limita a 4 (HHmm). */
    fun soloDigitos(entrada: String): String = entrada.filter { it.isDigit() }.take(4)

    /** "1930" → "19:30"; "19" → "19"; "" → "". */
    fun mascara(digitos: String): String {
        val d = soloDigitos(digitos)
        return if (d.length <= 2) d else d.substring(0, 2) + ":" + d.substring(2)
    }

    /** Hora válida en 24 h (00–23, 00–59) a partir de "HH:mm" o "HHmm"; null si no lo es. */
    fun parsearHora(texto: String): LocalTime? {
        val d = soloDigitos(texto)
        if (d.length != 4) return null
        val h = d.substring(0, 2).toInt()
        val m = d.substring(2, 4).toInt()
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    fun combinar(fecha: LocalDate, hora: LocalTime): LocalDateTime = LocalDateTime.of(fecha, hora)

    /** Una fecha del calendario es elegible si no es anterior a hoy. */
    fun fechaSeleccionable(fecha: LocalDate, hoy: LocalDate): Boolean = !fecha.isBefore(hoy)

    /**
     * Valida inicio y fin. Devuelve el mensaje de error o null si todo está bien.
     * - El inicio no puede ser anterior a hoy.
     * - El fin, si existe, no puede ser anterior al inicio; si es el mismo día,
     *   la hora de fin debe ser posterior a la de inicio.
     */
    fun validar(inicio: LocalDateTime?, fin: LocalDateTime?, hoy: LocalDate): String? {
        if (inicio == null) return "Elige la fecha y la hora de inicio."
        if (inicio.toLocalDate().isBefore(hoy)) return "La fecha de inicio no puede ser anterior a hoy."
        if (fin != null) {
            if (fin.toLocalDate().isBefore(inicio.toLocalDate())) return "La fecha de fin no puede ser anterior a la de inicio."
            if (!fin.isAfter(inicio)) return "La hora de fin debe ser posterior a la de inicio."
        }
        return null
    }
}
