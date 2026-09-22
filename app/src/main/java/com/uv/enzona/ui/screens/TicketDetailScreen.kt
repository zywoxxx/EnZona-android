package com.uv.enzona.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.ui.components.Aviso
import com.uv.enzona.ui.components.BarraSuperiorEnZona
import com.uv.enzona.ui.components.BotonSecundario
import com.uv.enzona.ui.components.EtiquetaEstadoBoleto
import com.uv.enzona.ui.components.Tono
import com.uv.enzona.ui.preview.Fixtures
import com.uv.enzona.ui.theme.BlancoQr
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.NegroQr
import com.uv.enzona.util.QrGenerator
import com.uv.enzona.util.FechaEvento

/**
 * Boleto electrónico con su código QR (RF-12).
 *
 * El elemento memorable de la app: un talón de boleto con muescas y línea de
 * corte. Arriba, los datos del evento; abajo, el QR sobre blanco puro con
 * margen y sin nada encima, seguido del código de referencia. El contenido del
 * QR ("codigo.firma") no cambia: es el que espera el validador.
 *
 * No se muestran datos personales: el boleto identifica al evento y al
 * asiento, no a la persona.
 */
@Composable
fun TicketDetailScreen(boletoId: Long, onVolver: () -> Unit, onAbrirBoleto: ((Long) -> Unit)? = null) {
    // Se resuelve desde las listas observables: si el validador lo marca
    // como usado, la pantalla se actualiza sola
    val boleto = MockRepository.boletos.find { it.id == boletoId } ?: return
    val detalle = MockRepository.detalleDe(boleto) ?: return
    val contexto = LocalContext.current
    var correoAbierto by rememberSaveable { mutableStateOf(false) }

    // Sube el brillo al máximo mientras el boleto está en pantalla
    DisposableEffect(Unit) {
        val ventana = (contexto as? Activity)?.window
        val parametros = ventana?.attributes
        val brilloOriginal = parametros?.screenBrightness ?: -1f
        if (ventana != null && parametros != null) {
            parametros.screenBrightness = 1f
            ventana.attributes = parametros
        }
        onDispose {
            if (ventana != null && parametros != null) {
                parametros.screenBrightness = brilloOriginal
                ventana.attributes = parametros
            }
        }
    }

    val qr = remember(detalle.contenidoQr) { QrGenerator.generar(detalle.contenidoQr).asImageBitmap() }
    // Boletos de la misma orden: "Boleto k de N" con navegación entre ellos
    val hermanos = MockRepository.boletosDeOrden(detalle.orden.id)
    val posicion = hermanos.indexOfFirst { it.id == detalle.id } + 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = "Tu boleto", onVolver = onVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hermanos.size > 1) {
                NavegacionEntreBoletos(
                    posicion = posicion,
                    total = hermanos.size,
                    onAnterior = { hermanos.getOrNull(posicion - 2)?.let { onAbrirBoleto?.invoke(it.id) } },
                    onSiguiente = { hermanos.getOrNull(posicion)?.let { onAbrirBoleto?.invoke(it.id) } },
                    habilitado = onAbrirBoleto != null,
                )
                Spacer(Modifier.height(Espacio.m))
            }
            TalonBoleto(detalle = detalle, qr = qr, posicion = if (hermanos.size > 1) posicion to hermanos.size else null)

            Spacer(Modifier.height(Espacio.l))
            TextoEstado(detalle)

            Spacer(Modifier.height(Espacio.l))

            // RF-12: en producción el backend envía el boleto por correo al emitirlo.
            // Aquí solo se abre la app de correo del teléfono; no hay servicio de envío.
            BotonSecundario(
                texto = "Enviar con mi app de correo",
                icono = Icons.Filled.MailOutline,
                onClick = {
                    val correo = SessionManager.usuario?.correo ?: ""
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(correo))
                        putExtra(Intent.EXTRA_SUBJECT, "Tu boleto EnZona · ${detalle.evento.nombre}")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            buildString {
                                appendLine("Evento: ${detalle.evento.nombre}")
                                appendLine("Lugar: ${detalle.evento.lugar}")
                                appendLine("Fecha: ${FechaEvento.corto(detalle.evento.fecha)}")
                                appendLine("Boleto: ${detalle.nombreTipo}")
                                detalle.etiquetaAsiento?.let { appendLine("Asiento: $it") }
                                appendLine("Código: ${detalle.codigo}")
                                appendLine()
                                appendLine("Contenido del QR: ${detalle.contenidoQr}")
                                appendLine("Presenta este código en el acceso. Es de un solo uso.")
                            }
                        )
                    }
                    contexto.startActivity(Intent.createChooser(intent, "Enviar boleto"))
                    correoAbierto = true
                },
            )
            if (correoAbierto) {
                Spacer(Modifier.height(Espacio.s))
                Aviso(
                    texto = "Se abrió tu aplicación de correo con los datos del boleto. EnZona todavía no envía boletos por sí misma: el envío automático llegará con el backend.",
                    tono = Tono.Neutro,
                )
            }

            Spacer(Modifier.height(Espacio.l))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Brightness7, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Espacio.s))
                Text(
                    "Brillo al máximo mientras muestras el boleto",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}

// ==================================================================
//  Navegación entre boletos de la misma orden
// ==================================================================

@Composable
private fun NavegacionEntreBoletos(posicion: Int, total: Int, onAnterior: () -> Unit, onSiguiente: () -> Unit, habilitado: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onAnterior, enabled = habilitado && posicion > 1,
            modifier = Modifier.size(com.uv.enzona.ui.theme.Tamanos.control),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Boleto anterior") }
        Text(
            "Boleto $posicion de $total",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onSiguiente, enabled = habilitado && posicion < total,
            modifier = Modifier.size(com.uv.enzona.ui.theme.Tamanos.control),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Boleto siguiente") }
    }
}

// ==================================================================
//  Talón
// ==================================================================

/**
 * Talón de boleto: mitad superior con los datos, línea de corte con muescas y
 * mitad inferior blanca con el QR. Cada mitad tiene su propia forma con
 * semicírculos recortados, de modo que las muescas dejan ver el fondo.
 */
@Composable
fun TalonBoleto(detalle: BoletoDetalle, qr: ImageBitmap?, modifier: Modifier = Modifier, posicion: Pair<Int, Int>? = null) {
    val vigente = detalle.estado == EstadoBoleto.VALIDO
    val muesca = 14.dp
    val esquina = 24.dp
    val fondoTalon = MaterialTheme.colorScheme.primary
    val sobreTalon = MaterialTheme.colorScheme.onPrimary

    Column(modifier.fillMaxWidth()) {
        // ---------- mitad superior: datos ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FormaTalon(esquina = esquina, muesca = muesca, muescasArriba = false, muescasAbajo = true))
                .background(fondoTalon)
                .padding(start = Espacio.xl, end = Espacio.xl, top = Espacio.xl, bottom = Espacio.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ConfirmationNumber, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Espacio.s))
                Text(
                    if (posicion != null) "Boleto ${posicion.first} de ${posicion.second}" else "Boleto EnZona",
                    style = MaterialTheme.typography.labelLarge,
                    color = sobreTalon,
                )
                Spacer(Modifier.weight(1f))
                EtiquetaEstadoBoleto(detalle.estado)
            }
            Spacer(Modifier.height(Espacio.m))
            Text(
                detalle.evento.nombre,
                style = MaterialTheme.typography.headlineSmall,
                color = sobreTalon,
            )
            Spacer(Modifier.height(Espacio.l))
            DatoTalon(Icons.Filled.CalendarMonth, "Fecha", FechaEvento.corto(detalle.evento.fecha))
            Spacer(Modifier.height(Espacio.m))
            DatoTalon(Icons.Filled.LocationOn, "Lugar", detalle.evento.lugar)
            Spacer(Modifier.height(Espacio.m))
            Row {
                DatoTalon(Icons.Filled.ConfirmationNumber, "Tipo", detalle.nombreTipo, Modifier.weight(1f))
                detalle.asiento?.let { asiento ->
                    DatoTalon(Icons.Filled.EventSeat, "Asiento", "${asiento.seccion}\nFila ${asiento.fila} · ${asiento.numero}", Modifier.weight(1f))
                }
            }
        }

        // ---------- línea de corte ----------
        LineaDeCorte(color = fondoTalon, muesca = muesca)

        // ---------- mitad inferior: QR ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FormaTalon(esquina = esquina, muesca = muesca, muescasArriba = true, muescasAbajo = false))
                .background(BlancoQr)
                .padding(start = Espacio.xl, end = Espacio.xl, top = Espacio.l, bottom = Espacio.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .background(BlancoQr)
                    .semantics { contentDescription = "Código QR del boleto ${detalle.codigo}" },
            ) {
                if (qr != null) {
                    Image(
                        bitmap = qr,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (vigente) 1f else 0.3f),
                    )
                } else {
                    // Solo en previews: el QR real se genera con ZXing en el dispositivo
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(Espacio.l)
                            .background(NegroQr.copy(alpha = 0.08f))
                    )
                }
            }
            Spacer(Modifier.height(Espacio.m))
            Text(
                detalle.codigo,
                color = NegroQr,
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.5.sp),
            )
            Text(
                "Código de referencia",
                color = NegroQr.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DatoTalon(icono: androidx.compose.ui.graphics.vector.ImageVector, etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    // Sobre azul marino: blanco al 100 % para el valor y al 80 % para la etiqueta (≥ 7:1)
    val sobre = MaterialTheme.colorScheme.onPrimary
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            icono, contentDescription = null,
            tint = sobre.copy(alpha = 0.8f), modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(Espacio.s))
        Column {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = sobre.copy(alpha = 0.8f))
            Text(valor, style = MaterialTheme.typography.bodyLarge, color = sobre)
        }
    }
}

/** Línea discontinua entre las dos mitades; las muescas quedan en los extremos. */
@Composable
private fun LineaDeCorte(color: Color, muesca: Dp) {
    val colorLinea = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        val margen = muesca.toPx() + 6.dp.toPx()
        drawLine(
            color = colorLinea,
            start = Offset(margen, size.height / 2),
            end = Offset(size.width - margen, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        )
    }
}

/**
 * Forma de mitad de talón: rectángulo con esquinas redondeadas en el lado
 * exterior y semicírculos recortados en el lado de la línea de corte.
 */
private class FormaTalon(
    private val esquina: Dp,
    private val muesca: Dp,
    private val muescasArriba: Boolean,
    private val muescasAbajo: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { esquina.toPx() }
        val m = with(density) { muesca.toPx() }
        val camino = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    topLeft = if (muescasArriba) androidx.compose.ui.geometry.CornerRadius.Zero else androidx.compose.ui.geometry.CornerRadius(r),
                    topRight = if (muescasArriba) androidx.compose.ui.geometry.CornerRadius.Zero else androidx.compose.ui.geometry.CornerRadius(r),
                    bottomRight = if (muescasAbajo) androidx.compose.ui.geometry.CornerRadius.Zero else androidx.compose.ui.geometry.CornerRadius(r),
                    bottomLeft = if (muescasAbajo) androidx.compose.ui.geometry.CornerRadius.Zero else androidx.compose.ui.geometry.CornerRadius(r),
                )
            )
            if (muescasArriba) {
                addOval(Rect(center = Offset(0f, 0f), radius = m))
                addOval(Rect(center = Offset(size.width, 0f), radius = m))
            }
            if (muescasAbajo) {
                addOval(Rect(center = Offset(0f, size.height), radius = m))
                addOval(Rect(center = Offset(size.width, size.height), radius = m))
            }
        }
        return Outline.Generic(camino)
    }
}

@Composable
private fun TextoEstado(detalle: BoletoDetalle) {
    val (texto, tono) = when (detalle.estado) {
        EstadoBoleto.VALIDO -> "Boleto válido, de un solo uso. Muestra el QR en el acceso." to Tono.Exito
        EstadoBoleto.USADO -> "Este boleto ya se utilizó el ${detalle.fechaUso ?: "—"}. No admite un segundo acceso." to Tono.Neutro
        EstadoBoleto.CANCELADO -> when {
            detalle.evento.cancelado && detalle.orden.total > 0 -> "Evento cancelado · reembolso en proceso. Recibirás el 100 % de tu compra por el mismo medio de pago." to Tono.Error
            detalle.evento.cancelado -> "Evento cancelado. Tu confirmación quedó anulada." to Tono.Error
            else -> "Este boleto fue cancelado." to Tono.Error
        }
        EstadoBoleto.EXPIRADO -> "La vigencia de este boleto terminó." to Tono.Neutro
    }
    Aviso(texto = texto, tono = tono)
}

// ==================================================================
//  Previews
// ==================================================================

@Preview(name = "Boleto válido", showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewTalonValido() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l)) {
            TalonBoleto(Fixtures.boletoValido, qr = null)
            Spacer(Modifier.height(Espacio.l))
            TextoEstado(Fixtures.boletoValido)
        }
    }
}

@Preview(name = "Boleto usado y gratuito", showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewTalonUsado() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.l)) {
            TalonBoleto(Fixtures.boletoUsado, qr = null)
            TalonBoleto(Fixtures.boletoGratis, qr = null)
        }
    }
}
