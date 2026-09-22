package com.uv.enzona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.ui.components.EstadoVacio
import com.uv.enzona.ui.components.Etiqueta
import com.uv.enzona.ui.components.EtiquetaEstadoBoleto
import com.uv.enzona.ui.components.TarjetaEnZona
import com.uv.enzona.ui.preview.Fixtures
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.FechaEvento
import com.uv.enzona.ui.components.Tono
import androidx.compose.material.icons.filled.EventBusy

/**
 * Mis boletos (RF-12): los vigentes primero, después los usados, cancelados o
 * expirados. Cada fila muestra evento, fecha, tipo, asiento si aplica y el
 * estado del boleto (que no es el estado del pago).
 */
@Composable
fun TicketsScreen(onBoletoClick: (Long) -> Unit, onExplorar: (() -> Unit)? = null) {
    val boletos = MockRepository.boletosDe(SessionManager.usuarioId)
    val vigentes = boletos.filter { it.estado == EstadoBoleto.VALIDO }
    val anteriores = boletos.filter { it.estado != EstadoBoleto.VALIDO }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Espacio.margen, end = Espacio.margen, top = Espacio.m, bottom = Espacio.xl),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item(key = "titulo") {
            Text("Mis boletos", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        if (boletos.isEmpty()) {
            item(key = "vacio") {
                EstadoVacio(
                    icono = Icons.Filled.ConfirmationNumber,
                    titulo = "Aún no tienes boletos",
                    texto = "Cuando confirmes asistencia o compres un boleto, aparecerá aquí con su código QR.",
                    textoAccion = if (onExplorar != null) "Explorar eventos" else null,
                    onAccion = onExplorar,
                )
            }
        }
        if (vigentes.isNotEmpty()) {
            item(key = "sub_vigentes") { Subtitulo("Vigentes", vigentes.size) }
            items(vigentes, key = { it.id }) { TarjetaBoleto(it, posicionEnOrden(it)) { onBoletoClick(it.id) } }
        }
        if (anteriores.isNotEmpty()) {
            item(key = "sub_anteriores") { Subtitulo("Anteriores", anteriores.size) }
            items(anteriores, key = { "ant_${it.id}" }) { TarjetaBoleto(it, posicionEnOrden(it)) { onBoletoClick(it.id) } }
        }
    }
}

@Composable
private fun Subtitulo(texto: String, cantidad: Int) {
    Text(
        "$texto · $cantidad",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Espacio.xs),
    )
}

/** "k de N" cuando la orden tiene más de un boleto; null si es único. */
private fun posicionEnOrden(detalle: BoletoDetalle): Pair<Int, Int>? {
    val hermanos = MockRepository.boletosDeOrden(detalle.orden.id)
    if (hermanos.size <= 1) return null
    return (hermanos.indexOfFirst { it.id == detalle.id } + 1) to hermanos.size
}

/** Fila de boleto: bloque de fecha, datos y estado. */
@Composable
fun TarjetaBoleto(detalle: BoletoDetalle, posicion: Pair<Int, Int>? = null, onClick: () -> Unit) {
    val vigente = detalle.estado == EstadoBoleto.VALIDO
    TarjetaEnZona(onClick = onClick, relleno = PaddingValues(Espacio.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BloqueFechaBoleto(detalle.evento.fecha, atenuado = !vigente)
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Text(
                    detalle.evento.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (vigente) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (posicion != null) {
                    Text(
                        "Boleto ${posicion.first} de ${posicion.second}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(FechaEvento.hora(detalle.evento.fecha), detalle.evento.lugar).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(detalle.nombreTipo, detalle.asiento?.let { "${it.seccion} ${it.etiquetaCorta}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Espacio.s))
                if (detalle.estado == EstadoBoleto.CANCELADO && detalle.evento.cancelado) {
                    Etiqueta(
                        if (detalle.orden.total > 0) "Evento cancelado · reembolso en proceso" else "Evento cancelado",
                        tono = Tono.Error, icono = Icons.Filled.EventBusy,
                    )
                } else {
                    EtiquetaEstadoBoleto(detalle.estado)
                }
            }
            Spacer(Modifier.width(Espacio.s))
            Icon(
                Icons.Filled.QrCode2,
                contentDescription = if (vigente) "Ver código QR" else null,
                tint = if (vigente) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun BloqueFechaBoleto(fecha: java.time.LocalDateTime, atenuado: Boolean) {
    val dia = FechaEvento.dia(fecha)
    val mes = FechaEvento.mesCorto(fecha)
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 60.dp)
            .background(
                if (atenuado) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.shapes.small,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dia,
            style = MaterialTheme.typography.headlineSmall,
            color = if (atenuado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            mes,
            style = MaterialTheme.typography.labelMedium,
            color = if (atenuado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewBoletos() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            TarjetaBoleto(Fixtures.boletoValido) {}
            TarjetaBoleto(Fixtures.boletoGratis) {}
            TarjetaBoleto(Fixtures.boletoUsado) {}
            TarjetaBoleto(Fixtures.boletoCancelado) {}
        }
    }
}
