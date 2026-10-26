package com.uv.enzona.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.Repositorio
import com.uv.enzona.ui.components.rememberOperacion
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.util.Validaciones

/**
 * Registro de usuario (RF-01). Cubre los casos de prueba CP-01, CP-02 y CP-03:
 * datos válidos, CURP duplicado y CURP con formato inválido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onVolver: () -> Unit, onRegistrado: (Long) -> Unit, onVerPrivacidad: () -> Unit = {}) {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var curp by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    // Aviso de privacidad (LFPDPPP): la API exige la aceptación explícita en el registro
    var aceptaAviso by remember { mutableStateOf(false) }
    var intentado by remember { mutableStateOf(false) }
    // Duplicados que informó el servidor (CP-02): se muestran en el campo correspondiente
    var correoDuplicado by remember { mutableStateOf<String?>(null) }
    var curpDuplicada by remember { mutableStateOf<String?>(null) }
    var telefonoDuplicado by remember { mutableStateOf<String?>(null) }
    val operacion = rememberOperacion()
    // Sin proveedor de SMS el teléfono no se pide: exigiría un código que nunca llegaría
    val pideTelefono = Repositorio.actual.capacidades.verificacionPorTelefono

    // Validaciones (solo se muestran después del primer intento)
    val errNombre = if (intentado && nombre.isBlank()) "Escribe tu nombre completo." else null
    val errCorreo = when {
        !intentado -> null
        !Validaciones.correoValido(correo) -> "El correo no tiene un formato válido."
        correoDuplicado == correo.trim().lowercase() -> "Ese correo ya está registrado."
        else -> null
    }
    val errTelefono = when {
        !pideTelefono -> null
        intentado && !Validaciones.telefonoValido(telefono) -> "El teléfono debe tener 10 dígitos."
        intentado && telefono.isNotBlank() && telefonoDuplicado == telefono.trim() -> "Ese teléfono ya está registrado en otra cuenta."
        else -> null
    }
    val errCurp = when {
        !intentado -> null
        !Validaciones.curpValido(curp) -> "CURP inválido: deben ser 18 caracteres con el formato oficial."
        curpDuplicada == curp.trim().uppercase() -> "Ese CURP ya está registrado en el sistema."
        else -> null
    }
    val errContrasena = if (intentado && !Validaciones.contrasenaValida(contrasena))
        "Mínimo 8 caracteres, con letras y números." else null
    val errConfirmar = if (intentado && contrasena != confirmar) "Las contraseñas no coinciden." else null
    val errAviso = if (intentado && !aceptaAviso) "Debes aceptar el aviso de privacidad para crear la cuenta." else null

    val hayErrores = listOf(errNombre, errCorreo, errTelefono, errCurp, errContrasena, errConfirmar, errAviso).any { it != null }

    Scaffold(
        containerColor = MoradoFondo,
        topBar = {
            TopAppBar(
                title = { Text("Crear cuenta", color = TextoPrincipal) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextoPrincipal)
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
                .background(MoradoFondo)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Text(
                "Tu CURP se usa como dato de verificación de identidad y se almacena cifrado, conforme a la LFPDPPP.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(18.dp))

            CampoEnZona(nombre, { nombre = it }, "Nombre completo", error = errNombre)
            Spacer(Modifier.height(12.dp))
            CampoEnZona(
                correo, { correo = it }, "Correo electrónico",
                error = errCorreo,
                // Con datos de prueba no se envía correo: el código se muestra en la siguiente pantalla
                ayuda = if (errCorreo == null && !Repositorio.esDemostracion) "Te enviaremos un código de verificación a este correo." else null,
                teclado = KeyboardType.Email,
            )
            Spacer(Modifier.height(12.dp))
            if (pideTelefono) {
                CampoEnZona(telefono, { telefono = it }, "Teléfono (opcional)", error = errTelefono, teclado = KeyboardType.Phone)
                Spacer(Modifier.height(12.dp))
            }
            CampoEnZona(
                curp, { curp = it }, "CURP",
                error = errCurp,
                ayuda = if (errCurp == null) "18 caracteres. Ejemplo: ASIS020404MVZSNN09" else null,
                mayusculas = true
            )
            Spacer(Modifier.height(12.dp))
            CampoEnZona(contrasena, { contrasena = it }, "Contraseña", error = errContrasena, esContrasena = true)
            Spacer(Modifier.height(12.dp))
            CampoEnZona(confirmar, { confirmar = it }, "Confirmar contraseña", error = errConfirmar, esContrasena = true)

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = aceptaAviso,
                    onCheckedChange = { aceptaAviso = it },
                    colors = CheckboxDefaults.colors(checkedColor = com.uv.enzona.ui.theme.NaranjaAcento, uncheckedColor = TextoSecundario),
                )
                Text(
                    "He leído y acepto el aviso de privacidad.",
                    color = if (errAviso != null) com.uv.enzona.ui.theme.RojoError else TextoPrincipal,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onVerPrivacidad) { Text("Leer", color = com.uv.enzona.ui.theme.AzulProfundo) }
            }
            if (errAviso != null) {
                Text(errAviso, color = com.uv.enzona.ui.theme.RojoError, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            BotonEnZona(
                texto = "Crear cuenta",
                cargando = operacion.enCurso,
                onClick = {
                    intentado = true
                    val ok = nombre.isNotBlank() &&
                        Validaciones.correoValido(correo) &&
                        (!pideTelefono || Validaciones.telefonoValido(telefono)) &&
                        Validaciones.curpValido(curp) &&
                        Validaciones.contrasenaValida(contrasena) && contrasena == confirmar &&
                        aceptaAviso
                    if (ok) {
                        operacion.ejecutar(
                            alTerminar = { pendiente: com.uv.enzona.data.RegistroPendiente ->
                                CodigoSimulado.recordar(pendiente.usuarioId, pendiente.otpSimulado)
                                onRegistrado(pendiente.usuarioId)
                            },
                            alFallar = { fallo ->
                                when ((fallo as? ErrorEnZona)?.codigo) {
                                    "CORREO_DUPLICADO", "CORREO_REGISTRADO" -> correoDuplicado = correo.trim().lowercase()
                                    "CURP_DUPLICADA", "CURP_REGISTRADO" -> curpDuplicada = curp.trim().uppercase()
                                    "TELEFONO_DUPLICADO" -> telefonoDuplicado = telefono.trim()
                                    else -> operacion.error = fallo.message
                                }
                            },
                        ) {
                            Repositorio.actual.registrar(
                                nombre.trim(), correo.trim(), if (pideTelefono) telefono.trim() else "", curp.trim(), contrasena,
                                aceptaAvisoPrivacidad = aceptaAviso,
                            )
                        }
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            if (operacion.error != null) {
                Text(operacion.error!!, color = com.uv.enzona.ui.theme.RojoError, style = MaterialTheme.typography.bodyMedium)
            } else if (intentado && hayErrores) {
                Text("Revisa los campos marcados.", color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
