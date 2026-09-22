package com.uv.enzona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.NotificacionAsistente
import com.uv.enzona.data.model.TipoNotificacion
import com.uv.enzona.ui.components.BarraSuperiorEnZona
import com.uv.enzona.ui.components.EstadoVacio
import com.uv.enzona.ui.components.TarjetaEnZona
import com.uv.enzona.ui.theme.EnZonaTema
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.FechaEvento
import java.time.LocalDateTime

/**
 * Centro de notificaciones del asistente (v4): eventos próximos con boleto
 * (recordatorio), eventos cancelados y confirmaciones de compra. Se abre desde
 * la campana de Inicio. Tocar una notificación la marca como leída y abre el
 * boleto o el evento relacionado.
 */

/** Pestañas del centro de notificaciones. */
private enum class FiltroNotificacion(val etiqueta: String, val tipos: Set<TipoNotificacion>) {
    TODAS("Todas", TipoNotificacion.entries.toSet()),
    PROXIMOS("Próximos", setOf(TipoNotificacion.RECORDATORIO, TipoNotificacion.FECHA_CAMBIADA)),
    CANCELADOS("Cancelados", setOf(TipoNotificacion.EVENTO_CANCELADO)),
    COMPRAS("Compras", setOf(TipoNotificacion.COMPRA_CONFIRMADA)),
}

@Composable
fun NotificacionesScreen(
    onVolver: () -> Unit,
    onAbrirBoleto: (Long) -> Unit,
    onAbrirEvento: (Long) -> Unit,
) {
    val usuarioId = SessionManager.usuarioId
    var filtro by rememberSaveable { mutableStateOf(FiltroNotificacion.TODAS) }

    // Se lee de la lista observable: al marcar como leída la tarjeta se actualiza sola
    val todas = MockRepository.notificaciones.toList()
    val propias = MockRepository.notificacionesDe(usuarioId)
    val visibles = propias.filter { it.tipo in filtro.tipos }
    val noLeidas = todas.count { it.usuarioId == usuarioId && !it.leida }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = "Notificaciones", onVolver = onVolver) },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = Espacio.margen, end = Espacio.margen, top = Espacio.s, bottom = Espacio.xl),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            item(key = "filtros") {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                        items(FiltroNotificacion.entries) { f ->
                            val cuantas = propias.count { it.tipo in f.tipos }
                            FilterChip(
                                selected = filtro == f,
                                onClick = { filtro = f },
                                label = { Text(if (cuantas > 0 && f != FiltroNotificacion.TODAS) "${f.etiqueta} ($cuantas)" else f.etiqueta) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            when (noLeidas) {
                                0 -> "Todo leído"
                                1 -> "1 sin leer"
                                else -> "$noLeidas sin leer"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (noLeidas > 0) {
                            TextButton(onClick = { MockRepository.marcarNotificacionesLeidas(usuarioId) }) {
                                Text("Marcar todas como leídas")
                            }
                        }
                    }
                }
            }
            items(visibles, key = { it.id }) { n ->
                TarjetaNotificacion(
                    notificacion = n,
                    onClick = {
                        MockRepository.marcarNotificacionLeida(n.id)
                        val boletoId = n.boletoId
                        if (boletoId != null && n.tipo != TipoNotificacion.EVENTO_CANCELADO) onAbrirBoleto(boletoId)
                        else onAbrirEvento(n.eventoId)
                    },
                )
            }
            if (visibles.isEmpty()) {
                item(key = "vacio") {
                    EstadoVacio(
                        icono = Icons.Filled.NotificationsNone,
                        titulo = when (filtro) {
                            FiltroNotificacion.TODAS -> "Sin notificaciones"
                            FiltroNotificacion.PROXIMOS -> "No tienes eventos próximos"
                            FiltroNotificacion.CANCELADOS -> "Ningún evento cancelado"
                            FiltroNotificacion.COMPRAS -> "Aún no hay compras"
                        },
                        texto = when (filtro) {
                            FiltroNotificacion.TODAS -> "Aquí verás los recordatorios de tus eventos, las cancelaciones y tus compras confirmadas."
                            FiltroNotificacion.PROXIMOS -> "Te avisaremos ${MockRepository.DIAS_RECORDATORIO} días antes de cada evento para el que tengas boleto."
                            FiltroNotificacion.CANCELADOS -> "Si un organizador cancela un evento con tu boleto, lo verás aquí con el estado del reembolso."
                            FiltroNotificacion.COMPRAS -> "Cada compra o confirmación de asistencia genera un aviso con acceso directo al QR."
                        },
                    )
                }
            }
        }
    }
}

// ==================================================================
//  Piezas
// ==================================================================

private data class EstiloNotificacion(val icono: ImageVector, val fondo: androidx.compose.ui.graphics.Color, val tinta: androidx.compose.ui.graphics.Color)

@Composable
private fun estiloDe(tipo: TipoNotificacion): EstiloNotificacion {
    val c = MaterialTheme.colorScheme
    val s = EnZonaTema.semanticos
    return when (tipo) {
        TipoNotificacion.RECORDATORIO -> EstiloNotificacion(Icons.Filled.Event, c.primaryContainer, c.onPrimaryContainer)
        TipoNotificacion.EVENTO_CANCELADO -> EstiloNotificacion(Icons.Filled.Cancel, c.errorContainer, c.onErrorContainer)
        TipoNotificacion.COMPRA_CONFIRMADA -> EstiloNotificacion(Icons.Filled.ConfirmationNumber, s.exitoContenedor, s.exito)
        TipoNotificacion.FECHA_CAMBIADA -> EstiloNotificacion(Icons.Filled.EditCalendar, c.tertiaryContainer, c.onTertiaryContainer)
    }
}

@Composable
private fun TarjetaNotificacion(notificacion: NotificacionAsistente, onClick: () -> Unit) {
    val estilo = estiloDe(notificacion.tipo)
    val estado = if (notificacion.leida) "leída" else "sin leer"
    TarjetaEnZona(
        onClick = onClick,
        relleno = PaddingValues(Espacio.m),
        modifier = Modifier.semantics { contentDescription = "${notificacion.titulo}, $estado. ${notificacion.texto}" },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(estilo.fondo, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(estilo.icono, contentDescription = null, tint = estilo.tinta)
            }
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notificacion.titulo,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notificacion.leida) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    notificacion.texto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notificacion.leida) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    FechaEvento.corto(notificacion.fecha),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ==================================================================
//  Previews
// ==================================================================

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewNotificaciones() {
    val f = LocalDateTime.of(2026, 9, 22, 9, 0)
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            TarjetaNotificacion(NotificacionAsistente(1, 4, 1, TipoNotificacion.RECORDATORIO, "«Feria de Emprendimiento FCAS» es en 3 días · Vie 25 Sep 2026 · 10:00 en Facultad de Negocios y Tecnologías. Lleva tu QR listo.", f)) {}
            TarjetaNotificacion(NotificacionAsistente(2, 4, 2, TipoNotificacion.EVENTO_CANCELADO, "El evento «Concierto: Orquesta Universitaria» fue cancelado. Recibirás el reembolso completo de tu compra.", f, leida = true)) {}
            TarjetaNotificacion(NotificacionAsistente(3, 4, 2, TipoNotificacion.COMPRA_CONFIRMADA, "Compraste 3 boletos para «Concierto: Orquesta Universitaria» · Sáb 3 Oct · 19:00. Tus QR están en Mis boletos.", f)) {}
        }
    }
}
