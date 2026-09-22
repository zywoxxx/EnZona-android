package com.uv.enzona.util

/**
 * Validaciones de formato usadas en el registro (RF-01, casos CP-01 y CP-03).
 */
object Validaciones {

    /**
     * CURP: 4 letras + 6 dígitos (fecha) + H/M + 5 letras (entidad y consonantes)
     * + 1 homoclave alfanumérica + 1 dígito verificador. 18 caracteres.
     */
    private val CURP = Regex("^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$")

    private val CORREO = Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.-]{2,}$")

    fun curpValido(curp: String): Boolean = CURP.matches(curp.trim().uppercase())

    fun correoValido(correo: String): Boolean = CORREO.matches(correo.trim())

    /** Mínimo 8 caracteres, con al menos una letra y un número. */
    fun contrasenaValida(c: String): Boolean =
        c.length >= 8 && c.any { it.isLetter() } && c.any { it.isDigit() }

    fun telefonoValido(t: String): Boolean = t.isBlank() || t.filter { it.isDigit() }.length == 10
}
