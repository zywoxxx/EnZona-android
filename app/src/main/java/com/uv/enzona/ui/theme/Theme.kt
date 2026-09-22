package com.uv.enzona.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tema EnZona v2.
 *
 * Tema claro principal según la paleta de la plataforma (fondo gris muy claro,
 * superficies blancas, azules de marca y coral para la acción destacada). Sin
 * color dinámico del sistema: la identidad debe verse igual en todos los
 * teléfonos. No hay modo oscuro en esta versión (ver notas técnicas).
 *
 * Roles Material 3 → paleta:
 *   primary   = Azul marino (botones generales, navegación, escenario)
 *   secondary = Azul profundo (enlaces, texto azul pequeño, bordes de selección)
 *   tertiary  = Coral (acción destacada: comprar/confirmar/publicar) con texto tinta
 *   error     = Error / Rechazado con blanco
 *   Éxito, advertencia e información viven en `EnZonaTema.semanticos`.
 */

// ==================================================================
//  Color
// ==================================================================

private val EsquemaClaro: ColorScheme = lightColorScheme(
    primary = AzulMarino,
    onPrimary = Color.White,
    primaryContainer = AzulTinte100,
    onPrimaryContainer = AzulMarino,

    secondary = AzulProfundo,
    onSecondary = Color.White,
    secondaryContainer = AzulTinte50,
    onSecondaryContainer = AzulMarino,

    tertiary = Coral,
    onTertiary = Tinta,
    tertiaryContainer = CoralSuave,
    onTertiaryContainer = Tinta,

    error = ErrorRojo,
    onError = Color.White,
    errorContainer = ErrorContenedor,
    onErrorContainer = SobreErrorContenedor,

    background = Fondo,
    onBackground = Tinta,
    surface = Superficie,
    onSurface = Tinta,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = GrisTexto,
    surfaceTint = AzulEnZona,

    surfaceContainerLowest = Superficie,
    surfaceContainerLow = SuperficieBaja,
    surfaceContainer = Superficie,
    surfaceContainerHigh = AzulTinte50,
    surfaceContainerHighest = AzulTinte100,
    surfaceBright = Superficie,
    surfaceDim = LineaBorde,

    outline = GrisSuave,
    outlineVariant = LineaBorde,
    inverseSurface = Tinta,
    inverseOnSurface = Superficie,
    inversePrimary = AzulTinte200,
    scrim = Color.Black,
)

/**
 * Colores semánticos que Material 3 no define (éxito, advertencia,
 * información) y el acento azul EnZona para iconos y elementos seleccionados.
 * Se leen con `EnZonaTema.semanticos` para no mezclarlos con los de marca.
 */
@Immutable
data class ColoresSemanticos(
    val exito: Color,
    val exitoContenedor: Color,
    val sobreExitoContenedor: Color,
    val aviso: Color,
    val avisoContenedor: Color,
    val sobreAvisoContenedor: Color,
    val informacion: Color,
    val informacionContenedor: Color,
    val sobreInformacionContenedor: Color,
    /** Azul EnZona: iconos y superficies de acento (4.1:1 sobre blanco: no para texto pequeño). */
    val acento: Color,
)

private val SemanticosClaro = ColoresSemanticos(
    exito = Exito,
    exitoContenedor = ExitoContenedor,
    sobreExitoContenedor = SobreExitoContenedor,
    aviso = Advertencia,
    avisoContenedor = AdvertenciaContenedor,
    sobreAvisoContenedor = SobreAdvertenciaContenedor,
    informacion = Informacion,
    informacionContenedor = InformacionContenedor,
    sobreInformacionContenedor = SobreInformacionContenedor,
    acento = AzulEnZona,
)

private val LocalColoresSemanticos = staticCompositionLocalOf { SemanticosClaro }

/** Acceso a los tokens propios de EnZona que no están en MaterialTheme. */
object EnZonaTema {
    val semanticos: ColoresSemanticos
        @Composable @ReadOnlyComposable get() = LocalColoresSemanticos.current
}

// ==================================================================
//  Tipografía
// ==================================================================

/*
 * Roboto (fuente del sistema). Sin fuentes descargables para no depender de
 * Google Play Services ni de red. La personalidad viene de la escala y los
 * pesos. Ningún estilo baja de 11.5 sp.
 */
private val Familia = FontFamily.Default

private val TipografiaEnZona = Typography(
    displayLarge = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),

    headlineLarge = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Familia, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),

    titleLarge = TextStyle(fontFamily = Familia, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Familia, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),

    bodyLarge = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),

    labelLarge = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Familia, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)

// ==================================================================
//  Formas
// ==================================================================

private val FormasEnZona = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),     // campos de texto, chips cuadrados
    large = RoundedCornerShape(20.dp),      // tarjetas
    extraLarge = RoundedCornerShape(28.dp), // hojas y diálogos
)

// ==================================================================
//  Tema
// ==================================================================

@Composable
fun EnZonaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColoresSemanticos provides SemanticosClaro) {
        MaterialTheme(
            colorScheme = EsquemaClaro,
            typography = TipografiaEnZona,
            shapes = FormasEnZona,
            content = content
        )
    }
}
