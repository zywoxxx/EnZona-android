package com.uv.enzona.ui.screens.validator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.ResultadoValidacion
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.MoradoClaro
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk

/**
 * Pantalla del personal de acceso (RF-15).
 *
 * Escanea el QR, verifica la firma HMAC (RNF-02), marca el boleto como usado
 * (un solo uso) y registra la validación en la bitácora. Con el modo sin
 * conexión activo, las validaciones quedan pendientes de sincronizar (RNF-08).
 */
@Composable
fun ScannerScreen() {
    val contexto = LocalContext.current
    val validadorId = SessionManager.usuarioId

    var permisoConcedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val solicitarPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> permisoConcedido = concedido }

    var resultado by remember { mutableStateOf<ResultadoValidacion?>(null) }
    var modoManual by remember { mutableStateOf(false) }
    var codigoManual by remember { mutableStateOf("") }
    val offline = MockRepository.modoOffline.value
    val pendientes = MockRepository.pendientesDeSincronizar()

    fun validar(contenido: String) {
        if (resultado != null) return   // ya hay un resultado en pantalla
        resultado = MockRepository.validarQr(contenido, validadorId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoradoFondo)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Validar acceso", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Text("Escanea el código QR del asistente", color = TextoSecundario)

        Spacer(Modifier.height(14.dp))

        // Modo sin conexión + sincronización (RNF-08, CP-14)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MoradoSuperficie)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CloudOff, contentDescription = null,
                        tint = if (offline) AmarilloAcento else TextoSecundario
                    )
                    Text("  Modo sin conexión", color = TextoPrincipal)
                }
                Switch(
                    checked = offline,
                    onCheckedChange = { MockRepository.modoOffline.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AmarilloAcento,
                        uncheckedTrackColor = MoradoClaro,
                    )
                )
            }
            if (pendientes > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "$pendientes validación(es) pendientes de sincronizar",
                    color = AmarilloAcento, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { MockRepository.sincronizarValidaciones() },
                    enabled = !offline,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Cached, contentDescription = null, tint = NaranjaAcento)
                    Text("  Sincronizar ahora", color = TextoPrincipal)
                }
                if (offline) {
                    Text(
                        "Desactiva el modo sin conexión para sincronizar.",
                        color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            // ---- Resultado de la última lectura ----
            resultado != null -> TarjetaResultado(resultado!!) {
                resultado = null
                codigoManual = ""
            }

            // ---- Entrada manual del código ----
            modoManual -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoradoSuperficie)
                        .padding(16.dp)
                ) {
                    Text("Captura manual", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
                    Text(
                        "Útil cuando la cámara falla o al probar en el emulador. Copia el contenido del QR del boleto.",
                        color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    CampoEnZona(
                        valor = codigoManual,
                        onCambio = { codigoManual = it },
                        etiqueta = "Contenido del QR (EZ-0000-0000-0000.firma)"
                    )
                    Spacer(Modifier.height(12.dp))
                    BotonEnZona(
                        texto = "Validar código",
                        habilitado = codigoManual.isNotBlank(),
                        onClick = { validar(codigoManual.trim()) }
                    )
                    TextButton(onClick = { modoManual = false }) {
                        Text("Volver a la cámara", color = TextoSecundario)
                    }
                }
            }

            // ---- Cámara ----
            permisoConcedido -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.85f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                ) {
                    CamaraQr(
                        modifier = Modifier.fillMaxSize(),
                        activo = resultado == null,
                        onCodigo = { validar(it) }
                    )
                    // Marco guía
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                            .border(3.dp, NaranjaAcento, RoundedCornerShape(18.dp))
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Apunta al código QR del boleto",
                    color = TextoSecundario, modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { modoManual = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Keyboard, contentDescription = null, tint = TextoSecundario)
                    Text("  Capturar código manualmente", color = TextoSecundario)
                }
            }

            // ---- Sin permiso de cámara ----
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoradoSuperficie)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CameraAlt, contentDescription = null,
                        tint = TextoSecundario, modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Se necesita la cámara", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Concede el permiso para escanear los boletos en la entrada.",
                        color = TextoSecundario, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    BotonEnZona(
                        texto = "Permitir cámara",
                        onClick = { solicitarPermiso.launch(Manifest.permission.CAMERA) }
                    )
                    TextButton(onClick = { modoManual = true }) {
                        Text("Capturar código manualmente", color = TextoSecundario)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        BitacoraReciente()
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun TarjetaResultado(resultado: ResultadoValidacion, onContinuar: () -> Unit) {
    val color = if (resultado.permitido) VerdeOk else RojoError
    val fondo = if (resultado.permitido) com.uv.enzona.ui.theme.VerdeContenedor else com.uv.enzona.ui.theme.RojoContenedor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(fondo)
            .border(2.dp, color, RoundedCornerShape(20.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (resultado.permitido) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null, tint = color, modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            resultado.titulo,
            color = color, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            resultado.detalle, color = TextoPrincipal,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
        )
        resultado.asiento?.let { asiento ->
            Spacer(Modifier.height(10.dp))
            Text(
                asiento,
                color = TextoPrincipal,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        resultado.codigo?.let { codigo ->
            Spacer(Modifier.height(10.dp))
            Text(
                codigo,
                color = TextoSecundario,
                modifier = Modifier
                    .background(MoradoClaro, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(18.dp))
        BotonEnZona(texto = "Escanear siguiente", onClick = onContinuar)
    }
}

@Composable
private fun BitacoraReciente() {
    val registros = MockRepository.validaciones.takeLast(6).reversed()
    if (registros.isEmpty()) return

    Text("Últimos accesos", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
    Spacer(Modifier.height(8.dp))
    registros.forEach { registro ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (registro.resultado.name == "PERMITIDO") VerdeOk else RojoError)
            )
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(registro.codigoBoleto, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${registro.motivo} · ${registro.fechaHora}",
                    color = TextoSecundario, style = MaterialTheme.typography.labelMedium
                )
            }
            if (!registro.sincronizado) {
                Text("sin sincronizar", color = AmarilloAcento, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
