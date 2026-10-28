package com.uv.enzona.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.SessionManager
import com.uv.enzona.ui.components.rememberOperacion
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.components.DialogoServidor
import com.uv.enzona.ui.components.gestoOculto
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario

/**
 * Inicio de sesión (RF-03).
 *
 * `modoPersonal = true` es el acceso especial para organizadores, validadores
 * y administradores: mismo mecanismo de autenticación, pero pantalla y
 * mensajes diferenciados, y solo admite cuentas con rol de personal.
 */
@Composable
fun LoginScreen(
    modoPersonal: Boolean,
    onAutenticado: () -> Unit,
    onIrARegistro: () -> Unit,
    onCambiarModo: () -> Unit,
    onVerificarCuenta: (Long) -> Unit = {},
) {
    var identificador by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Cuenta registrada pero sin verificar: se ofrece ir a escribir el código
    var cuentaSinVerificar by remember { mutableStateOf<Long?>(null) }
    val operacion = rememberOperacion()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoradoFondo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))

        // Marca. Siete toques seguidos abren la opción oculta de desarrollo «Servidor».
        var mostrarServidor by remember { mutableStateOf(false) }
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.uv.enzona.R.drawable.enzona_logo),
            contentDescription = "EnZona",
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(76.dp)
                .gestoOculto { mostrarServidor = true },
        )
        if (mostrarServidor) DialogoServidor(onCerrar = { mostrarServidor = false })

        Spacer(Modifier.height(18.dp))
        if (modoPersonal) {
            Text(
                "Acceso del personal",
                style = MaterialTheme.typography.headlineMedium,
                color = TextoPrincipal
            )
        }
        Text(
            if (modoPersonal) "Organizadores, validadores y administración"
            else "Descubre y accede a los eventos de tu zona",
            color = TextoSecundario,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(30.dp))

        CampoEnZona(
            valor = identificador,
            onCambio = { identificador = it; error = null; cuentaSinVerificar = null },
            etiqueta = "Correo, teléfono o CURP",
            habilitado = !operacion.enCurso,
        )
        Spacer(Modifier.height(12.dp))
        CampoEnZona(
            valor = contrasena,
            onCambio = { contrasena = it; error = null },
            etiqueta = "Contraseña",
            esContrasena = true,
            habilitado = !operacion.enCurso,
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = RojoError, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        } else if (SessionManager.expirada) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Tu sesión expiró. Inicia sesión de nuevo para continuar.",
                color = TextoSecundario, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
        }
        cuentaSinVerificar?.let { id ->
            TextButton(onClick = { onVerificarCuenta(id) }, enabled = !operacion.enCurso) {
                Text("Escribir el código de verificación", color = com.uv.enzona.ui.theme.AzulProfundo)
            }
        }

        Spacer(Modifier.height(22.dp))
        BotonEnZona(
            texto = "Iniciar sesión",
            habilitado = identificador.isNotBlank() && contrasena.isNotBlank(),
            cargando = operacion.enCurso,
            onClick = {
                error = null
                cuentaSinVerificar = null
                operacion.ejecutar(
                    alTerminar = { mensaje: String? ->
                        if (mensaje != null) error = mensaje else onAutenticado()
                    },
                    alFallar = { fallo ->
                        error = fallo.message
                        if (fallo is ErrorEnZona && fallo.codigo == "CUENTA_NO_VERIFICADA") {
                            cuentaSinVerificar = (fallo.propiedades["usuarioId"] as? Number)?.toLong()
                        }
                    },
                ) {
                    val repositorio = Repositorio.actual
                    val usuario = repositorio.iniciarSesion(identificador.trim(), contrasena, modoPersonal)
                    // El acceso del personal está separado del de asistentes (Anexo E, E.10)
                    val rechazo = when {
                        modoPersonal && !usuario.esPersonal -> "Esta cuenta no tiene permisos de personal. Usa el acceso normal."
                        !modoPersonal && usuario.esPersonal && usuario.rolPrincipal.name == "ADMIN" ->
                            "Las cuentas de administración entran por el acceso del personal."
                        else -> null
                    }
                    if (rechazo != null) repositorio.cerrarSesion()
                    else SessionManager.iniciarSesion(usuario, comoPersonal = modoPersonal)
                    rechazo
                }
            }
        )

        Spacer(Modifier.height(6.dp))

        if (!modoPersonal) {
            TextButton(onClick = onIrARegistro) {
                Text("¿No tienes cuenta? Regístrate", color = com.uv.enzona.ui.theme.AzulProfundo)
            }
        }
        TextButton(onClick = onCambiarModo) {
            Text(
                if (modoPersonal) "Volver al acceso de asistentes" else "Acceso para organizadores y personal",
                color = TextoSecundario
            )
        }

        Spacer(Modifier.height(20.dp))

        // Cuentas de demostración: solo existen en los datos de prueba en memoria, no en la API
        if (Repositorio.esDemostracion) Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MoradoSuperficie)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("Cuentas de prueba · contraseña: enzona123",
                color = TextoPrincipal, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            if (modoPersonal) {
                Text("organizador@uv.mx — crea y gestiona eventos", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                Text("validador@uv.mx — escanea boletos en la puerta", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                Text("admin@uv.mx — gestiona usuarios y eventos", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("asistente@uv.mx — compra boletos y muestra su QR", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
