package com.uv.enzona.ui.screens.auth

/**
 * Reglas del botón «Reenviar código» de [OtpScreen], sin Compose para poder
 * probarlas en la JVM. La API cuenta 60 s desde la última emisión del código
 * (el registro incluido) y descarta en silencio, con 202, los reenvíos
 * tempranos, así que la app lleva la cuenta regresiva por su lado.
 */
object ReenvioCodigo {

    /** Segundos que la app espera tras una emisión (registro o reenvío) antes de permitir otro envío. */
    const val ESPERA_SEGUNDOS = 60

    private const val MENSAJE_GENERICO = "No se pudo reenviar el código. Inténtalo de nuevo."

    /** Texto del botón: con cuenta regresiva mientras la API aún no acepta otro envío. */
    fun textoReenvio(segundosRestantes: Int): String =
        if (segundosRestantes > 0) "Reenviar código ($segundosRestantes s)" else "Reenviar código"

    /**
     * Texto para un fallo del reenvío: el detalle que mandó la API o, si no hay,
     * un texto genérico. Se separa del fallo de verificación para no decir que
     * «el código es incorrecto» cuando lo que falló fue pedir otro.
     */
    fun mensajeDeFallo(fallo: Throwable): String =
        fallo.message?.takeIf { it.isNotBlank() } ?: MENSAJE_GENERICO
}
