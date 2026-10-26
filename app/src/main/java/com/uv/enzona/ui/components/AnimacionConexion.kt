package com.uv.enzona.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.AzulEnZona
import com.uv.enzona.ui.theme.AzulMarino
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.GrisTexto

/*
 * Animación de conexión con el servidor (v6).
 *
 * Mientras la app recupera la sesión se arma el logo por partes, al estilo de
 * los comerciales de Little Caesars: primero se traza el contorno del pin de
 * ubicación, luego se llena el disco y el boleto aparece con un rebote, y al
 * final entra el nombre. El ciclo se repite mientras dure la espera. Todo se
 * dibuja con Canvas: no hay imágenes ni vídeo.
 */

/** Fondo azul muy claro de la pantalla de conexión. */
val AzulCielo = Color(0xFFE4F2FA)

/** Momentos (ms) de cada parte dentro de un ciclo. */
object TiemposConexion {
    const val CICLO = 2800L
    const val CONTORNO_FIN = 1000L
    const val DISCO_INICIO = 900L
    const val DISCO_FIN = 1150L
    const val BOLETO_INICIO = 1050L
    const val BOLETO_FIN = 1550L
    const val TEXTO_INICIO = 1400L
    const val TEXTO_FIN = 1800L
    const val SALIDA_INICIO = 2500L
}

/**
 * Cuánto está dibujada cada parte en el instante `ms` del ciclo.
 * `contorno`, `disco` y `texto` van de 0 a 1; `boleto` llega a 1 con un pequeño
 * rebote por encima (hasta ~1.1); `opacidad` baja a 0 al final del ciclo para
 * que el siguiente empiece limpio.
 */
class FasesConexion(
    val contorno: Float,
    val disco: Float,
    val boleto: Float,
    val texto: Float,
    val opacidad: Float,
)

fun fasesConexion(ms: Long): FasesConexion {
    val t = ((ms % TiemposConexion.CICLO) + TiemposConexion.CICLO) % TiemposConexion.CICLO
    return FasesConexion(
        contorno = suavizar(tramo(t, 0L, TiemposConexion.CONTORNO_FIN)),
        disco = suavizar(tramo(t, TiemposConexion.DISCO_INICIO, TiemposConexion.DISCO_FIN)),
        boleto = rebote(tramo(t, TiemposConexion.BOLETO_INICIO, TiemposConexion.BOLETO_FIN)),
        texto = suavizar(tramo(t, TiemposConexion.TEXTO_INICIO, TiemposConexion.TEXTO_FIN)),
        opacidad = 1f - tramo(t, TiemposConexion.SALIDA_INICIO, TiemposConexion.CICLO),
    )
}

/** Fracción recorrida de un tramo de tiempo, entre 0 y 1. */
private fun tramo(t: Long, inicio: Long, fin: Long): Float =
    ((t - inicio).toFloat() / (fin - inicio)).coerceIn(0f, 1f)

/** Arranca rápido y frena al final (ease-out cúbico). */
private fun suavizar(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

/** Pasa de largo y vuelve (ease-out back): el “pop” del boleto. */
private fun rebote(p: Float): Float {
    if (p <= 0f) return 0f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val q = p - 1f
    return 1f + c3 * q * q * q + c1 * q * q
}

/**
 * Pantalla completa de espera al abrir la app: logo animado sobre azul claro y
 * un texto de estado debajo.
 */
@Composable
fun PantallaConectando(texto: String = "Conectando con EnZona…", modifier: Modifier = Modifier) {
    var ms by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val inicio = withFrameMillis { it }
        while (true) withFrameMillis { ahora -> ms = ahora - inicio }
    }
    val fases = fasesConexion(ms)
    Column(
        modifier
            .fillMaxSize()
            .background(AzulCielo)
            .systemBarsPadding()
            .semantics { contentDescription = texto },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(200.dp)) { dibujarLogo(fases) }
        Spacer(Modifier.height(20.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = AzulMarino)) { append("En") }
                withStyle(SpanStyle(color = AzulEnZona)) { append("Zona") }
            },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .alpha(fases.texto * fases.opacidad)
                .offset(y = ((1f - fases.texto) * 12).dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = GrisTexto)
    }
}

/** Dibuja el pin con el boleto según las fases. El lienzo se asume cuadrado. */
private fun DrawScope.dibujarLogo(f: FasesConexion) {
    val lado = minOf(size.width, size.height)
    val centro = Offset(size.width / 2f, lado * 0.42f)
    val radio = lado * 0.33f
    val punta = Offset(centro.x, lado * 0.96f)
    val grosor = lado * 0.055f

    // Contorno del pin (teal) y anillo interior (marino), trazados progresivamente
    val pin = caminoDelPin(centro, radio, punta)
    val medida = PathMeasure().apply { setPath(pin, false) }
    val parcial = Path()
    medida.getSegment(0f, medida.length * f.contorno, parcial, true)
    drawPath(parcial, AzulEnZona, alpha = f.opacidad, style = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val radioAnillo = radio * 0.80f
    drawArc(
        color = AzulMarino,
        startAngle = -90f,
        sweepAngle = 360f * f.contorno,
        useCenter = false,
        topLeft = Offset(centro.x - radioAnillo, centro.y - radioAnillo),
        size = Size(radioAnillo * 2, radioAnillo * 2),
        alpha = f.opacidad,
        style = Stroke(grosor * 0.45f, cap = StrokeCap.Round),
    )

    // Disco interior con sus muescas laterales
    if (f.disco > 0f) {
        val alfaDisco = f.disco * f.opacidad
        drawCircle(AzulMarino.copy(alpha = 0.92f), radioAnillo, centro, alpha = alfaDisco)
        val muesca = radio * 0.13f
        drawCircle(AzulMarino, muesca, Offset(centro.x - radioAnillo, centro.y), alpha = alfaDisco)
        drawCircle(AzulMarino, muesca, Offset(centro.x + radioAnillo, centro.y), alpha = alfaDisco)
    }

    // Boleto: aparece con rebote, girado como en el logo
    if (f.boleto > 0f) {
        rotate(-35f, pivot = centro) {
            scale(f.boleto, pivot = centro) {
                dibujarBoleto(centro, Size(radio * 1.25f, radio * 0.62f), lado * 0.012f, f.opacidad)
            }
        }
    }
}

/** Pin de ubicación: círculo con punta abajo. */
private fun caminoDelPin(centro: Offset, radio: Float, punta: Offset): Path {
    val apertura = 48f // mitad del ángulo (grados) que abarca la punta sobre el círculo
    val caja = Rect(centro.x - radio, centro.y - radio, centro.x + radio, centro.y + radio)
    return Path().apply {
        // Empieza en la punta, sube por la derecha, rodea el círculo y vuelve por la izquierda
        moveTo(punta.x, punta.y)
        val inicio = 90f - apertura
        val x = centro.x + radio * kotlin.math.cos(Math.toRadians(inicio.toDouble())).toFloat()
        val y = centro.y + radio * kotlin.math.sin(Math.toRadians(inicio.toDouble())).toFloat()
        lineTo(x, y)
        arcTo(caja, inicio, -(360f - 2 * apertura), forceMoveTo = false)
        close()
    }
}

/** Boleto blanco con muescas en los extremos y dos líneas punteadas. */
private fun DrawScope.dibujarBoleto(centro: Offset, tam: Size, grosor: Float, opacidad: Float) {
    val izq = centro.x - tam.width / 2f
    val arriba = centro.y - tam.height / 2f
    val esquina = tam.height * 0.16f
    val muesca = tam.height * 0.19f
    val contorno = Path().apply {
        addRoundRect(RoundRect(Rect(Offset(izq, arriba), tam), CornerRadius(esquina)))
    }
    val forma = Path().apply {
        fillType = PathFillType.EvenOdd
        addPath(contorno)
        addOval(Rect(center = Offset(izq, centro.y), radius = muesca))
        addOval(Rect(center = Offset(izq + tam.width, centro.y), radius = muesca))
    }
    // Las muescas son medios círculos: lo que cae fuera del rectángulo se recorta
    clipPath(contorno) {
        drawPath(forma, Color.White, alpha = opacidad)
        drawPath(forma, AzulEnZona, alpha = opacidad, style = Stroke(grosor * 2))
    }
    val punteado = PathEffect.dashPathEffect(floatArrayOf(grosor * 2.2f, grosor * 2.2f))
    for (dx in listOf(-0.28f, 0.28f)) {
        val x = centro.x + tam.width * dx
        drawLine(
            AzulEnZona,
            Offset(x, arriba + tam.height * 0.16f),
            Offset(x, arriba + tam.height * 0.84f),
            strokeWidth = grosor,
            alpha = opacidad,
            pathEffect = punteado,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PantallaConectandoPreview() {
    EnZonaTheme { PantallaConectando() }
}
