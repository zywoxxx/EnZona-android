package com.uv.enzona.ui.screens.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk

// ==================================================================
//  Armazón común
// ==================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaAjuste(
    titulo: String,
    onVolver: () -> Unit,
    contenido: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MoradoFondo,
        topBar = {
            TopAppBar(
                title = { Text(titulo, color = TextoPrincipal) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            contenido()
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MoradoSuperficie)
            .padding(16.dp)
    ) { contenido() }
}

@Composable
private fun Apartado(titulo: String, cuerpo: String) {
    Tarjeta {
        Text(titulo, color = TextoPrincipal, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(cuerpo, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
    }
}

// ==================================================================
//  Editar mis datos (RF-04)
// ==================================================================

@Composable
fun EditProfileScreen(onVolver: () -> Unit) {
    val usuario = SessionManager.usuario ?: return
    var nombre by remember(usuario.id) { mutableStateOf(usuario.nombre) }
    var telefono by remember(usuario.id) { mutableStateOf(usuario.telefono) }
    var guardado by remember { mutableStateOf(false) }

    PantallaAjuste("Editar mis datos", onVolver) {
        CampoEnZona(nombre, { nombre = it; guardado = false }, "Nombre completo")
        Spacer(Modifier.height(12.dp))
        CampoEnZona(telefono, { telefono = it; guardado = false }, "Teléfono", teclado = KeyboardType.Phone)
        Spacer(Modifier.height(16.dp))

        Tarjeta {
            Text("CURP", color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
            Text(usuario.curp, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Este dato se almacena cifrado y no puede modificarse desde la app. Se usa únicamente para " +
                    "verificar tu identidad y reducir el fraude de boletos.",
                color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(6.dp))
        BotonEnZona(
            texto = if (guardado) "Cambios guardados" else "Guardar cambios",
            habilitado = nombre.isNotBlank(),
            onClick = {
                MockRepository.actualizarPerfil(usuario.id, nombre.trim(), telefono.trim())
                SessionManager.refrescar()
                guardado = true
            }
        )
    }
}

// ==================================================================
//  Seguridad
// ==================================================================

@Composable
fun SecurityScreen(onVolver: () -> Unit) {
    val usuario = SessionManager.usuario ?: return
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }

    PantallaAjuste("Seguridad", onVolver) {
        Text("Estado de la cuenta", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
        Spacer(Modifier.height(10.dp))
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mail, contentDescription = null,
                    tint = if (usuario.correoVerificado) VerdeOk else RojoError,
                    modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Correo electrónico", color = TextoPrincipal,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (usuario.correoVerificado) "${usuario.correo} · verificado"
                        else "${usuario.correo} · sin verificar",
                        color = TextoSecundario, style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = TextoSecundario,
                    modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Teléfono", color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (usuario.telefono.isBlank()) "Sin registrar" else usuario.telefono,
                        color = TextoSecundario, style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VerdeOk,
                    modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Identidad verificada con CURP", color = TextoPrincipal,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Reduce el fraude y la duplicación de boletos", color = TextoSecundario,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Cambiar contraseña", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
        Spacer(Modifier.height(10.dp))
        CampoEnZona(actual, { actual = it; mensaje = null }, "Contraseña actual", esContrasena = true)
        Spacer(Modifier.height(10.dp))
        CampoEnZona(nueva, { nueva = it; mensaje = null }, "Nueva contraseña", esContrasena = true,
            ayuda = "Mínimo 8 caracteres, con letras y números")
        Spacer(Modifier.height(10.dp))
        CampoEnZona(confirmar, { confirmar = it; mensaje = null }, "Confirmar nueva contraseña",
            esContrasena = true)

        if (mensaje != null) {
            Spacer(Modifier.height(10.dp))
            Text(mensaje!!, color = if (esError) RojoError else VerdeOk,
                style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))
        BotonEnZona(
            texto = "Actualizar contraseña",
            habilitado = actual.isNotBlank() && nueva.isNotBlank() && confirmar.isNotBlank(),
            onClick = {
                if (nueva != confirmar) {
                    mensaje = "Las contraseñas nuevas no coinciden."; esError = true
                } else {
                    MockRepository.cambiarContrasena(usuario.id, actual, nueva)
                        .onSuccess {
                            mensaje = "Tu contraseña se actualizó correctamente."; esError = false
                            actual = ""; nueva = ""; confirmar = ""
                        }
                        .onFailure { mensaje = it.message; esError = true }
                }
            }
        )

        Spacer(Modifier.height(18.dp))
        Apartado(
            "Cómo protegemos tu cuenta",
            "Tu contraseña se guarda cifrada con un algoritmo de un solo sentido: ni el equipo de EnZona puede " +
                "leerla. Las sesiones usan un token firmado que caduca, y tu CURP se almacena cifrado y nunca " +
                "se muestra a terceros."
        )
    }
}

// ==================================================================
//  ¿Necesitas ayuda?
// ==================================================================

@Composable
fun HelpScreen(onVolver: () -> Unit) {
    PantallaAjuste("¿Necesitas ayuda?", onVolver) {
        Text("Preguntas frecuentes", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
        Spacer(Modifier.height(10.dp))

        Apartado(
            "No me llegó el boleto por correo",
            "Tu boleto siempre está disponible en la pestaña «Mis boletos», aunque el correo no haya llegado. " +
                "Desde ahí puedes volver a enviártelo. Revisa también la carpeta de correo no deseado."
        )
        Apartado(
            "¿Puedo entrar con una captura de pantalla del QR?",
            "Sí. Lo que se valida es el código, no la pantalla donde se muestra. Eso sí: el boleto es de un " +
                "solo uso, así que si alguien más lo escanea antes que tú, ya no podrás entrar con él. No " +
                "compartas tu código."
        )
        Apartado(
            "El evento se canceló, ¿qué pasa con mi dinero?",
            "Cuando un organizador cancela un evento, tus boletos se anulan automáticamente y el reembolso se " +
                "inicia a través de la misma pasarela por la que pagaste. El tiempo de acreditación depende de " +
                "tu banco."
        )
        Apartado(
            "Me equivoqué de evento o ya no puedo asistir",
            "Escríbele al organizador del evento: es quien define su política de cambios. EnZona no permite " +
                "revender ni transferir boletos entre usuarios."
        )
        Apartado(
            "Soy organizador, ¿cómo valido los boletos en la puerta?",
            "Desde tu panel del evento designa como personal de acceso a quien vaya a estar en la entrada. Esa " +
                "persona verá la pestaña «Escanear» en su cuenta. Descarguen la lista del evento antes de " +
                "empezar: el escáner funciona aunque no haya señal."
        )

        Spacer(Modifier.height(8.dp))
        Text("Contacto", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
        Spacer(Modifier.height(10.dp))
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mail, contentDescription = null, tint = NaranjaAcento,
                    modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("soporte@enzona.mx", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Text("Respondemos en menos de 24 horas hábiles", color = TextoSecundario,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ==================================================================
//  Privacidad
// ==================================================================

@Composable
fun PrivacyScreen(onVolver: () -> Unit) {
    PantallaAjuste("Privacidad", onVolver) {
        Apartado(
            "Aviso de privacidad",
            "EnZona, como responsable del tratamiento de tus datos personales, hace de tu conocimiento que la " +
                "información que nos proporcionas se trata conforme a la Ley Federal de Protección de Datos " +
                "Personales en Posesión de los Particulares."
        )
        Apartado(
            "Qué datos recabamos",
            "Nombre, correo electrónico, teléfono (opcional) y CURP. También registramos los eventos a los que " +
                "asistes y los accesos validados, porque son necesarios para emitir y verificar tus boletos."
        )
        Apartado(
            "Para qué los usamos",
            "Para identificarte de forma única y reducir el fraude de boletos, para entregarte tus boletos y " +
                "avisarte de cambios o cancelaciones, y para llevar el control de aforo de los eventos. No " +
                "vendemos tus datos ni los compartimos con fines publicitarios."
        )
        Apartado(
            "Cómo los protegemos",
            "Tu CURP se almacena cifrado y tu contraseña como un hash irreversible. No guardamos los datos de " +
                "tu tarjeta: los pagos los procesa una pasarela externa y nosotros solo conservamos una " +
                "referencia que no permite reconstruirla."
        )
        Apartado(
            "Tus derechos ARCO",
            "Puedes solicitar el Acceso, Rectificación, Cancelación u Oposición al tratamiento de tus datos " +
                "escribiendo a privacidad@enzona.mx. Atenderemos tu solicitud en un plazo máximo de 20 días " +
                "hábiles. Considera que la cancelación de tus datos puede impedir la validación de boletos ya " +
                "emitidos a tu nombre."
        )
        Apartado(
            "Cambios a este aviso",
            "Si modificamos este aviso te lo notificaremos dentro de la aplicación antes de que los cambios " +
                "entren en vigor."
        )
    }
}

// ==================================================================
//  Legal
// ==================================================================

@Composable
fun LegalScreen(onVolver: () -> Unit) {
    PantallaAjuste("Legal", onVolver) {
        Apartado(
            "Términos y condiciones de uso",
            "Al crear una cuenta aceptas usar EnZona de buena fe: proporcionar información veraz, no " +
                "suplantar a otras personas y no intentar duplicar, falsificar o revender boletos."
        )
        Apartado(
            "Transparencia de precios",
            "El importe que ves antes de pagar es el importe final. EnZona no añade cargos en pasos " +
                "posteriores ni aplica tarifas que varíen según la demanda, conforme a las disposiciones " +
                "vigentes de la Procuraduría Federal del Consumidor."
        )
        Apartado(
            "Papel de EnZona en la venta",
            "EnZona es la plataforma que conecta a organizadores y asistentes. El responsable del evento, de " +
                "su realización y de sus condiciones es siempre el organizador. Los cobros los procesa una " +
                "pasarela externa; EnZona no custodia los fondos."
        )
        Apartado(
            "Reembolsos",
            "Si un evento se cancela, los boletos se anulan y el reembolso se inicia automáticamente por la " +
                "misma vía del pago. Para cambios o cancelaciones por parte del asistente aplica la política " +
                "que defina cada organizador."
        )
        Apartado(
            "Boletos y accesos",
            "Cada boleto es único, está firmado digitalmente y solo puede utilizarse una vez. Compartir tu " +
                "código implica el riesgo de que otra persona lo use antes que tú; en ese caso el acceso te " +
                "será negado."
        )
        Apartado(
            "Proyecto académico",
            "Esta versión de EnZona es un piloto desarrollado en la Facultad de Negocios y Tecnologías de la " +
                "Universidad Veracruzana. Los pagos que se procesan en este entorno son simulados."
        )
    }
}
