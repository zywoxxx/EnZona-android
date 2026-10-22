package com.uv.enzona.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.CanalVerificacion
import com.uv.enzona.data.EstadoVerificacion
import com.uv.enzona.data.Repositorio
import com.uv.enzona.ui.components.rememberOperacion
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk

/**
 * Verificación de la cuenta con código de un solo uso (RF-02).
 * Casos de prueba CP-01 (OTP correcto) y CP-04 (OTP incorrecto).
 *
 * La API envía un código al correo y, si se registró un teléfono, otro al
 * teléfono: la cuenta se activa cuando ambos se confirman, así que la pantalla
 * pide el segundo código después del primero.
 *
 * SIMULACIÓN: con los datos de prueba (sin servidor) el código se muestra aquí
 * rotulado como simulación. Con la API el código nunca viaja a la app: en
 * desarrollo (`NOTIFICATIONS_MODE=log`) aparece en la consola del servidor.
 */
@Composable
fun OtpScreen(usuarioId: Long, onVerificado: () -> Unit) {
    var codigo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var canal by remember { mutableStateOf(CanalVerificacion.CORREO) }
    var otpVisible by remember { mutableStateOf(CodigoSimulado.de(usuarioId)) }
    val operacion = rememberOperacion()
    val destino = if (canal == CanalVerificacion.CORREO) "correo" else "teléfono"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoradoFondo)
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(90.dp))
        Text("Verifica tu cuenta", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enviamos un código de 6 dígitos a tu $destino. Ingrésalo para activar tu cuenta.",
            color = TextoSecundario, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Ayuda de demostración: solo cuando el envío del código es una simulación
        if (otpVisible.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MoradoSuperficie)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Simulación — no hay envío real de correo. Código generado:", color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(otpVisible, color = AmarilloAcento, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium)
            }
        }

        aviso?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = VerdeOk, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))
        CampoEnZona(
            valor = codigo,
            onCambio = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { codigo = it; error = null } },
            etiqueta = "Código de verificación",
            teclado = KeyboardType.Number,
            error = error
        )

        Spacer(Modifier.height(20.dp))
        BotonEnZona(
            texto = "Verificar",
            habilitado = codigo.length == 6,
            cargando = operacion.enCurso,
            onClick = {
                operacion.ejecutar(
                    alTerminar = { estado: EstadoVerificacion ->
                        if (estado.completa) {
                            CodigoSimulado.olvidar(usuarioId)
                            onVerificado()
                        } else {
                            // Falta el segundo canal (teléfono): se pide su código sin salir de la pantalla
                            canal = estado.siguienteCanal ?: CanalVerificacion.TELEFONO
                            codigo = ""
                            error = null
                            aviso = "Código del correo aceptado. Ahora escribe el código que enviamos a tu teléfono."
                        }
                    },
                    alFallar = { error = it.message ?: "El código es incorrecto. Intenta de nuevo o solicita otro." },
                ) { Repositorio.actual.verificarOtp(usuarioId, codigo) }
            }
        )
        TextButton(
            enabled = !operacion.enCurso,
            onClick = {
                operacion.ejecutar(
                    alTerminar = { nuevo: String? ->
                        otpVisible = nuevo.orEmpty()
                        CodigoSimulado.recordar(usuarioId, nuevo)
                        codigo = ""
                        error = null
                        aviso = "Enviamos un código nuevo a tu $destino."
                    },
                    alFallar = { error = it.message },
                ) { Repositorio.actual.reenviarOtp(usuarioId) }
            },
        ) {
            Text("Reenviar código", color = com.uv.enzona.ui.theme.AzulProfundo)
        }

        if (error != null) {
            Text(
                "La cuenta permanece sin activar hasta que el código sea correcto.",
                color = RojoError, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
