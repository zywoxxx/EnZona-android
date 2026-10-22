package com.uv.enzona.ui.screens.validator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.ResumenSincronizacion
import com.uv.enzona.data.model.ResultadoAcceso
import com.uv.enzona.data.model.ResultadoValidacion
import com.uv.enzona.data.model.ValidacionAcceso
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.components.Carga
import com.uv.enzona.ui.components.IndicadorCarga
import com.uv.enzona.ui.components.SelectorDesplegable
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.components.rememberOperacion
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.ErrorRojo
import com.uv.enzona.ui.theme.Exito
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import com.uv.enzona.util.FechaEvento
import kotlinx.coroutines.delay

/**
 * Pantalla del personal de acceso (RF-15), pensada para la puerta de un evento:
 * la cámara ocupa casi toda la pantalla, el resultado se ve de lejos (toda la
 * zona en verde o rojo, con vibración) y lo demás queda en una fila de controles
 * cortos: evento, conexión, envíos pendientes y contadores de la jornada.
 *
 * Con conexión, el servidor verifica la firma HMAC (RNF-02), marca el boleto
 * como usado (un solo uso) y registra la validación en la bitácora.
 *
 * Modo sin conexión (RNF-08, CP-14): al activarlo se descarga el manifiesto del
 * evento (huellas SHA-256 de los boletos). Cada escaneo se comprueba por huella y
 * se guarda como pendiente; «Enviar» los manda al servidor cuando vuelve la red.
 */
@Composable
fun ScannerScreen() {
    val contexto = LocalContext.current
    val repositorio = Repositorio.actual
    val haptico = LocalHapticFeedback.current

    // Eventos en los que esta cuenta está designada como personal de acceso
    val eventos = rememberCarga { repositorio.eventosParaValidar() }
    var eventoElegido by rememberSaveable { mutableStateOf<Long?>(null) }
    val lista = eventos.valor.orEmpty()
    val evento = lista.firstOrNull { it.id == eventoElegido } ?: lista.firstOrNull()
    val operacion = rememberOperacion()
    val bitacora = rememberCarga(evento?.id) {
        evento?.let { e -> runCatching { repositorio.ultimasValidaciones(e.id) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    var permisoConcedido by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val solicitarPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permisoConcedido = it }

    var resultado by remember { mutableStateOf<ResultadoValidacion?>(null) }
    var capturaManual by remember { mutableStateOf(false) }
    var linterna by rememberSaveable { mutableStateOf(false) }
    var verBitacora by rememberSaveable { mutableStateOf(false) }
    var offline by rememberSaveable { mutableStateOf(false) }
    var avisoModo by remember { mutableStateOf<String?>(null) }
    var errorModo by remember { mutableStateOf<String?>(null) }
    // Se vuelve a contar después de cada escaneo o sincronización
    val pendientes = remember(Repositorio.cambios, resultado) { repositorio.pendientesDeSincronizar() }

    fun validar(contenido: String) {
        val actual = evento ?: return
        if (resultado != null || operacion.enCurso) return   // ya hay un resultado en pantalla o una validación en curso
        errorModo = null
        operacion.ejecutar(
            alTerminar = { r: ResultadoValidacion ->
                resultado = r
                haptico.performHapticFeedback(HapticFeedbackType.LongPress)   // se siente aunque no se mire la pantalla
            },
            alFallar = { fallo ->
                errorModo = when {
                    // 403: la cuenta no está designada en este evento (la API lista todos los publicados)
                    (fallo as? ErrorEnZona)?.estado == 403 ->
                        "No estás designado para este evento. Pide al organizador que te agregue como personal de acceso o elige otro evento."
                    else -> (fallo.message ?: "No se pudo validar el boleto.").trimEnd('.') + "." +
                        if (!offline) " Si no hay red en el acceso, activa «Sin conexión»." else ""
                }
            },
        ) {
            if (offline) repositorio.validarSinConexion(contenido, actual.id) else repositorio.validarQr(contenido, actual.id)
        }
    }

    fun cambiarConexion(sinRed: Boolean) {
        val actual = evento ?: return
        errorModo = null
        if (!sinRed) {
            offline = false
            avisoModo = null
            return
        }
        // El manifiesto se descarga con conexión, antes de quedarse sin red
        operacion.ejecutar(
            alTerminar = { vigentes: Int ->
                offline = true
                avisoModo = "Listo: $vigentes boletos guardados en el teléfono. Puedes seguir sin internet."
            },
            alFallar = { errorModo = it.message },
        ) { repositorio.prepararModoSinConexion(actual.id) }
    }

    fun sincronizar() {
        errorModo = null
        operacion.ejecutar(
            alTerminar = { r: ResumenSincronizacion ->
                avisoModo = "Enviadas ${r.enviadas}: ${r.permitidas} entraron y ${r.rechazadas} rechazadas" +
                    (if (r.duplicadas > 0) " (${r.duplicadas} ya habían entrado)." else ".")
            },
            alFallar = { errorModo = it.message },
        ) { repositorio.sincronizarValidaciones() }
    }

    // Un acceso permitido se cierra solo para no frenar la fila; un rechazo espera a que lo vean
    LaunchedEffect(resultado) {
        val r = resultado ?: return@LaunchedEffect
        if (r.permitido) {
            delay(SEGUNDOS_RESULTADO_PERMITIDO * 1000L)
            if (resultado === r) resultado = null
        }
    }

    val registros = bitacora.valor.orEmpty()
    val entraron = registros.count { it.resultado == ResultadoAcceso.PERMITIDO }
    val rechazados = registros.size - entraron

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoradoFondo)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // ---- Evento en el que se valida ----
        when (val estado = eventos.estado) {
            is Carga.Cargando -> IndicadorCarga("Cargando tus eventos…", Modifier.padding(vertical = 12.dp))
            is Carga.Fallo -> {
                Text(estado.mensaje, color = RojoError, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = eventos::recargar) { Text("Reintentar", color = NaranjaAcento) }
            }
            is Carga.Lista -> if (lista.isEmpty()) {
                Text("Validar acceso", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
                Text(
                    "Todavía no tienes eventos asignados. El organizador debe designarte como personal de acceso de su evento.",
                    color = TextoSecundario, style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                SelectorDesplegable(
                    etiqueta = "Evento",
                    opciones = lista.map { "${it.nombre} · ${FechaEvento.corto(it.fecha)}" },
                    seleccion = evento?.let { "${it.nombre} · ${FechaEvento.corto(it.fecha)}" },
                    onSeleccion = { texto ->
                        lista.firstOrNull { "${it.nombre} · ${FechaEvento.corto(it.fecha)}" == texto }?.let { elegido ->
                            eventoElegido = elegido.id
                            offline = false          // el manifiesto es por evento: hay que descargarlo de nuevo
                            avisoModo = null
                            resultado = null
                        }
                    },
                    habilitado = !operacion.enCurso,
                )
            }
        }
        if (evento == null) return@Column

        Spacer(Modifier.height(8.dp))

        // ---- Fila de estado: conexión, envíos pendientes y contadores de la jornada ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = offline,
                enabled = !operacion.enCurso,
                onClick = { cambiarConexion(!offline) },
                label = { Text(if (offline) "Sin conexión" else "Con internet") },
                leadingIcon = { Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmarilloAcento, selectedLabelColor = TextoPrincipal, selectedLeadingIconColor = TextoPrincipal),
            )
            if (pendientes > 0) {
                AssistChip(
                    enabled = !offline && !operacion.enCurso,
                    onClick = { sincronizar() },
                    label = { Text("Enviar $pendientes") },
                    leadingIcon = { Icon(Icons.Filled.Cached, contentDescription = null, modifier = Modifier.size(18.dp), tint = NaranjaAcento) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = TextoPrincipal),
                )
            }
            Spacer(Modifier.weight(1f))
            Contador(entraron, "entraron", VerdeOk)
            Contador(rechazados, "rechazados", RojoError)
        }
        avisoModo?.let { Text(it, color = VerdeOk, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        errorModo?.let { Text(it, color = RojoError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }

        Spacer(Modifier.height(10.dp))

        // ---- Zona principal: cámara o resultado, lo más grande posible ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            val actual = resultado
            when {
                actual != null -> PanelResultado(actual) { resultado = null }
                operacion.enCurso -> Box(Modifier.fillMaxSize().background(MoradoSuperficie), contentAlignment = Alignment.Center) {
                    IndicadorCarga("Revisando el boleto…")
                }
                permisoConcedido -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                    CamaraQr(
                        modifier = Modifier.fillMaxSize(),
                        activo = true,
                        linterna = linterna,
                        onCodigo = { validar(it) },
                    )
                    // Marco guía
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.68f)
                            .aspectRatio(1f)
                            .border(3.dp, NaranjaAcento, RoundedCornerShape(18.dp))
                    )
                    FilledIconButton(
                        onClick = { linterna = !linterna },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .semantics { contentDescription = if (linterna) "Apagar linterna" else "Encender linterna" },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x66000000), contentColor = Color.White),
                    ) { Icon(if (linterna) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, contentDescription = null) }
                    Text(
                        "Apunta al código del boleto",
                        color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp)
                            .background(Color(0x66000000), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                else -> SinCamara { solicitarPermiso.launch(Manifest.permission.CAMERA) }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ---- Acciones secundarias, cortas ----
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { capturaManual = true }, enabled = resultado == null && !operacion.enCurso) {
                Icon(Icons.Filled.Keyboard, contentDescription = null, tint = TextoSecundario)
                Text("  Escribir código", color = TextoSecundario)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { verBitacora = !verBitacora }) {
                Text("Últimos accesos", color = TextoSecundario)
                Icon(if (verBitacora) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = TextoSecundario)
            }
        }
        if (verBitacora) {
            BitacoraReciente(registros.take(5))
            Spacer(Modifier.height(8.dp))
        }
    }

    if (capturaManual) {
        DialogoCapturaManual(
            onCerrar = { capturaManual = false },
            onValidar = { codigo -> capturaManual = false; validar(codigo) },
        )
    }
}

private const val SEGUNDOS_RESULTADO_PERMITIDO = 3

@Composable
private fun Contador(valor: Int, etiqueta: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 6.dp)) {
        Text("$valor", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(etiqueta, color = TextoSecundario, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Resultado a toda la zona de la cámara: verde «PUEDE PASAR» o rojo «NO PUEDE
 * PASAR», legible a un metro. Tocar en cualquier parte vuelve a la cámara.
 */
@Composable
private fun PanelResultado(resultado: ResultadoValidacion, onContinuar: () -> Unit) {
    val fondo = if (resultado.permitido) Exito else ErrorRojo
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo)
            .clickable(onClick = onContinuar)
            .padding(24.dp)
            .semantics { contentDescription = (if (resultado.permitido) "Puede pasar. " else "No puede pasar. ") + resultado.titulo },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (resultado.permitido) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null, tint = Color.White, modifier = Modifier.size(110.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (resultado.permitido) "PUEDE PASAR" else "NO PUEDE PASAR",
            color = Color.White, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(resultado.titulo, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        // En un acceso permitido el detalle repite el título y el asiento: solo se muestra cuando aporta algo
        if (!resultado.permitido || resultado.asiento == null) {
            Text(resultado.detalle, color = Color(0xE6FFFFFF), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
        resultado.asiento?.let { asiento ->
            Spacer(Modifier.height(14.dp))
            Text("Asiento", color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelLarge)
            Text(asiento, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        }
        resultado.codigo?.let { codigo ->
            Spacer(Modifier.height(10.dp))
            Text(
                codigo, color = Color.White,
                modifier = Modifier.background(Color(0x33000000), CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onContinuar,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = fondo),
            modifier = Modifier.fillMaxWidth(0.8f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Siguiente", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        Text(
            if (resultado.permitido) "Vuelve a la cámara en unos segundos" else "Toca para seguir con la siguiente persona",
            color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SinCamara(onPedirPermiso: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoradoSuperficie)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = TextoSecundario, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Se necesita la cámara", color = TextoPrincipal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(
            "Sirve solo para leer el código del boleto. También puedes escribir el código a mano.",
            color = TextoSecundario, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        BotonEnZona(texto = "Permitir cámara", onClick = onPedirPermiso)
    }
}

@Composable
private fun DialogoCapturaManual(onCerrar: () -> Unit, onValidar: (String) -> Unit) {
    var codigo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Escribir el código") },
        text = {
            Column {
                Text(
                    "Si la cámara no lee el boleto, copia aquí el código que aparece debajo del QR.",
                    color = TextoSecundario, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                CampoEnZona(valor = codigo, onCambio = { codigo = it }, etiqueta = "Código del boleto")
            }
        },
        confirmButton = { TextButton(onClick = { onValidar(codigo.trim()) }, enabled = codigo.isNotBlank()) { Text("Validar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun BitacoraReciente(registros: List<ValidacionAcceso>) {
    if (registros.isEmpty()) {
        Text("Todavía no hay accesos registrados.", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        registros.forEach { registro ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (registro.resultado == ResultadoAcceso.PERMITIDO) VerdeOk else RojoError)
                )
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(registro.codigoBoleto, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
                    Text("${registro.motivo} · ${registro.fechaHora}", color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
                }
                if (!registro.sincronizado) {
                    Spacer(Modifier.width(6.dp))
                    Text("por enviar", color = AmarilloAcento, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
