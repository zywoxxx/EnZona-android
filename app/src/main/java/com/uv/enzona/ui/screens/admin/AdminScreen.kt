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
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.ui.components.ContenidoCarga
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.components.rememberOperacion
import com.uv.enzona.ui.components.Operacion
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
            Text("Resumen, usuarios y eventos de la plataforma", color = TextoSecundario)
        }
        Spacer(Modifier.height(12.dp))

        val pestanas = listOf("Resumen", "Usuarios", "Eventos")
        TabRow(
            selectedTabIndex = pestana,
            containerColor = MoradoFondo,
            contentColor = NaranjaAcento
        ) {
            pestanas.forEachIndexed { indice, titulo ->
                Tab(selected = pestana == indice, onClick = { pestana = indice },
                    text = { Text(titulo, color = if (pestana == indice) NaranjaAcento else TextoSecundario) })
            }
        }

        when (pestana) {
            0 -> PestanaEstadisticas()
            1 -> PestanaUsuarios()
            else -> PestanaEventos()
        }
    }
}

@Composable
private fun PestanaUsuarios() {
    val carga = rememberCarga { Repositorio.actual.usuarios() }
    val operacion = rememberOperacion()
    val propioId = SessionManager.usuarioId

    ContenidoCarga(carga, textoCargando = "Cargando cuentas…") { usuarios ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            operacion.error?.let { mensaje ->
                item(key = "error") { Text(mensaje, color = RojoError, style = MaterialTheme.typography.bodyMedium) }
            }
            items(usuarios, key = { it.id }) { usuario ->
                TarjetaUsuario(usuario, esPropio = usuario.id == propioId, operacion = operacion)
            }
        }
    }
}

@Composable
private fun TarjetaUsuario(usuario: Usuario, esPropio: Boolean, operacion: Operacion) {
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
            // La API de EnZona no permite asignar roles desde la administración: se muestran sin editar
            if (!Repositorio.actual.capacidades.rolesPorAdministrador) {
                Text(
                    usuario.roles.joinToString { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium,
                )
            } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Rol.entries.forEach { rol ->
                    val activo = rol in usuario.roles
                    FilterChip(
                        selected = activo,
                        enabled = !esPropio && !operacion.enCurso,
                        onClick = { operacion.ejecutar<Any> { Repositorio.actual.asignarRol(usuario.id, rol, !activo) } },
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
                TextButton(
                    enabled = !operacion.enCurso,
                    onClick = {
                        operacion.ejecutar<Any> {
                            Repositorio.actual.cambiarEstadoUsuario(usuario.id, if (bloqueado) EstadoUsuario.ACTIVO else EstadoUsuario.BLOQUEADO)
                        }
                    },
                ) {
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
    val carga = rememberCarga { Repositorio.actual.todosLosEventos() }

    ContenidoCarga(carga, textoCargando = "Cargando eventos…") { eventos ->
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
}

@Composable
private fun TarjetaEventoAdmin(evento: Evento) {
    val sinBoletos = !evento.tieneBoletos
    val butacas = if (evento.requiereAsiento) evento.aforo else 0
    val operacion = rememberOperacion()

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
                "Organiza: ${evento.organizadorNombre ?: "—"}",
                color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
            )
            // Red de seguridad humana tras la moderación automática: el administrador retira una portada inapropiada
            if (evento.imagenUrl != null) {
                val quitarPortada = rememberOperacion()
                TextButton(
                    enabled = !quitarPortada.enCurso,
                    onClick = { quitarPortada.ejecutar<Evento> { Repositorio.actual.quitarImagenEvento(evento.id) } },
                ) {
                    Text(if (quitarPortada.enCurso) "Quitando portada…" else "Quitar portada", color = RojoError, style = MaterialTheme.typography.labelMedium)
                }
                quitarPortada.error?.let { Text(it, color = RojoError, style = MaterialTheme.typography.labelMedium) }
            }
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
                        enabled = sinBoletos && !operacion.enCurso &&
                            evento.estado != EstadoEvento.CANCELADO && evento.estado != EstadoEvento.REALIZADO,
                        onCheckedChange = { activar ->
                            // Distribución automática a partir del aforo actual: filas de 12 butacas hasta cubrirlo
                            val porFila = 12
                            val filas = ((evento.aforo + porFila - 1) / porFila).coerceIn(1, 26)
                            operacion.ejecutar<Any> {
                                Repositorio.actual.cambiarAsientos(
                                    evento.id, if (activar) listOf(LayoutSeccion("General", filas, porFila)) else emptyList(),
                                )
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
                operacion.error?.let { Text(it, color = RojoError, style = MaterialTheme.typography.labelMedium) }
            }

            // ---- Cancelación administrativa (RF-18 + RF-14): motivo obligatorio, sin comisión ----
            // La política se pide al servidor al tocar «Cancelar evento»: una consulta por evento al cargar
            // la lista sería un desperdicio.
            var politica by remember { androidx.compose.runtime.mutableStateOf<PoliticaCancelacion?>(null) }
            var errorCancelacion by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var resultado by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            val cancelacion = rememberOperacion()
            val yaComenzo = !evento.fecha.isAfter(FechaEvento.ahora())
            if (evento.estado == EstadoEvento.CANCELADO) {
                Text(
                    listOfNotNull(
                        "Cancelado",
                        evento.canceladoPorNombre?.let { "por $it" },
                        evento.motivoCancelacion?.let { "· $it" },
                    ).joinToString(" "),
                    color = RojoError, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (evento.estado != EstadoEvento.REALIZADO) {
                TextButton(
                    enabled = !yaComenzo && !cancelacion.enCurso,
                    onClick = {
                        errorCancelacion = null
                        cancelacion.ejecutar(
                            alTerminar = { p: PoliticaCancelacion -> politica = p },
                            alFallar = { errorCancelacion = it.message },
                        ) { Repositorio.actual.politicaCancelacion(evento.id, administrativa = true) }
                    },
                ) {
                    Text(
                        if (cancelacion.enCurso && politica == null) "Consultando…" else "Cancelar evento",
                        color = if (!yaComenzo) RojoError else TextoSecundario,
                    )
                }
                if (yaComenzo) {
                    Text(
                        "El evento ya comenzó. No se puede cancelar; solo puede quedar como finalizado.",
                        color = TextoSecundario, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (resultado != null) {
                Text(resultado!!, color = VerdeOk, style = MaterialTheme.typography.labelMedium)
            }
            if (errorCancelacion != null && politica == null) {
                Text(errorCancelacion!!, color = RojoError, style = MaterialTheme.typography.labelMedium)
            }
            politica?.let { vigente ->
                com.uv.enzona.ui.components.DialogoCancelarEvento(
                    nombreEvento = evento.nombre,
                    politica = vigente,
                    administrativa = true,
                    procesando = cancelacion.enCurso,
                    error = errorCancelacion,
                    onCancelar = { if (!cancelacion.enCurso) { politica = null; errorCancelacion = null } },
                    onConfirmar = { motivo ->
                        errorCancelacion = null
                        cancelacion.ejecutar(
                            alTerminar = { r: com.uv.enzona.data.model.ResultadoCancelacion ->
                                politica = null
                                resultado = "Cancelado: ${r.boletosAnulados} boletos anulados, ${com.uv.enzona.util.Formato.importeMxn(r.importeReembolsado.toDouble())} reembolsados, sin comisión al organizador."
                            },
                            alFallar = { errorCancelacion = it.message },
                        ) {
                            Repositorio.actual.cancelarEvento(evento.id, motivo = motivo, aceptaComision = false, administrativa = true)
                        }
                    },
                )
            }
        }
    }
}
