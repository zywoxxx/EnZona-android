package com.uv.enzona.ui.theme

import androidx.compose.ui.unit.dp

/** Escala de espaciado (múltiplos de 4 dp). */
object Espacio {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Margen lateral de las pantallas. */
    val margen = 16.dp
}

/** Tamaños de control compartidos. */
object Tamanos {
    /** Área táctil mínima de cualquier control. */
    val control = 48.dp
    val botonPrincipal = 52.dp
    val iconoFila = 22.dp
    val iconoDato = 20.dp

    /** Proporción de las portadas de evento (tarjeta y detalle). */
    const val PROPORCION_PORTADA = 16f / 9f
}
