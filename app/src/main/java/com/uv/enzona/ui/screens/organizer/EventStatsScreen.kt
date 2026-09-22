package com.uv.enzona.ui.screens.organizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.theme.MoradoClaro
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import com.uv.enzona.util.FechaEvento

/**
 * Detalle del evento para el organizador: reportes de asistentes, ventas y
 * accesos validados (RF-17), más publicar (RF-08) y cancelar (RF-14).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventStatsScreen(
    eventoId: Long,
    onVolver: () -> Unit,
    onEditar: (Long) -> Unit,
) {
    val evento = MockRepository.eventos.find { it.id == eventoId } ?: return
    val boletos = MockRepository.boletosDeEvento(eventoId)
    val validados = boletos.count { it.estado == EstadoBoleto.USADO }
    val ingresos = evento.vendidos * evento.precioDesde
    var confirmarCancelacion by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var errorCancelacion by remember { mutableStateOf<String?>(null) }
    var procesandoCancelacion by remember { mutableStateOf(false) }
    // Política de cancelación (RF-14, v3): única fuente de verdad, calculada por el modelo
    val politica = MockRepository.politicaCancelacionDe(eventoId)
    val cargos = MockRepository.cargosOrganizador.filter { it.eventoId == eventoId }
    val yaComenzo = !evento.fecha.isAfter(FechaEvento.ahora())

    val ocupacion = if (evento.aforo > 0) evento.vendidos.toFloat() / evento.aforo else 0f
    val asistencia = if (evento.vendidos > 0) validados.toFloat() / evento.vendidos else 0f

    Scaffold(
        containerColor = MoradoFondo,
        topBar = {
            TopAppBar(
                title = { Text("Panel del evento", color = TextoPrincipal) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextoPrincipal)
                    }
                },
                actions = {
                    if (evento.estado != EstadoEvento.CANCELADO) {
                        IconButton(onClick = { onEditar(eventoId) }) {
                            Icon(Icons.Filled.Edit, "Editar", tint = NaranjaAcento)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MoradoFondo)
            )
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    evento.nombre, style = MaterialTheme.typography.titleLarge,
                    color = TextoPrincipal, modifier = Modifier.weight(1f)
                )
                EtiquetaEstado(evento.estado)
            }
            Text("${evento.lugar} · ${FechaEvento.corto(evento.fecha)}", color = TextoSecundario)

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tarjeta("Asistentes", "${evento.vendidos}", Modifier.weight(1f))
                Tarjeta("Validados", "$validados", Modifier.weight(1f))
                Tarjeta("Ingresos", "$${"%.0f".format(ingresos)}", Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            Barra("Ocupación del aforo", "${evento.vendidos} / ${evento.aforo}", ocupacion, NaranjaAcento)
            Spacer(Modifier.height(14.dp))
            Barra("Asistencia registrada en puerta", "$validados / ${evento.vendidos}", asistencia, VerdeOk)

            Spacer(Modifier.height(24.dp))
            Text("Boletos emitidos", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
            Spacer(Modifier.height(8.dp))
            if (boletos.isEmpty()) {
                Text("Todavía no hay boletos emitidos.", color = TextoSecundario)
            } else {
                boletos.forEach { boleto ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(boleto.codigo, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                boleto.etiquetaAsiento ?: boleto.nombreTipo,
                                color = TextoSecundario, style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Text(
                            when (boleto.estado) {
                                EstadoBoleto.VALIDO -> "Sin usar"
                                EstadoBoleto.USADO -> "Validado"
                                EstadoBoleto.CANCELADO -> "Cancelado"
                                EstadoBoleto.EXPIRADO -> "Expirado"
                            },
                            color = when (boleto.estado) {
                                EstadoBoleto.USADO -> VerdeOk
                                EstadoBoleto.CANCELADO -> RojoError
                                else -> TextoSecundario
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            PersonalDeAcceso()

            Spacer(Modifier.height(24.dp))

            if (evento.estado == EstadoEvento.CANCELADO) {
                com.uv.enzona.ui.components.Aviso(
                    titulo = "Evento cancelado",
                    texto = listOfNotNull(
                        evento.fechaCancelacion?.let { "Cancelado el ${FechaEvento.largo(it)}." },
                        evento.motivoCancelacion?.let { "Motivo: $it" },
                        if (evento.comisionCancelacion > 0) "Comisión de cancelación cargada a tu cuenta: ${com.uv.enzona.util.Formato.importeMxn(evento.comisionCancelacion)}." else "Sin comisión.",
                    ).joinToString(" "),
                    tono = com.uv.enzona.ui.components.Tono.Error,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (cargos.isNotEmpty()) {
                com.uv.enzona.ui.components.Aviso(
                    titulo = "Cargo pendiente en tu cuenta",
                    texto = cargos.joinToString("\n") { "${it.concepto}: ${com.uv.enzona.util.Formato.importeMxn(it.importe)}" + if (it.pagado) " (pagado)" else " (pendiente)" },
                    tono = com.uv.enzona.ui.components.Tono.Aviso,
                )
                Spacer(Modifier.height(10.dp))
            }

            if (evento.estado == EstadoEvento.BORRADOR) {
                OutlinedButton(
                    onClick = {
                        MockRepository.publicarEvento(eventoId)
                            .onSuccess { aviso = "El evento ya es visible para los asistentes." }
                            .onFailure { aviso = null; errorCancelacion = it.message }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Publicar evento", color = VerdeOk, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (evento.estado == EstadoEvento.PUBLICADO && yaComenzo) {
                OutlinedButton(
                    onClick = {
                        MockRepository.marcarRealizado(eventoId)
                        aviso = "El evento quedó marcado como finalizado."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Marcar como finalizado", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (evento.estado != EstadoEvento.CANCELADO && evento.estado != EstadoEvento.REALIZADO && politica != null) {
                OutlinedButton(
                    onClick = { errorCancelacion = null; confirmarCancelacion = true },
                    enabled = politica.permitida,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Cancelar evento",
                        color = if (politica.permitida) RojoError else TextoSecundario,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!politica.permitida) {
                    Spacer(Modifier.height(6.dp))
                    Text(politica.motivoBloqueo ?: "", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (aviso != null) {
                Spacer(Modifier.height(12.dp))
                Text(aviso!!, color = VerdeOk, style = MaterialTheme.typography.bodyMedium)
            }
            if (errorCancelacion != null && !confirmarCancelacion) {
                Spacer(Modifier.height(12.dp))
                Text(errorCancelacion!!, color = RojoError, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmarCancelacion && politica != null) {
        com.uv.enzona.ui.components.DialogoCancelarEvento(
            nombreEvento = evento.nombre,
            politica = politica,
            administrativa = false,
            procesando = procesandoCancelacion,
            error = errorCancelacion,
            onCancelar = { if (!procesandoCancelacion) { confirmarCancelacion = false; errorCancelacion = null } },
            onConfirmar = { motivo ->
                procesandoCancelacion = true
                MockRepository.cancelarEvento(
                    eventoId = eventoId,
                    ejecutadoPor = com.uv.enzona.data.SessionManager.usuarioId,
                    motivo = motivo,
                    aceptaComision = politica.cobraComision,
                ).onSuccess { r ->
                    procesandoCancelacion = false
                    confirmarCancelacion = false
                    aviso = "Evento cancelado. Se anularon ${r.boletosAnulados} boletos y se avisó a ${r.asistentesNotificados} asistentes." +
                        (if (r.comision.signum() > 0) " Comisión: ${com.uv.enzona.util.Formato.importeMxn(r.comision.toDouble())}." else "")
                }.onFailure {
                    procesandoCancelacion = false
                    errorCancelacion = it.message
                }
            },
        )
    }
}

/**
 * El organizador designa personal de acceso (documento de visión, sección 6).
 * Concede el rol VALIDADOR a una cuenta ya registrada — es decir, agrega la
 * fila correspondiente en `usuario_rol`.
 */
@Composable
private fun PersonalDeAcceso() {
    var correo by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }
    val validadores = MockRepository.personalDeAcceso()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MoradoSuperficie)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.HowToReg, contentDescription = null, tint = NaranjaAcento)
            Text("  Personal de acceso", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Quien designes podrá escanear los códigos QR en la puerta desde su propia cuenta.",
            color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))
        CampoEnZona(
            valor = correo,
            onCambio = { correo = it; mensaje = null },
            etiqueta = "Correo de la persona",
            teclado = KeyboardType.Email
        )
        Spacer(Modifier.height(10.dp))
        BotonEnZona(
            texto = "Designar como validador",
            habilitado = correo.isNotBlank(),
            onClick = {
                MockRepository.designarValidador(correo)
                    .onSuccess {
                        mensaje = "${it.nombre} ya puede validar accesos."
                        esError = false
                        correo = ""
                    }
                    .onFailure {
                        mensaje = it.message
                        esError = true
                    }
            }
        )

        if (mensaje != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                mensaje!!,
                color = if (esError) RojoError else VerdeOk,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (validadores.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Designados", color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
            validadores.forEach { persona ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(persona.nombre, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
                        Text(persona.correo, color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { MockRepository.quitarValidador(persona.id) }) {
                        Text("Quitar", color = RojoError, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tarjeta(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MoradoSuperficie)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(valor, color = NaranjaAcento, style = MaterialTheme.typography.titleLarge)
        Text(etiqueta, color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Barra(titulo: String, detalle: String, progreso: Float, color: androidx.compose.ui.graphics.Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(titulo, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
            Text(detalle, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progreso.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MoradoClaro,
        )
    }
}
