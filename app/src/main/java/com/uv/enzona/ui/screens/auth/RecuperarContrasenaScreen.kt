package com.uv.enzona.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.uv.enzona.data.Repositorio
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.components.rememberOperacion
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.AzulProfundo
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import kotlinx.coroutines.delay

/**
 * «¿Olvidaste tu contraseña?» en dos pasos sin iniciar sesión:
 * 1. Correo de la cuenta → la API envía un código de 6 dígitos (responde igual
 *    exista o no el correo, así que el aviso no confirma nada).
 * 2. Código + contraseña nueva (dos veces) → la API la cambia y se vuelve al acceso.
 *
 * El reenvío respeta los 60 s que la API exige entre códigos, igual que [OtpScreen].
 * Con los datos de prueba (sin servidor) el código se muestra en pantalla rotulado
 * como datos de prueba; con la API nunca viaja a la app.
 */
@Composable
fun RecuperarContrasenaScreen(onVolver: () -> Unit, onRestablecida: () -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var codigoPedido by rememberSaveable { mutableStateOf(false) }
    var codigo by rememberSaveable { mutableStateOf("") }
    var nueva by rememberSaveable { mutableStateOf("") }
    var confirmacion by rememberSaveable { mutableStateOf("") }
    var codigoSimulado by rememberSaveable { mutableStateOf("") }
    var segundosParaReenviar by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val operacion = rememberOperacion()

    LaunchedEffect(segundosParaReenviar) {
        if (segundosParaReenviar <= 0) return@LaunchedEffect
        delay(1_000)
        segundosParaReenviar -= 1
    }

    fun pedirCodigo() {
        error = RecuperacionContrasena.errorDeCorreo(correo)
        if (error != null) return
        operacion.ejecutar(
            alTerminar = { simulado: String? ->
                codigoSimulado = simulado.orEmpty()
                codigoPedido = true
                codigo = ""
                aviso = RecuperacionContrasena.AVISO_CODIGO_ENVIADO
                segundosParaReenviar = if (Repositorio.esDemostracion) 0 else ReenvioCodigo.ESPERA_SEGUNDOS
            },
            alFallar = { error = ReenvioCodigo.mensajeDeFallo(it) },
        ) { Repositorio.actual.solicitarRecuperacion(correo.trim()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoradoFondo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(70.dp))
        Text("Recupera tu contraseña", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Spacer(Modifier.height(8.dp))
        Text(
            if (codigoPedido) "Escribe el código que llegó a $correo y elige una contraseña nueva."
            else "Escribe el correo de tu cuenta y te enviamos un código para elegir una contraseña nueva.",
            color = TextoSecundario, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        if (!codigoPedido) {
            CampoEnZona(
                valor = correo,
                onCambio = { correo = it; error = null },
                etiqueta = "Correo",
                teclado = KeyboardType.Email,
                error = error,
                habilitado = !operacion.enCurso,
            )
            Spacer(Modifier.height(20.dp))
            BotonEnZona(
                texto = "Enviar código",
                habilitado = correo.isNotBlank(),
                cargando = operacion.enCurso,
                onClick = ::pedirCodigo,
            )
        } else {
            if (codigoSimulado.isNotBlank()) {
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
                    Text(codigoSimulado, color = AmarilloAcento, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            aviso?.let {
                Text(it, color = VerdeOk, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
            }
            CampoEnZona(
                valor = codigo,
                onCambio = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { codigo = it; error = null } },
                etiqueta = "Código de 6 dígitos",
                teclado = KeyboardType.Number,
                habilitado = !operacion.enCurso,
                ayuda = if (codigoSimulado.isBlank())
                    "Llega de EnZona con el asunto “Restablece tu contraseña de EnZona”." else null,
            )
            Spacer(Modifier.height(12.dp))
            CampoEnZona(
                valor = nueva,
                onCambio = { nueva = it; error = null },
                etiqueta = "Contraseña nueva",
                esContrasena = true,
                habilitado = !operacion.enCurso,
                ayuda = "Mínimo 8 caracteres, con letras y números.",
            )
            Spacer(Modifier.height(12.dp))
            CampoEnZona(
                valor = confirmacion,
                onCambio = { confirmacion = it; error = null },
                etiqueta = "Repite la contraseña nueva",
                esContrasena = true,
                habilitado = !operacion.enCurso,
                error = error,
            )
            Spacer(Modifier.height(20.dp))
            BotonEnZona(
                texto = "Cambiar contraseña",
                habilitado = RecuperacionContrasena.codigoCompleto(codigo) && nueva.isNotBlank() && confirmacion.isNotBlank(),
                cargando = operacion.enCurso,
                onClick = {
                    error = RecuperacionContrasena.errorDeContrasena(nueva, confirmacion)
                    if (error == null) {
                        operacion.ejecutar(
                            alTerminar = { _: Unit -> onRestablecida() },
                            alFallar = { error = RecuperacionContrasena.mensajeDeFallo(it) },
                        ) { Repositorio.actual.restablecerContrasena(correo.trim(), codigo, nueva) }
                    }
                },
            )
            TextButton(
                enabled = !operacion.enCurso && segundosParaReenviar == 0,
                onClick = ::pedirCodigo,
            ) {
                Text(ReenvioCodigo.textoReenvio(segundosParaReenviar), color = AzulProfundo)
            }
            if (error != null) {
                Text(
                    "La contraseña no cambia hasta que el código y la contraseña nueva sean válidos.",
                    color = RojoError, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onVolver, enabled = !operacion.enCurso) {
            Text("Volver al inicio de sesión", color = TextoSecundario)
        }
        Spacer(Modifier.height(24.dp))
    }
}
