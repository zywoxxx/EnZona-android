package com.uv.enzona.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
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
fun RegisterScreen(onVolver: () -> Unit, onRegistrado: (Long) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var curp by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var intentado by remember { mutableStateOf(false) }

    // Validaciones (solo se muestran después del primer intento)
    val errNombre = if (intentado && nombre.isBlank()) "Escribe tu nombre completo." else null
    val errCorreo = when {
        !intentado -> null
        !Validaciones.correoValido(correo) -> "El correo no tiene un formato válido."
        MockRepository.correoRegistrado(correo.trim()) -> "Ese correo ya está registrado."
        else -> null
    }
    val errTelefono = if (intentado && !Validaciones.telefonoValido(telefono)) "El teléfono debe tener 10 dígitos." else null
    val errCurp = when {
        !intentado -> null
        !Validaciones.curpValido(curp) -> "CURP inválido: deben ser 18 caracteres con el formato oficial."
        MockRepository.curpRegistrado(curp.trim()) -> "Ese CURP ya está registrado en el sistema."
        else -> null
    }
    val errContrasena = if (intentado && !Validaciones.contrasenaValida(contrasena))
        "Mínimo 8 caracteres, con letras y números." else null
    val errConfirmar = if (intentado && contrasena != confirmar) "Las contraseñas no coinciden." else null

    val hayErrores = listOf(errNombre, errCorreo, errTelefono, errCurp, errContrasena, errConfirmar).any { it != null }

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
            CampoEnZona(correo, { correo = it }, "Correo electrónico", error = errCorreo, teclado = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            CampoEnZona(telefono, { telefono = it }, "Teléfono (opcional)", error = errTelefono, teclado = KeyboardType.Phone)
            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(24.dp))
            BotonEnZona(
                texto = "Crear cuenta",
                onClick = {
                    intentado = true
                    val ok = nombre.isNotBlank() &&
                        Validaciones.correoValido(correo) && !MockRepository.correoRegistrado(correo.trim()) &&
                        Validaciones.telefonoValido(telefono) &&
                        Validaciones.curpValido(curp) && !MockRepository.curpRegistrado(curp.trim()) &&
                        Validaciones.contrasenaValida(contrasena) && contrasena == confirmar
                    if (ok) {
                        val (usuario, _) = MockRepository.registrar(
                            nombre.trim(), correo.trim(), telefono.trim(), curp.trim(), contrasena
                        )
                        onRegistrado(usuario.id)
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            if (intentado && hayErrores) {
                Text("Revisa los campos marcados.", color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
