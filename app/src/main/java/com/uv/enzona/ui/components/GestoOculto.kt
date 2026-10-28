package com.uv.enzona.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Toques seguidos que activan una opción oculta (como las opciones de desarrollador de Android). */
const val TOQUES_GESTO_OCULTO = 7

/** Si entre un toque y el siguiente pasa más que esto, la cuenta vuelve a empezar. */
private const val PAUSA_MAXIMA_MS = 1500L

/**
 * Cuenta toques rápidos sobre el elemento, sin efecto visual, y llama a
 * [onActivar] al llegar a [TOQUES_GESTO_OCULTO]. Quien usa la app no ve nada;
 * solo quien conoce el gesto llega a la opción.
 */
@Composable
fun Modifier.gestoOculto(onActivar: () -> Unit): Modifier {
    val contador = remember { ContadorDeToques() }
    return clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        if (contador.tocar(System.currentTimeMillis())) onActivar()
    }
}

/** Lógica del gesto separada de Compose para poder probarla. */
class ContadorDeToques(private val toquesNecesarios: Int = TOQUES_GESTO_OCULTO, private val pausaMaximaMs: Long = PAUSA_MAXIMA_MS) {
    private var toques = 0
    private var ultimo = 0L

    /** Registra un toque en el instante [ahoraMs]; devuelve `true` cuando se completa el gesto. */
    fun tocar(ahoraMs: Long): Boolean {
        toques = if (ahoraMs - ultimo > pausaMaximaMs) 1 else toques + 1
        ultimo = ahoraMs
        if (toques < toquesNecesarios) return false
        toques = 0
        return true
    }
}
