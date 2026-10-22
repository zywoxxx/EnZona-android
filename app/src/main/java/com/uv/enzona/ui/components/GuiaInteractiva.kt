@file:OptIn(ExperimentalFoundationApi::class)

package com.uv.enzona.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.Guias
import com.uv.enzona.ui.theme.Espacio
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Guías interactivas de la primera vez («tooltip walkthroughs»).
 *
 * Cómo se usa:
 *  1. [AnfitrionDeGuias] envuelve toda la app (MainActivity) y dibuja la guía
 *     activa encima de cualquier pantalla, incluida la barra inferior.
 *  2. Cada pantalla marca sus zonas difíciles con `Modifier.objetivoDeGuia("clave")`
 *     y declara su recorrido con [GuiaDePantalla]. La guía se abre sola la primera
 *     vez que se entra a la pantalla y se recuerda en [Guias] (preferencias locales).
 *  3. Cada paso oscurece la pantalla, recorta un foco alrededor del objetivo, lo
 *     trae a la vista si está fuera de pantalla y muestra una tarjeta con el texto.
 *
 * Sin anfitrión (vistas previas, pruebas de componentes) nada de esto hace nada.
 */
class PasoGuia(
    /** Clave del objetivo marcado con `objetivoDeGuia`; null para un paso sin foco (bienvenida). */
    val objetivo: String? = null,
    val titulo: String,
    val texto: String,
)

/** Estado compartido entre el anfitrión y las pantallas: objetivos visibles y guía activa. */
class EstadoGuias {

    internal class Objetivo(val limites: Rect, val traerAVista: BringIntoViewRequester?)

    internal class GuiaActiva(val clave: String, val pasos: List<PasoGuia>)

    internal val objetivos = mutableStateMapOf<String, Objetivo>()

    internal var activa by mutableStateOf<GuiaActiva?>(null)
        private set

    internal fun registrar(clave: String, limites: Rect, traerAVista: BringIntoViewRequester?) {
        objetivos[clave] = Objetivo(limites, traerAVista)
    }

    internal fun quitar(clave: String) {
        objetivos.remove(clave)
    }

    /** Abre la guía si no se ha visto y no hay otra en pantalla. Devuelve si se abrió. */
    fun pedir(clave: String, pasos: List<PasoGuia>): Boolean {
        if (pasos.isEmpty() || activa != null || Guias.vista(clave)) return false
        activa = GuiaActiva(clave, pasos)
        return true
    }

    /** La persona terminó u omitió la guía: no se vuelve a mostrar. */
    fun terminar(clave: String) {
        if (activa?.clave != clave) return
        Guias.marcarVista(clave)
        activa = null
    }

    /** La pantalla se cerró a mitad de la guía: se oculta sin marcarla como vista. */
    fun cancelar(clave: String) {
        if (activa?.clave == clave) activa = null
    }

    val claveActiva: String? get() = activa?.clave
}

val LocalGuias = compositionLocalOf<EstadoGuias?> { null }

/** Marca un elemento como objetivo de una guía. La clave debe coincidir con la de un [PasoGuia]. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.objetivoDeGuia(clave: String): Modifier = composed {
    val estado = LocalGuias.current ?: return@composed this
    val peticion = remember { BringIntoViewRequester() }
    DisposableEffect(clave, estado) { onDispose { estado.quitar(clave) } }
    this
        .bringIntoViewRequester(peticion)
        .onGloballyPositioned { estado.registrar(clave, it.boundsInRoot(), peticion) }
}

/**
 * Declara la guía de la pantalla en la que se llama. Se abre sola la primera vez;
 * `activa = false` la pospone (por ejemplo, hasta que terminen de cargar los datos).
 */
@Composable
fun GuiaDePantalla(clave: String, pasos: List<PasoGuia>, activa: Boolean = true) {
    val estado = LocalGuias.current ?: return
    LaunchedEffect(clave, activa, Guias.cambios) {
        if (!activa) return@LaunchedEffect
        delay(ESPERA_ANTES_DE_ABRIR_MS)   // deja que la pantalla termine de dibujarse y medir sus objetivos
        estado.pedir(clave, pasos)
    }
    DisposableEffect(clave, estado) { onDispose { estado.cancelar(clave) } }
}

/** Envuelve la app y dibuja la guía activa por encima de todo. */
@Composable
fun AnfitrionDeGuias(contenido: @Composable () -> Unit) {
    val estado = remember { EstadoGuias() }
    CompositionLocalProvider(LocalGuias provides estado) {
        Box(Modifier.fillMaxSize()) {
            contenido()
            estado.activa?.let { guia -> SuperposicionGuia(guia, estado) }
        }
    }
}

// ==================================================================
//  Dibujo de la guía (estilo mínimo: fondo oscuro, texto blanco y una flecha al elemento)
// ==================================================================

private const val ESPERA_ANTES_DE_ABRIR_MS = 450L
private val MARGEN_FOCO = 8.dp
private val SEPARACION_TEXTO = 84.dp     // deja sitio para la flecha entre el texto y el foco
private val MARGEN_PANTALLA = 24.dp
private val GROSOR_FLECHA = 5.dp
private val PUNTA_FLECHA = 22.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuperposicionGuia(guia: EstadoGuias.GuiaActiva, estado: EstadoGuias) {
    var indice by remember(guia) { mutableIntStateOf(0) }
    val paso = guia.pasos[indice]
    val objetivo = paso.objetivo?.let { estado.objetivos[it] }
    val foco = objetivo?.limites?.takeIf { !it.isEmpty }
    val densidad = androidx.compose.ui.platform.LocalDensity.current
    val margenFoco = with(densidad) { MARGEN_FOCO.toPx() }
    val separacion = with(densidad) { SEPARACION_TEXTO.toPx() }
    val margenPantalla = with(densidad) { MARGEN_PANTALLA.toPx() }
    val grosor = with(densidad) { GROSOR_FLECHA.toPx() }
    val punta = with(densidad) { PUNTA_FLECHA.toPx() }
    val altoBarraEstado = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(densidad)

    // Si el objetivo está fuera de la zona visible (formulario largo), se desplaza hasta él
    LaunchedEffect(guia, indice, objetivo?.traerAVista) {
        objetivo?.traerAVista?.bringIntoView()
    }

    val ultimo = indice == guia.pasos.lastIndex
    val siguiente: () -> Unit = { if (ultimo) estado.terminar(guia.clave) else indice++ }
    // La flecha se calcula al colocar el texto y se lee solo al dibujar
    var flecha by remember { mutableStateOf<Flecha?>(null) }

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .testTag("guia_superposicion")
            .semantics { contentDescription = "Guía, paso ${indice + 1} de ${guia.pasos.size}: ${paso.titulo}. Toca para continuar" }
            // Tocar en cualquier parte pasa al siguiente paso; la pantalla de abajo no responde mientras tanto
            .pointerInput(guia, indice) { detectTapGestures { siguiente() } }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawRect(COLOR_SOMBRA)
                if (foco != null) {
                    val recorte = foco.inflate(margenFoco)
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(recorte.left, recorte.top),
                        size = Size(recorte.width, recorte.height),
                        cornerRadius = CornerRadius(margenFoco * 2, margenFoco * 2),
                        blendMode = BlendMode.Clear,
                    )
                }
                drawContent()
                flecha?.let { dibujarFlecha(it, grosor, punta) }
            },
        content = {
            TextoGuia(paso)
            BarraGuia(
                numero = indice + 1, total = guia.pasos.size,
                onOmitir = { estado.terminar(guia.clave) },
                onAtras = { if (indice > 0) indice-- },
                onSiguiente = siguiente,
            )
        },
    ) { medibles, restricciones ->
        val pantalla = Size(restricciones.maxWidth.toFloat(), restricciones.maxHeight.toFloat())
        val anchoTexto = (pantalla.width * (if (foco == null) 0.8f else 0.66f)).roundToInt()
        val texto = medibles[0].measure(restricciones.copy(minWidth = 0, minHeight = 0, maxWidth = anchoTexto))
        val barra = medibles[1].measure(restricciones.copy(minWidth = 0, minHeight = 0))
        layout(restricciones.maxWidth, restricciones.maxHeight) {
            val recorte = foco?.inflate(margenFoco)
            val base = posicionTarjeta(recorte, pantalla, Size(texto.width.toFloat(), texto.height.toFloat()), separacion, margenPantalla)
            // Sin foco el texto va centrado; con foco, alineado al margen izquierdo como en un apunte a mano
            val posicionTexto = if (recorte == null) base else IntOffset(margenPantalla.roundToInt(), base.y)
            texto.place(posicionTexto)
            val limitesTexto = Rect(Offset(posicionTexto.x.toFloat(), posicionTexto.y.toFloat()), Size(texto.width.toFloat(), texto.height.toFloat()))
            flecha = recorte?.let { trazarFlecha(limitesTexto, it, pantalla) }
            // La barra de botones va abajo, salvo que el foco esté ahí (barra de navegación): entonces arriba
            val barraArriba = recorte != null && recorte.bottom > pantalla.height * 0.8f
            barra.place(0, if (barraArriba) altoBarraEstado else (pantalla.height - barra.height).roundToInt())
        }
    }
}

private val COLOR_SOMBRA = Color(0xF21B2432)   // Tinta al 95 %: la pantalla de abajo solo se intuye
private val BLANCO_SUAVE = Color(0xD9FFFFFF)

@Composable
private fun TextoGuia(paso: PasoGuia) {
    Column(Modifier.testTag("guia_tarjeta")) {
        Text(paso.titulo, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Espacio.s))
        Text(paso.texto, style = MaterialTheme.typography.bodyLarge, color = BLANCO_SUAVE)
    }
}

@Composable
private fun BarraGuia(numero: Int, total: Int, onOmitir: () -> Unit, onAtras: () -> Unit, onSiguiente: () -> Unit) {
    val colores = ButtonDefaults.textButtonColors(contentColor = Color.White)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.s, vertical = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (total > 1) TextButton(onClick = onOmitir, colors = colores) { Text("Omitir") }
        Spacer(Modifier.weight(1f))
        Text("$numero de $total", style = MaterialTheme.typography.labelLarge, color = BLANCO_SUAVE)
        Spacer(Modifier.width(Espacio.s))
        if (numero > 1) TextButton(onClick = onAtras, colors = colores) { Text("Atrás") }
        TextButton(onClick = onSiguiente, colors = colores, modifier = Modifier.testTag("guia_siguiente")) {
            Text(if (numero == total) "Entendido" else "Siguiente →", fontWeight = FontWeight.Bold)
        }
    }
}

/** Flecha curva (Bézier cuadrática) del texto al foco: nace horizontal y llega vertical, como trazada a mano. */
class Flecha(val inicio: Offset, val control: Offset, val fin: Offset)

/**
 * Traza la flecha desde el borde del texto hasta el borde del foco más cercano.
 * Si el foco está debajo del texto, llega a su borde superior; si está arriba,
 * al inferior. Función pura.
 */
fun trazarFlecha(texto: Rect, foco: Rect, pantalla: Size, holgura: Float = 14f): Flecha {
    val focoDebajo = foco.top >= texto.bottom
    val yFin = if (focoDebajo) foco.top - holgura else foco.bottom + holgura
    // Sale por el lado derecho del texto cuando el foco queda a la derecha; si no, baja/sube desde su primera línea
    val haciaLaDerecha = foco.center.x > texto.right + holgura * 2
    val inicio = if (haciaLaDerecha) Offset(texto.right + holgura, texto.center.y)
    else Offset(texto.left + holgura * 2, if (focoDebajo) texto.bottom + holgura else texto.top - holgura)
    val xFin = (if (haciaLaDerecha) foco.center.x else maxOf(inicio.x + holgura * 7, foco.left + holgura * 2))
        .coerceIn(foco.left + holgura, foco.right - holgura).coerceIn(holgura, pantalla.width - holgura)
    val fin = Offset(xFin, yFin)
    val control = if (haciaLaDerecha) Offset(fin.x, inicio.y) else Offset(inicio.x, (inicio.y + fin.y) / 2f)
    return Flecha(inicio, control, fin)
}

private fun DrawScope.dibujarFlecha(f: Flecha, grosor: Float, punta: Float) {
    val trazo = Path().apply { moveTo(f.inicio.x, f.inicio.y); quadraticTo(f.control.x, f.control.y, f.fin.x, f.fin.y) }
    val estilo = Stroke(width = grosor, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(trazo, Color.White, style = estilo)
    // Punta: dos trazos abiertos sobre la tangente final (dirección control → fin)
    val dir = f.fin - f.control
    val largo = kotlin.math.hypot(dir.x, dir.y).takeIf { it > 0.5f } ?: return
    val u = Offset(dir.x / largo, dir.y / largo)
    val n = Offset(-u.y, u.x)
    val ala1 = f.fin - u * punta + n * (punta * 0.55f)
    val ala2 = f.fin - u * punta - n * (punta * 0.55f)
    drawLine(Color.White, ala1, f.fin, grosor, StrokeCap.Round)
    drawLine(Color.White, ala2, f.fin, grosor, StrokeCap.Round)
}

/**
 * Dónde va el texto: debajo del foco si cabe, si no encima; sin foco, al
 * centro. Siempre dentro de la pantalla con el margen dado. Función pura.
 */
fun posicionTarjeta(foco: Rect?, pantalla: Size, tarjeta: Size, separacion: Float, margen: Float): IntOffset {
    val x = ((pantalla.width - tarjeta.width) / 2f).coerceAtLeast(margen)
    if (foco == null) {
        return IntOffset(x.roundToInt(), ((pantalla.height - tarjeta.height) / 2f).coerceAtLeast(margen).roundToInt())
    }
    val debajo = foco.bottom + separacion
    val encima = foco.top - separacion - tarjeta.height
    val y = when {
        debajo + tarjeta.height + margen <= pantalla.height -> debajo
        encima >= margen -> encima
        // No cabe ni arriba ni abajo (objetivo muy alto): se pega al borde que deje más foco visible
        foco.center.y > pantalla.height / 2f -> margen
        else -> pantalla.height - margen - tarjeta.height
    }
    return IntOffset(x.roundToInt(), y.coerceAtLeast(margen).roundToInt())
}
