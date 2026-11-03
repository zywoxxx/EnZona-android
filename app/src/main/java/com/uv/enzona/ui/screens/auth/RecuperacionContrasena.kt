package com.uv.enzona.ui.screens.auth

import com.uv.enzona.util.Validaciones

/**
 * Reglas de «¿Olvidaste tu contraseña?» sin Compose, para probarlas en la JVM.
 * La API responde 202 siempre al pedir el código (no revela qué correos existen),
 * así que la app avanza al paso del código con cualquier correo bien formado.
 */
object RecuperacionContrasena {

    const val MENSAJE_CORREO = "Escribe un correo válido."
    const val MENSAJE_CONTRASENA = "La contraseña debe tener al menos 8 caracteres, con letras y números."
    const val MENSAJE_CONFIRMACION = "Las contraseñas no coinciden."

    /** Texto que se muestra tras pedir el código; no afirma que el correo exista. */
    const val AVISO_CODIGO_ENVIADO =
        "Si ese correo tiene una cuenta en EnZona, te enviamos un código de 6 dígitos. Revisa también la carpeta de no deseados."

    /** Motivo por el que no se puede pedir el código, o null si el correo sirve. */
    fun errorDeCorreo(correo: String): String? =
        if (Validaciones.correoValido(correo)) null else MENSAJE_CORREO

    /** Motivo por el que no se puede cambiar la contraseña todavía, o null si todo está en orden. */
    fun errorDeContrasena(nueva: String, confirmacion: String): String? = when {
        !Validaciones.contrasenaValida(nueva) -> MENSAJE_CONTRASENA
        nueva != confirmacion -> MENSAJE_CONFIRMACION
        else -> null
    }

    /** El código son exactamente 6 dígitos; se descarta cualquier otro carácter al escribir. */
    fun codigoCompleto(codigo: String): Boolean = codigo.length == 6 && codigo.all { it.isDigit() }

    /** Texto para el fallo al cambiar la contraseña: el detalle de la API o uno genérico. */
    fun mensajeDeFallo(fallo: Throwable): String =
        fallo.message?.takeIf { it.isNotBlank() } ?: "No se pudo cambiar la contraseña. Revisa el código e inténtalo de nuevo."
}
