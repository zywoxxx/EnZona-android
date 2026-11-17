package com.uv.enzona.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay

/**
 * Animaciones compartidas: cortas (180 a 320 ms) y con la misma curva, para que
 * la app se sienta viva sin estorbar. Ninguna cambia el resultado final de la
 * pantalla: si se quitan, todo queda en el mismo sitio.
 */
object Movimiento {
    const val CORTO_MS = 180
    const val MEDIO_MS = 260
    const val LARGO_MS = 320

    /** Tope de elementos que escalonan su entrada; del noveno en adelante aparecen juntos. */
    const val MAX_ESCALONADOS = 8

    /** Espera entre un elemento de lista y el siguiente al aparecer. */
    const val PASO_ESCALONADO_MS = 45

    /** Retraso de entrada del elemento en la posición dada (puro, para probarlo). */
    fun retrasoDeEntradaMs(indice: Int): Int = indice.coerceIn(0, MAX_ESCALONADOS) * PASO_ESCALONADO_MS

    /** Fracción del ancho que recorre una pantalla al entrar o salir. */
    private const val FRACCION_DESLIZAMIENTO = 10

    /** La pantalla nueva entra desde la derecha con un fundido. */
    val entrar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(MEDIO_MS)) + slideInHorizontally(tween(LARGO_MS, easing = FastOutSlowInEasing)) { it / FRACCION_DESLIZAMIENTO }
    }

    /** La pantalla que se tapa se desvanece sin moverse. */
    val salir: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(CORTO_MS))
    }

    /** Al volver atrás, la pantalla anterior reaparece desde la izquierda. */
    val volverAEntrar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(MEDIO_MS)) + slideInHorizontally(tween(LARGO_MS, easing = FastOutSlowInEasing)) { -it / FRACCION_DESLIZAMIENTO }
    }

    /** Al volver atrás, la pantalla actual sale hacia la derecha. */
    val volverASalir: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(CORTO_MS)) + slideOutHorizontally(tween(LARGO_MS, easing = FastOutSlowInEasing)) { it / FRACCION_DESLIZAMIENTO }
    }
}

/**
 * Entrada suave de un elemento: aparece con un fundido y subiendo unos puntos.
 * En listas, `indice` escalona las primeras tarjetas para que no caigan todas a la vez.
 */
@Composable
fun Modifier.aparicion(indice: Int = 0): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(Movimiento.retrasoDeEntradaMs(indice).toLong())
        visible = true
    }
    val opacidad by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(Movimiento.MEDIO_MS),
        label = "opacidad",
    )
    val desplazamiento by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(Movimiento.LARGO_MS, easing = FastOutSlowInEasing),
        label = "desplazamiento",
    )
    return graphicsLayer {
        alpha = opacidad
        translationY = desplazamiento * 20.dp.toPx()
    }
}

/** Encoge ligeramente el control mientras está presionado, como respuesta táctil. */
@Composable
fun Modifier.escalaAlPresionar(interaccion: InteractionSource, escala: Float = 0.97f): Modifier {
    val presionado by interaccion.collectIsPressedAsState()
    val factor by animateFloatAsState(
        targetValue = if (presionado) escala else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "escala",
    )
    return graphicsLayer {
        scaleX = factor
        scaleY = factor
    }
}
