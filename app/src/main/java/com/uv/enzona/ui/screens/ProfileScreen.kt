package com.uv.enzona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.Rol
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.FilaAjuste
import com.uv.enzona.ui.components.Separador
import com.uv.enzona.ui.components.TituloSeccion
import com.uv.enzona.ui.theme.MoradoClaro
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import kotlinx.coroutines.launch

/** Mi cuenta: perfil y ajustes (RF-04). */
@Composable
fun ProfileScreen(
    onEditarPerfil: () -> Unit,
    onMetodosPago: () -> Unit,
    onSeguridad: () -> Unit,
    onAyuda: () -> Unit,
    onPrivacidad: () -> Unit,
    onLegal: () -> Unit,
    onCerrarSesion: () -> Unit,
    onRolCambiado: () -> Unit = {},
) {
    val usuario = SessionManager.usuario ?: return
    val tarjetas = MockRepository.metodosDe(usuario.id)

    // ---- Confirmaciones de rol (v3, cambio 2) ----
    var confirmarConvertirse by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var confirmarDejar by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    val motivoNoDejar = if (Rol.ORGANIZADOR in usuario.roles) MockRepository.motivoNoPuedeDejarOrganizador(usuario.id) else null

    if (confirmarConvertirse) {
        com.uv.enzona.ui.components.DialogoConvertirseOrganizador(
            onCancelar = { confirmarConvertirse = false },
            onConfirmar = {
                confirmarConvertirse = false
                MockRepository.activarRolOrganizador(usuario.id)
                SessionManager.refrescar()   // la barra inferior se rearma con el nuevo rol; seguimos en Perfil
                alcance.launch { snackbar.showSnackbar("Ahora eres organizador") }
            },
        )
    }
    if (confirmarDejar) {
        com.uv.enzona.ui.components.DialogoDejarOrganizador(
            motivoBloqueo = motivoNoDejar,
            onCancelar = { confirmarDejar = false },
            onConfirmar = {
                confirmarDejar = false
                val eraAccesoPersonal = SessionManager.accesoPersonal
                MockRepository.dejarRolOrganizador(usuario.id)
                    .onSuccess {
                        SessionManager.refrescar()
                        // Si el rol activo era el de personal, la sesión vuelve a asistente y a Inicio
                        if (eraAccesoPersonal) onRolCambiado()
                        else alcance.launch { snackbar.showSnackbar("Ya no eres organizador. Sigues siendo asistente.") }
                    }
                    .onFailure { alcance.launch { snackbar.showSnackbar(it.message ?: "No se pudo quitar el rol.") } }
            },
        )
    }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { rellenoScaffold ->

    Column(
        modifier = Modifier
            .padding(rellenoScaffold)
            .fillMaxSize()
            .background(MoradoFondo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Mi cuenta", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)

        // ---------------- Cabecera del usuario ----------------
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MoradoSuperficie)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(com.uv.enzona.ui.theme.AzulDegradadoInicio, com.uv.enzona.ui.theme.AzulDegradadoFin))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    usuario.nombre.take(1).uppercase(),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp
                )
            }
            Column(
                Modifier
                    .padding(start = 14.dp)
                    .weight(1f)
            ) {
                Text(usuario.nombre, color = TextoPrincipal, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Text(usuario.correo, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text("● ", color = if (usuario.correoVerificado) VerdeOk else RojoError,
                        style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (usuario.correoVerificado) "Identidad verificada" else "Cuenta sin verificar",
                        color = TextoSecundario, style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            usuario.roles.forEach { rol ->
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            rol.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MoradoClaro, labelColor = TextoPrincipal
                    )
                )
            }
        }

        // ---------------- Alta como organizador ----------------
        if (Rol.ORGANIZADOR !in usuario.roles) {
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MoradoSuperficie)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = NaranjaAcento)
                    Text("  ¿Quieres publicar tus propios eventos?", color = TextoPrincipal,
                        fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Como organizador puedes crear eventos gratuitos o de pago, definir el aforo y los asientos, " +
                        "y llevar el control de acceso. Sigues conservando tu cuenta de asistente.",
                    color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                BotonEnZona(
                    texto = "Convertirme en organizador",
                    destacado = true,
                    onClick = { confirmarConvertirse = true }   // el rol solo se activa al confirmar en el diálogo
                )
            }
        } else if (Rol.ADMIN !in usuario.roles) {
            // ---------------- Dejar de ser organizador (v3) ----------------
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MoradoSuperficie)
                    .padding(16.dp)
            ) {
                Text("Cuenta de organizador", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    motivoNoDejar ?: "Puedes dejar de ser organizador cuando quieras. Sigues siendo asistente y tu historial se conserva.",
                    color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                com.uv.enzona.ui.components.BotonSecundario(
                    texto = "Dejar de ser organizador",
                    habilitado = motivoNoDejar == null,
                    onClick = { confirmarDejar = true },
                )
            }
        }

        // ---------------- Ajustes de localización ----------------
        TituloSeccion("Ajustes de localización")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MoradoSuperficie)
        ) {
            FilaAjuste(
                icono = Icons.Filled.Place,
                texto = "Mi ubicación",
                valor = MockRepository.ciudad.value,
                mostrarFlecha = false,
            )
            Separador()
            FilaAjuste(
                icono = Icons.Filled.Public,
                texto = "Mi país",
                valor = MockRepository.pais.value,
                mostrarFlecha = false,
            )
            Separador()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, tint = TextoSecundario,
                    modifier = Modifier.size(22.dp))
                Column(
                    Modifier
                        .padding(start = 14.dp)
                        .weight(1f)
                ) {
                    Text("Contenido basado en mi ubicación", color = TextoPrincipal,
                        style = MaterialTheme.typography.bodyLarge)
                    Text("Muestra primero los eventos de tu zona", color = TextoSecundario,
                        style = MaterialTheme.typography.labelMedium)
                }
                Switch(
                    checked = MockRepository.contenidoPorUbicacion.value,
                    onCheckedChange = { MockRepository.contenidoPorUbicacion.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NaranjaAcento,
                        uncheckedTrackColor = MoradoClaro,
                    )
                )
            }
        }

        // ---------------- Preferencias ----------------
        TituloSeccion("Preferencias")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MoradoSuperficie)
        ) {
            FilaAjuste(
                icono = Icons.Filled.Person,
                texto = "Editar mis datos",
                descripcion = "Nombre y teléfono",
                onClick = onEditarPerfil,
            )
            Separador()
            FilaAjuste(
                icono = Icons.Filled.CreditCard,
                texto = "Métodos de pago guardados",
                descripcion = if (tarjetas.isEmpty()) "Ninguno guardado"
                else "${tarjetas.size} guardado(s)",
                onClick = onMetodosPago,
            )
            Separador()
            FilaAjuste(
                icono = Icons.Filled.Shield,
                texto = "Seguridad",
                descripcion = "Contraseña y verificación de la cuenta",
                onClick = onSeguridad,
            )
        }

        // ---------------- Ayuda ----------------
        TituloSeccion("Ayuda")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MoradoSuperficie)
        ) {
            FilaAjuste(
                icono = Icons.Filled.HelpOutline,
                texto = "¿Necesitas ayuda?",
                onClick = onAyuda,
            )
            Separador()
            FilaAjuste(
                icono = Icons.Filled.PrivacyTip,
                texto = "Privacidad",
                onClick = onPrivacidad,
            )
            Separador()
            FilaAjuste(
                icono = Icons.Filled.Description,
                texto = "Legal",
                onClick = onLegal,
            )
        }

        // ---------------- Cerrar sesión ----------------
        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MoradoSuperficie)
        ) {
            FilaAjuste(
                icono = Icons.Filled.Logout,
                texto = "Cerrar sesión",
                colorTexto = RojoError,
                colorIcono = RojoError,
                mostrarFlecha = false,
                onClick = {
                    SessionManager.cerrarSesion()
                    onCerrarSesion()
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "EnZona · versión 0.3.0 (piloto)",
            color = TextoSecundario, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(30.dp))
    }
    }
}
