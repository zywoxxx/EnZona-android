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
import com.uv.enzona.data.MockRepository
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario

/**
 * Verificación de la cuenta con código de un solo uso (RF-02).
 * Casos de prueba CP-01 (OTP correcto) y CP-04 (OTP incorrecto).
 *
 * En producción el código llega por correo o SMS; aquí se muestra en pantalla
 * porque no hay servicio de mensajería en la demo.
 */
@Composable
fun OtpScreen(usuarioId: Long, onVerificado: () -> Unit) {
    var codigo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var otpVisible by remember { mutableStateOf(MockRepository.otpDe(usuarioId) ?: "") }

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
            "Enviamos un código de 6 dígitos a tu correo. Ingrésalo para activar tu cuenta.",
            color = TextoSecundario, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Ayuda de demostración
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MoradoSuperficie)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Modo demostración — código generado:", color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium)
            Text(otpVisible, color = AmarilloAcento, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium)
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
            onClick = {
                if (MockRepository.verificarOtp(usuarioId, codigo)) {
                    onVerificado()
                } else {
                    error = "El código es incorrecto. Intenta de nuevo o solicita otro."
                }
            }
        )
        TextButton(onClick = {
            otpVisible = MockRepository.reenviarOtp(usuarioId)
            codigo = ""
            error = null
        }) {
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
