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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.uv.enzona.ui.theme.AzulProfundo
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import kotlinx.coroutines.delay

/** Aviso cuando la API exige un código por SMS y la fuente de datos no tiene cómo enviarlo. */
private const val AVISO_SIN_SMS =
    "Tu cuenta tiene un teléfono registrado y por ahora EnZona no envía SMS. " +
        "Escribe al equipo de EnZona (enzona.org@gmail.com) para activarla."

/**
 * Verificación de la cuenta con código de un solo uso (RF-02).
 * Casos de prueba CP-01 (OTP correcto) y CP-04 (OTP incorrecto).
 *
 * La API envía el código por correo (Resend). Si la fuente de datos admite
 * verificación por teléfono y se registró uno, pide un segundo código para ese
 * canal: la cuenta se activa cuando ambos se confirman. Si la API pide el
 * teléfono pero la fuente no ofrece SMS, la pantalla no pide ese código: avisa
 * cómo activar la cuenta y deshabilita Verificar y Reenviar.
 *
 * Reenvío: la API cuenta los 60 s entre códigos desde la última emisión (el
 * registro incluido) y descarta en silencio, con 202, los reenvíos tempranos.
 * Por eso la cuenta regresiva arranca ya al llegar desde el registro (desde
 * Inicio de sesión con cuenta pendiente llega usuarioId 0 y no hubo emisión
 * reciente), el código, el canal y la cuenta sobreviven a la rotación con
 * rememberSaveable, y el aviso de éxito advierte que el código anterior sigue
 * vigente por si el nuevo no llega.
 *
 * SIMULACIÓN: con los datos de prueba (sin servidor) el código se muestra aquí
 * rotulado como datos de prueba, no hay espera inicial y no se habla del correo.
 * Con la API el código nunca viaja a la app: se envía por correo con Resend; en
 * desarrollo puede verse en la consola del servidor con `NOTIFICATIONS_MODE=log`.
 */
@Composable
fun OtpScreen(usuarioId: Long, onVerificado: () -> Unit) {
    var codigo by rememberSaveable { mutableStateOf("") }
    // Fallo al verificar el código (la cuenta sigue sin activar)
    var error by remember { mutableStateOf<String?>(null) }
    // Fallo al pedir otro código: no significa que el código escrito sea incorrecto
    var errorReenvio by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var canal by rememberSaveable { mutableStateOf(CanalVerificacion.CORREO) }
    var otpVisible by remember { mutableStateOf(CodigoSimulado.de(usuarioId)) }
    // La API pide un código por SMS que la app no puede conseguir: la cuenta se activa fuera de la app
    var sinSms by rememberSaveable { mutableStateOf(false) }
    // Al venir del registro la API acaba de emitir un código: la espera corre desde el principio
    var segundosParaReenviar by rememberSaveable {
        mutableIntStateOf(if (usuarioId != 0L && !Repositorio.esDemostracion) ReenvioCodigo.ESPERA_SEGUNDOS else 0)
    }
    val operacion = rememberOperacion()
    val destino = if (canal == CanalVerificacion.CORREO) "correo" else "teléfono"

    // Cuenta regresiva del reenvío: se reinicia con cada segundo que baja hasta llegar a 0
    LaunchedEffect(segundosParaReenviar) {
        if (segundosParaReenviar <= 0) return@LaunchedEffect
        delay(1_000)
        segundosParaReenviar -= 1
    }

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
            if (sinSms) "Tu correo quedó verificado, pero falta confirmar el teléfono."
            else "Enviamos un código de 6 dígitos a tu $destino. Ingrésalo para activar tu cuenta.",
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
                Text("Datos de prueba — sin servidor no se envía correo. Código generado:", color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(otpVisible, color = AmarilloAcento, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium)
            }
        }

        if (sinSms) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MoradoSuperficie)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(AVISO_SIN_SMS, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center)
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
            error = error,
            habilitado = !sinSms,
            // Con datos de prueba el código se ve arriba: no hay correo que buscar
            ayuda = if (canal == CanalVerificacion.CORREO && otpVisible.isBlank() && !sinSms)
                "Si no lo ves, revisa la carpeta de no deseados: llega de EnZona con el asunto “Tu código de verificación de EnZona”."
            else null,
        )

        Spacer(Modifier.height(20.dp))
        BotonEnZona(
            texto = "Verificar",
            habilitado = codigo.length == 6 && !sinSms,
            cargando = operacion.enCurso,
            onClick = {
                errorReenvio = null
                operacion.ejecutar(
                    alTerminar = { estado: EstadoVerificacion ->
                        if (estado.completa) {
                            CodigoSimulado.olvidar(usuarioId)
                            onVerificado()
                        } else {
                            // Falta el segundo canal (teléfono)
                            val siguiente = estado.siguienteCanal ?: CanalVerificacion.TELEFONO
                            codigo = ""
                            error = null
                            if (siguiente == CanalVerificacion.TELEFONO && !Repositorio.actual.capacidades.verificacionPorTelefono) {
                                // Sin proveedor de SMS ese código nunca llegará: no se pide
                                sinSms = true
                                aviso = null
                            } else {
                                // Se pide el código del otro canal sin salir de la pantalla
                                canal = siguiente
                                segundosParaReenviar = 0
                                aviso = "Código del correo aceptado. Ahora escribe el código que enviamos a tu teléfono."
                            }
                        }
                    },
                    alFallar = { error = it.message ?: "El código es incorrecto. Intenta de nuevo o solicita otro." },
                ) { Repositorio.actual.verificarOtp(usuarioId, codigo) }
            }
        )
        TextButton(
            enabled = !operacion.enCurso && segundosParaReenviar == 0 && !sinSms,
            onClick = {
                errorReenvio = null
                operacion.ejecutar(
                    alTerminar = { nuevo: String? ->
                        otpVisible = nuevo.orEmpty()
                        CodigoSimulado.recordar(usuarioId, nuevo)
                        codigo = ""
                        error = null
                        // La API descarta en silencio el reenvío si no pasaron 60 s: el anterior sigue valiendo
                        aviso = "Pedimos un código nuevo para tu $destino. Si no llega en un minuto, el anterior sigue vigente."
                        segundosParaReenviar = ReenvioCodigo.ESPERA_SEGUNDOS
                    },
                    alFallar = { errorReenvio = ReenvioCodigo.mensajeDeFallo(it) },
                ) { Repositorio.actual.reenviarOtp(usuarioId) }
            },
        ) {
            Text(ReenvioCodigo.textoReenvio(segundosParaReenviar), color = AzulProfundo)
        }

        errorReenvio?.let {
            Text(it, color = RojoError, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
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
