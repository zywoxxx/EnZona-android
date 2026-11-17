package com.uv.enzona.ui.theme

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clase de ancho de la pantalla, con los cortes de Material 3 (600 y 840 dp):
 * teléfono en vertical, teléfono grande o tableta chica, tableta o escritorio.
 */
enum class AnchoPantalla {
    COMPACTA, MEDIANA, EXPANDIDA;

    /** Hay sitio para una barra lateral y varias columnas de tarjetas. */
    val esAmplia: Boolean get() = this != COMPACTA
}

/** Clase de ancho a partir del ancho disponible en dp (sin Compose, para poder probarla). */
fun anchoPantalla(anchoDp: Int): AnchoPantalla = when {
    anchoDp < 600 -> AnchoPantalla.COMPACTA
    anchoDp < 840 -> AnchoPantalla.MEDIANA
    else -> AnchoPantalla.EXPANDIDA
}

@Composable
fun anchoPantalla(): AnchoPantalla = anchoPantalla(LocalConfiguration.current.screenWidthDp)

/** Anchos máximos de lectura: más allá de esto las líneas y los formularios se vuelven incómodos. */
object Legible {
    /** Formularios (acceso, registro, pago): campos de una sola columna. */
    val formulario = 560.dp

    /** Pantallas de lectura y detalle (evento, boleto, perfil, ajustes). */
    val lectura = 760.dp

    /** Ancho mínimo de una tarjeta de evento o boleto en una cuadrícula adaptable. */
    val tarjetaMinima = 320.dp
}

/**
 * Centra el contenido y limita su ancho en pantallas anchas; en un teléfono no
 * cambia nada. Va después del fondo y antes del scroll o del relleno interior,
 * así el fondo sigue cubriendo toda la pantalla.
 */
fun Modifier.contenidoLegible(maxAncho: Dp = Legible.lectura): Modifier =
    wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxAncho)

/** Columnas que caben en una cuadrícula adaptable; nunca menos de una (sin Compose, para probarlo). */
fun columnasAdaptables(anchoDisponibleDp: Int, anchoMinimoDp: Int): Int =
    if (anchoMinimoDp <= 0) 1 else maxOf(1, anchoDisponibleDp / anchoMinimoDp)
