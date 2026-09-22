package com.uv.enzona.ui.screens.admin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.LayoutSeccion
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.Usuario
import com.uv.enzona.ui.screens.organizer.EtiquetaEstado
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.MoradoClaro
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import com.uv.enzona.util.FechaEvento

/** Panel de administración: usuarios y eventos de toda la plataforma (RF-18). */
@Composable
fun AdminScreen() {
    var pestana by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoradoFondo)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text("Administración", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
            Text("Usuarios y eventos de la plataforma", color = TextoSecundario)
        }
        Spacer(Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = pestana,
            containerColor = MoradoFondo,
            contentColor = NaranjaAcento
        ) {
            Tab(selected = pestana == 0, onClick = { pestana = 0 },
                text = { Text("Usuarios", color = if (pestana == 0) NaranjaAcento else TextoSecundario) })
            Tab(selected = pestana == 1, onClick = { pestana = 1 },
                text = { Text("Eventos", color = if (pestana == 1) NaranjaAcento else TextoSecundario) })
        }

        if (pestana == 0) PestanaUsuarios() else PestanaEventos()
    }
}

@Composable
private fun PestanaUsuarios() {
    val usuarios = MockRepository.usuarios
    val propioId = SessionManager.usuarioId

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(usuarios, key = { it.id }) { usuario ->
            TarjetaUsuario(usuario, esPropio = usuario.id == propioId)
        }
    }
}

@Composable
private fun TarjetaUsuario(usuario: Usuario, esPropio: Boolean) {
    val bloqueado = usuario.estado == EstadoUsuario.BLOQUEADO
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MoradoSuperficie)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(usuario.nombre, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Text(usuario.correo, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                    Text(usuario.curp, color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    when (usuario.estado) {
                        EstadoUsuario.ACTIVO -> "Activo"
                        EstadoUsuario.PENDIENTE -> "Pendiente"
                        EstadoUsuario.BLOQUEADO -> "Bloqueado"
                    },
                    color = when (usuario.estado) {
                        EstadoUsuario.ACTIVO -> VerdeOk
                        EstadoUsuario.PENDIENTE -> TextoSecundario
                        EstadoUsuario.BLOQUEADO -> RojoError
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Roles", color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Rol.entries.forEach { rol ->
                    val activo = rol in usuario.roles
                    FilterChip(
                        selected = activo,
                        enabled = !esPropio,
                        onClick = { MockRepository.asignarRol(usuario.id, rol, !activo) },
                        label = {
                            Text(
                                rol.name.take(4).lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NaranjaAcento,
                            selectedLabelColor = Color.White,
                            containerColor = MoradoFondo,
                            labelColor = TextoSecundario,
                        )
                    )
                }
            }

            if (!esPropio) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    MockRepository.cambiarEstadoUsuario(
                        usuario.id,
                        if (bloqueado) EstadoUsuario.ACTIVO else EstadoUsuario.BLOQUEADO
                    )
                }) {
                    Text(
                        if (bloqueado) "Desbloquear cuenta" else "Bloquear cuenta",
                        color = if (bloqueado) VerdeOk else RojoError
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Es tu propia cuenta: no puedes modificar tus roles ni bloquearte.",
                    color = TextoSecundario, style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun PestanaEventos() {
    val eventos = MockRepository.eventos

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(eventos, key = { it.id }) { evento ->
            TarjetaEventoAdmin(evento)
        }
    }
}

@Composable
private fun TarjetaEventoAdmin(evento: Evento) {
    val organizador = MockRepository.usuarios.find { it.id == evento.organizadorId }
    val sinBoletos = MockRepository.puedeCambiarAsientos(evento.id)
    val butacas = MockRepository.asientosDe(evento.id).size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MoradoSuperficie)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    evento.nombre, color = TextoPrincipal,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                EtiquetaEstado(evento.estado)
            }
            Text(
                "Organiza: ${organizador?.nombre ?: "—"}",
                color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${evento.lugar} · ${FechaEvento.corto(evento.fecha)} · ${evento.vendidos}/${evento.aforo} lugares",
                color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
            )

            // ---- Interruptor de asientos numerados (RF-09) ----
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MoradoFondo)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Filled.EventSeat, contentDescription = null,
                            tint = if (evento.requiereAsiento) NaranjaAcento else TextoSecundario
                        )
                        Text(
                            "  ¿Requiere asientos?",
                            color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = evento.requiereAsiento,
                        enabled = sinBoletos,
                        onCheckedChange = { activar ->
                            if (activar) {
                                // Distribución automática a partir del aforo actual:
                                // filas de 12 butacas hasta cubrirlo
                                val porFila = 12
                                val filas = ((evento.aforo + porFila - 1) / porFila).coerceIn(1, 26)
                                MockRepository.activarAsientos(
                                    evento.id, listOf(LayoutSeccion("General", filas, porFila))
                                )
                            } else {
                                MockRepository.desactivarAsientos(evento.id)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NaranjaAcento,
                            uncheckedTrackColor = MoradoClaro,
                        )
                    )
                }
                Text(
                    when {
                        !sinBoletos -> "Bloqueado: el evento ya tiene boletos emitidos."
                        evento.requiereAsiento -> "$butacas butacas generadas; el aforo se ajustó a ese número."
                        else -> "Al activarlo se genera un mapa de butacas en filas de 12."
                    },
                    color = if (!sinBoletos) AmarilloAcento else TextoSecundario,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // ---- Cancelación administrativa (RF-18 + RF-14): motivo obligatorio, sin comisión ----
            val politica = MockRepository.politicaCancelacionDe(evento.id, administrativa = true)
            var confirmar by remember { androidx.compose.runtime.mutableStateOf(false) }
            var errorCancelacion by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var resultado by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            if (evento.estado == EstadoEvento.CANCELADO) {
                Text(
                    listOfNotNull(
                        "Cancelado",
                        evento.canceladoPor?.let { id -> MockRepository.usuarios.find { it.id == id }?.let { "por ${it.nombre}" } },
                        evento.motivoCancelacion?.let { "· $it" },
                    ).joinToString(" "),
                    color = RojoError, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (evento.estado != EstadoEvento.REALIZADO && politica != null) {
                TextButton(onClick = { errorCancelacion = null; confirmar = true }, enabled = politica.permitida) {
                    Text("Cancelar evento", color = if (politica.permitida) RojoError else TextoSecundario)
                }
                if (!politica.permitida) {
                    Text(politica.motivoBloqueo ?: "", color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (resultado != null) {
                Text(resultado!!, color = VerdeOk, style = MaterialTheme.typography.labelMedium)
            }
            if (confirmar && politica != null) {
                com.uv.enzona.ui.components.DialogoCancelarEvento(
                    nombreEvento = evento.nombre,
                    politica = politica,
                    administrativa = true,
                    procesando = false,
                    error = errorCancelacion,
                    onCancelar = { confirmar = false },
                    onConfirmar = { motivo ->
                        MockRepository.cancelarEvento(
                            eventoId = evento.id,
                            ejecutadoPor = SessionManager.usuarioId,
                            motivo = motivo,
                            administrativa = true,
                        ).onSuccess { r ->
                            confirmar = false
                            resultado = "Cancelado: ${r.boletosAnulados} boletos anulados, ${com.uv.enzona.util.Formato.importeMxn(r.importeReembolsado.toDouble())} reembolsados, sin comisión al organizador."
                        }.onFailure { errorCancelacion = it.message }
                    },
                )
            }
        }
    }
}
