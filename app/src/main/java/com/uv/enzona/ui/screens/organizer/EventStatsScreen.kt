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
import androidx.compose.foundation.layout.Box
import com.uv.enzona.data.Guias
import com.uv.enzona.ui.components.GuiaDePantalla
import com.uv.enzona.ui.components.PasoGuia
import com.uv.enzona.ui.components.objetivoDeGuia
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.uv.enzona.data.PersonalAcceso
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.CargoOrganizador
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.ui.components.EsperaDeCarga
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.components.rememberOperacion
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
    val carga = rememberCarga(eventoId) {
        val repositorio = Repositorio.actual
        val evento = repositorio.evento(eventoId)
        DatosPanel(
            evento = evento,
            boletos = repositorio.boletosDeEvento(eventoId),
            // Política de cancelación (RF-14): la calcula el servidor; la pantalla solo la muestra
            politica = runCatching { repositorio.politicaCancelacion(eventoId, administrativa = false) }.getOrNull(),
            cargos = if (repositorio.capacidades.cargosOrganizador) {
                runCatching { repositorio.cargosDeOrganizador() }.getOrDefault(emptyList()).filter { it.eventoId == eventoId }
            } else emptyList(),
            personal = runCatching { repositorio.personalDeAcceso(eventoId) }.getOrDefault(emptyList()),
        )
    }
    val datos = carga.valor
    if (datos == null) {
        EsperaDeCarga(carga, titulo = "Panel del evento", onVolver = onVolver, textoCargando = "Cargando el panel…")
        return
    }
    val evento = datos.evento
    val boletos = datos.boletos
    val vigentes = boletos.filter { it.estado == EstadoBoleto.VALIDO || it.estado == EstadoBoleto.USADO }
    val validados = boletos.count { it.estado == EstadoBoleto.USADO }
    // Ingresos: suma de lo que costó cada boleto vigente (el precio es el de su tipo)
    val ingresos = vigentes.sumOf { it.tipoBoleto?.precio ?: 0.0 }
    var confirmarCancelacion by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var errorCancelacion by remember { mutableStateOf<String?>(null) }
    val operacion = rememberOperacion()
    val procesandoCancelacion = operacion.enCurso
    val politica = datos.politica
    val cargos = datos.cargos
    val yaComenzo = !evento.fecha.isAfter(FechaEvento.ahora())

    val ocupacion = if (evento.aforo > 0) evento.vendidos.toFloat() / evento.aforo else 0f
    val asistencia = if (evento.vendidos > 0) validados.toFloat() / evento.vendidos else 0f

    val puedeCancelar = evento.estado != EstadoEvento.CANCELADO && evento.estado != EstadoEvento.REALIZADO && politica != null
    GuiaDePantalla(
        clave = Guias.Clave.PANEL_EVENTO,
        pasos = buildList {
            add(PasoGuia("panel_metricas", "Así va tu evento", "Cuántas personas tienen boleto, cuántas ya entraron y cuánto llevas vendido. Abajo ves qué tan lleno está."))
            add(PasoGuia("panel_boletos", "Quién tiene boleto", "Aquí aparece cada boleto vendido o apartado, con su estado: vigente, usado o cancelado."))
            add(PasoGuia("panel_personal", "¿Quién revisa en la puerta?", "Escribe el correo de la persona que va a escanear los boletos y desígnala como validador. Entrará a la app con su propia cuenta y solo verá este evento."))
            if (evento.estado != EstadoEvento.CANCELADO) add(PasoGuia("panel_editar", "¿Hay que cambiar algo?", "Con el lápiz editas el evento: descripción, portada, lugar… Si ya hay boletos vendidos y cambias la fecha, te pedimos confirmarlo."))
            if (puedeCancelar) add(PasoGuia("panel_cancelar", "Si tienes que cancelar", "Este botón cancela el evento y devuelve el dinero a los asistentes. En eventos de pago se cobra una pequeña comisión; te la mostramos antes de confirmar."))
        },
    )

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
                        IconButton(onClick = { onEditar(eventoId) }, modifier = Modifier.objetivoDeGuia("panel_editar")) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.objetivoDeGuia("panel_metricas")) {
                Tarjeta("Asistentes", "${evento.vendidos}", Modifier.weight(1f))
                Tarjeta("Validados", "$validados", Modifier.weight(1f))
                Tarjeta("Ingresos", "$${"%.0f".format(ingresos)}", Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            Barra("Ocupación del aforo", "${evento.vendidos} / ${evento.aforo}", ocupacion, NaranjaAcento)
            Spacer(Modifier.height(14.dp))
            Barra("Asistencia registrada en puerta", "$validados / ${evento.vendidos}", asistencia, VerdeOk)

            Spacer(Modifier.height(24.dp))
            Text("Boletos emitidos", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal, modifier = Modifier.objetivoDeGuia("panel_boletos"))
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
            Box(Modifier.objetivoDeGuia("panel_personal")) { PersonalDeAcceso(eventoId, datos.personal) }

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
                    enabled = !operacion.enCurso,
                    onClick = {
                        errorCancelacion = null
                        operacion.ejecutar(
                            alTerminar = { _: Evento -> aviso = "El evento ya es visible para los asistentes." },
                            alFallar = { aviso = null; errorCancelacion = it.message },
                        ) { Repositorio.actual.publicarEvento(eventoId) }
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

            // Con la API de EnZona el servidor marca solo los eventos terminados: no se ofrece el botón
            if (evento.estado == EstadoEvento.PUBLICADO && yaComenzo && Repositorio.actual.capacidades.marcarRealizado) {
                OutlinedButton(
                    enabled = !operacion.enCurso,
                    onClick = {
                        errorCancelacion = null
                        operacion.ejecutar(
                            alTerminar = { _: Evento -> aviso = "El evento quedó marcado como finalizado." },
                            alFallar = { aviso = null; errorCancelacion = it.message },
                        ) { Repositorio.actual.marcarRealizado(eventoId) }
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
                        .height(52.dp)
                        .objetivoDeGuia("panel_cancelar"),
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
                errorCancelacion = null
                operacion.ejecutar(
                    alTerminar = { r: com.uv.enzona.data.model.ResultadoCancelacion ->
                        confirmarCancelacion = false
                        aviso = "Evento cancelado. Se anularon ${r.boletosAnulados} boletos y se avisó a ${r.asistentesNotificados} asistentes." +
                            (if (r.comision.signum() > 0) " Comisión: ${com.uv.enzona.util.Formato.importeMxn(r.comision.toDouble())}." else "")
                    },
                    alFallar = { errorCancelacion = it.message },
                ) {
                    Repositorio.actual.cancelarEvento(eventoId, motivo = motivo, aceptaComision = politica.cobraComision, administrativa = false)
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
private fun PersonalDeAcceso(eventoId: Long, validadores: List<PersonalAcceso>) {
    var correo by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }
    val operacion = rememberOperacion()

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
            "Quien designes podrá escanear los códigos QR de este evento en la puerta, desde su propia cuenta.",
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
            cargando = operacion.enCurso,
            onClick = {
                val buscado = correo.trim()
                operacion.ejecutar(
                    alTerminar = { lista: List<PersonalAcceso> ->
                        val nombre = lista.firstOrNull { it.correo.equals(buscado, ignoreCase = true) }?.nombre ?: buscado
                        mensaje = "$nombre ya puede validar accesos."
                        esError = false
                        correo = ""
                    },
                    alFallar = {
                        mensaje = it.message
                        esError = true
                    },
                ) { Repositorio.actual.designarValidador(eventoId, buscado) }
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
                    TextButton(
                        enabled = !operacion.enCurso,
                        onClick = {
                            operacion.ejecutar<Any>(alFallar = { mensaje = it.message; esError = true }) {
                                Repositorio.actual.quitarValidador(eventoId, persona.usuarioId)
                            }
                        },
                    ) {
                        Text("Quitar", color = RojoError, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private data class DatosPanel(
    val evento: Evento,
    val boletos: List<BoletoDetalle>,
    val politica: PoliticaCancelacion?,
    val cargos: List<CargoOrganizador>,
    val personal: List<PersonalAcceso>,
)

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
