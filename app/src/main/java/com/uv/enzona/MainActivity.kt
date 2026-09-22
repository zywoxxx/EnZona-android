package com.uv.enzona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.Rol
import com.uv.enzona.ui.screens.CheckoutScreen
import com.uv.enzona.ui.screens.EventDetailScreen
import com.uv.enzona.ui.screens.HomeScreen
import com.uv.enzona.ui.screens.NotificacionesScreen
import com.uv.enzona.ui.screens.OrderSummaryScreen
import com.uv.enzona.ui.screens.ajustes.EditProfileScreen
import com.uv.enzona.ui.screens.ajustes.HelpScreen
import com.uv.enzona.ui.screens.ajustes.LegalScreen
import com.uv.enzona.ui.screens.ajustes.PaymentMethodsScreen
import com.uv.enzona.ui.screens.ajustes.PrivacyScreen
import com.uv.enzona.ui.screens.ajustes.SecurityScreen
import com.uv.enzona.ui.screens.ProfileScreen
import com.uv.enzona.ui.screens.SeatMapScreen
import com.uv.enzona.ui.screens.TicketDetailScreen
import com.uv.enzona.ui.screens.TicketsScreen
import com.uv.enzona.ui.screens.admin.AdminScreen
import com.uv.enzona.ui.screens.auth.LoginScreen
import com.uv.enzona.ui.screens.auth.OtpScreen
import com.uv.enzona.ui.screens.auth.RegisterScreen
import com.uv.enzona.ui.screens.organizer.EventFormScreen
import com.uv.enzona.ui.screens.organizer.EventStatsScreen
import com.uv.enzona.ui.screens.organizer.OrganizerHomeScreen
import com.uv.enzona.ui.screens.validator.ScannerScreen
import androidx.compose.material3.MaterialTheme
import com.uv.enzona.ui.theme.EnZonaTheme

object Rutas {
    // Acceso
    const val LOGIN = "login"
    const val LOGIN_PERSONAL = "login_personal"
    const val REGISTRO = "registro"
    const val OTP = "otp/{usuarioId}"

    // Asistente
    const val HOME = "home"
    const val EVENTO = "evento/{id}"
    // La compra viaja completa por la ruta: evento, tipo, cantidad y asientos ("0" si no aplica)
    const val ASIENTOS = "asientos/{eventoId}/{tipoId}/{cantidad}"
    const val PAGO = "pago/{eventoId}/{tipoId}/{cantidad}/{asientos}"
    const val ORDEN = "orden/{id}"
    const val BOLETOS = "boletos"
    const val BOLETO = "boleto/{id}"
    const val NOTIFICACIONES = "notificaciones"

    // Ajustes de la cuenta
    const val EDITAR_PERFIL = "editar_perfil"
    const val METODOS_PAGO = "metodos_pago"
    const val SEGURIDAD = "seguridad"
    const val AYUDA = "ayuda"
    const val PRIVACIDAD = "privacidad"
    const val LEGAL = "legal"

    // Organizador
    const val ORG_HOME = "org_home"
    const val ORG_NUEVO = "org_nuevo"
    const val ORG_EDITAR = "org_editar/{id}"
    const val ORG_STATS = "org_stats/{id}"

    // Validador y administración
    const val VALIDADOR = "validador"
    const val ADMIN = "admin"

    // Común
    const val PERFIL = "perfil"

    fun otp(usuarioId: Long) = "otp/$usuarioId"
    fun evento(id: Long) = "evento/$id"
    fun asientos(eventoId: Long, tipoId: Long?, cantidad: Int) = "asientos/$eventoId/${tipoId ?: 0L}/$cantidad"
    fun pago(eventoId: Long, tipoId: Long?, cantidad: Int, asientosIds: List<Long>) =
        "pago/$eventoId/${tipoId ?: 0L}/$cantidad/${asientosIds.joinToString("-").ifEmpty { "0" }}"
    fun orden(id: Long) = "orden/$id"
    fun boleto(id: Long) = "boleto/$id"
    fun orgEditar(id: Long) = "org_editar/$id"
    fun orgStats(id: Long) = "org_stats/$id"
}

private data class DestinoBarra(val ruta: String, val etiqueta: String, val icono: ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barras del sistema claras con iconos oscuros, independientemente del modo del sistema
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            EnZonaTheme { AppEnZona() }
        }
    }
}

@Composable
fun AppEnZona() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val rutaActual = backStack?.destination?.route

    val destinos = destinosDeRol()
    val mostrarBarra = SessionManager.autenticado && destinos.any { it.ruta == rutaActual }

    Scaffold(
        bottomBar = {
            if (mostrarBarra) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinos.forEach { destino ->
                        NavigationBarItem(
                            selected = rutaActual == destino.ruta,
                            onClick = { navegarATab(navController, destino.ruta, rutaInicioDeRol()) },
                            icon = { Icon(destino.icono, contentDescription = destino.etiqueta) },
                            label = { Text(destino.etiqueta) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                    }
                }
            }
        }
    ) { relleno ->
        NavHost(
            navController = navController,
            startDestination = Rutas.LOGIN,
            modifier = Modifier.padding(relleno)
        ) {
            // ---------------- Acceso ----------------
            composable(Rutas.LOGIN) {
                LoginScreen(
                    modoPersonal = false,
                    onAutenticado = { irAInicioDeRol(navController) },
                    onIrARegistro = { navController.navigate(Rutas.REGISTRO) },
                    onCambiarModo = { navController.navigate(Rutas.LOGIN_PERSONAL) },
                )
            }
            composable(Rutas.LOGIN_PERSONAL) {
                LoginScreen(
                    modoPersonal = true,
                    onAutenticado = { irAInicioDeRol(navController) },
                    onIrARegistro = {},
                    onCambiarModo = { navController.popBackStack() },
                )
            }
            composable(Rutas.REGISTRO) {
                RegisterScreen(
                    onVolver = { navController.popBackStack() },
                    onRegistrado = { usuarioId ->
                        navController.navigate(Rutas.otp(usuarioId)) {
                            popUpTo(Rutas.LOGIN)
                        }
                    }
                )
            }
            composable(Rutas.OTP) { entrada ->
                val usuarioId = entrada.arguments?.getString("usuarioId")?.toLongOrNull() ?: return@composable
                OtpScreen(
                    usuarioId = usuarioId,
                    onVerificado = {
                        navController.navigate(Rutas.LOGIN) {
                            popUpTo(Rutas.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            // ---------------- Asistente ----------------
            composable(Rutas.HOME) {
                HomeScreen(
                    onEventoClick = { id -> navController.navigate(Rutas.evento(id)) },
                    onNotificaciones = { navController.navigate(Rutas.NOTIFICACIONES) },
                )
            }
            composable(Rutas.NOTIFICACIONES) {
                NotificacionesScreen(
                    onVolver = { navController.popBackStack() },
                    onAbrirBoleto = { id -> navController.navigate(Rutas.boleto(id)) },
                    onAbrirEvento = { id -> navController.navigate(Rutas.evento(id)) },
                )
            }
            composable(Rutas.EVENTO) { entrada ->
                val id = entrada.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                EventDetailScreen(
                    eventoId = id,
                    onVolver = { navController.popBackStack() },
                    onBoletoEmitido = { boletoId ->
                        navController.navigate(Rutas.boleto(boletoId)) { popUpTo(Rutas.HOME) }
                    },
                    onElegirAsientos = { eventoId, tipoId, cantidad ->
                        navController.navigate(Rutas.asientos(eventoId, tipoId, cantidad))
                    },
                    onIrAPago = { eventoId, tipoId, cantidad ->
                        navController.navigate(Rutas.pago(eventoId, tipoId, cantidad, emptyList()))
                    },
                    onVerBoletos = { navegarATab(navController, Rutas.BOLETOS, rutaInicioDeRol()) },
                )
            }
            composable(Rutas.ASIENTOS) { entrada ->
                val eventoId = entrada.arguments?.getString("eventoId")?.toLongOrNull() ?: return@composable
                val tipoId = entrada.arguments?.getString("tipoId")?.toLongOrNull()
                val cantidad = entrada.arguments?.getString("cantidad")?.toIntOrNull() ?: 1
                SeatMapScreen(
                    eventoId = eventoId,
                    tipoBoletoId = tipoId?.takeIf { it != 0L },
                    cantidad = cantidad,
                    onVolver = { navController.popBackStack() },
                    onBoletoEmitido = { boletoId ->
                        navController.navigate(Rutas.boleto(boletoId)) { popUpTo(Rutas.HOME) }
                    },
                    onIrAPago = { evId, tpId, n, asientos ->
                        navController.navigate(Rutas.pago(evId, tpId, n, asientos))
                    }
                )
            }
            composable(Rutas.PAGO) { entrada ->
                val eventoId = entrada.arguments?.getString("eventoId")?.toLongOrNull() ?: return@composable
                val tipoId = entrada.arguments?.getString("tipoId")?.toLongOrNull()
                val cantidad = entrada.arguments?.getString("cantidad")?.toIntOrNull() ?: 1
                val asientos = entrada.arguments?.getString("asientos").orEmpty()
                    .split("-").mapNotNull { it.toLongOrNull() }.filter { it != 0L }
                CheckoutScreen(
                    eventoId = eventoId,
                    tipoBoletoId = tipoId?.takeIf { it != 0L },
                    cantidad = cantidad,
                    asientosIds = asientos,
                    onVolver = { navController.popBackStack() },
                    onIrAMetodos = { navController.navigate(Rutas.METODOS_PAGO) },
                    onCompraRealizada = { compra ->
                        // Un boleto: directo al QR. Varios: resumen con "Boleto k de N"
                        val destino = if (compra.boletos.size == 1) Rutas.boleto(compra.boletos.first().id)
                        else Rutas.orden(compra.orden.id)
                        navController.navigate(destino) { popUpTo(Rutas.HOME) }
                    }
                )
            }
            composable(Rutas.ORDEN) { entrada ->
                val id = entrada.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                OrderSummaryScreen(
                    ordenId = id,
                    onVerBoleto = { boletoId -> navController.navigate(Rutas.boleto(boletoId)) },
                    onVerMisBoletos = { navegarATab(navController, Rutas.BOLETOS, rutaInicioDeRol()) },
                    onVolverAInicio = { navegarATab(navController, Rutas.HOME, rutaInicioDeRol()) },
                )
            }
            composable(Rutas.BOLETOS) {
                TicketsScreen(
                    onBoletoClick = { id -> navController.navigate(Rutas.boleto(id)) },
                    onExplorar = { navegarATab(navController, Rutas.HOME, rutaInicioDeRol()) },
                )
            }
            composable(Rutas.BOLETO) { entrada ->
                val id = entrada.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                TicketDetailScreen(
                    boletoId = id,
                    onVolver = { navController.popBackStack() },
                    onAbrirBoleto = { otroId ->
                        navController.navigate(Rutas.boleto(otroId)) { popUpTo(Rutas.BOLETO) { inclusive = true } }
                    },
                )
            }

            // ---------------- Organizador ----------------
            composable(Rutas.ORG_HOME) {
                OrganizerHomeScreen(
                    onNuevoEvento = { navController.navigate(Rutas.ORG_NUEVO) },
                    onEventoClick = { id -> navController.navigate(Rutas.orgStats(id)) }
                )
            }
            composable(Rutas.ORG_NUEVO) {
                EventFormScreen(
                    eventoId = null,
                    onVolver = { navController.popBackStack() },
                    onGuardado = { navController.popBackStack() }
                )
            }
            composable(Rutas.ORG_EDITAR) { entrada ->
                val id = entrada.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                EventFormScreen(
                    eventoId = id,
                    onVolver = { navController.popBackStack() },
                    onGuardado = { navController.popBackStack() }
                )
            }
            composable(Rutas.ORG_STATS) { entrada ->
                val id = entrada.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                EventStatsScreen(
                    eventoId = id,
                    onVolver = { navController.popBackStack() },
                    onEditar = { eventoId -> navController.navigate(Rutas.orgEditar(eventoId)) }
                )
            }

            // ---------------- Validador y administración ----------------
            composable(Rutas.VALIDADOR) { ScannerScreen() }
            composable(Rutas.ADMIN) { AdminScreen() }

            // ---------------- Común ----------------
            composable(Rutas.PERFIL) {
                ProfileScreen(
                    onEditarPerfil = { navController.navigate(Rutas.EDITAR_PERFIL) },
                    onMetodosPago = { navController.navigate(Rutas.METODOS_PAGO) },
                    onSeguridad = { navController.navigate(Rutas.SEGURIDAD) },
                    onAyuda = { navController.navigate(Rutas.AYUDA) },
                    onPrivacidad = { navController.navigate(Rutas.PRIVACIDAD) },
                    onLegal = { navController.navigate(Rutas.LEGAL) },
                    onCerrarSesion = {
                        navController.navigate(Rutas.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    // Al adquirir o dejar el rol de organizador la barra se rearma; si se pierde el
                    // rol activo, la sesión vuelve al acceso normal y a Inicio
                    onRolCambiado = {
                        navController.navigate(rutaInicioDeRol()) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Rutas.EDITAR_PERFIL) {
                EditProfileScreen(onVolver = { navController.popBackStack() })
            }
            composable(Rutas.METODOS_PAGO) {
                PaymentMethodsScreen(onVolver = { navController.popBackStack() })
            }
            composable(Rutas.SEGURIDAD) {
                SecurityScreen(onVolver = { navController.popBackStack() })
            }
            composable(Rutas.AYUDA) {
                HelpScreen(onVolver = { navController.popBackStack() })
            }
            composable(Rutas.PRIVACIDAD) {
                PrivacyScreen(onVolver = { navController.popBackStack() })
            }
            composable(Rutas.LEGAL) {
                LegalScreen(onVolver = { navController.popBackStack() })
            }
        }
    }
}

/** Pestañas de la barra inferior según el rol de quien inició sesión. */
@Composable
private fun destinosDeRol(): List<DestinoBarra> {
    val usuario = SessionManager.usuario ?: return emptyList()
    val destinos = mutableListOf<DestinoBarra>()

    if (Rol.ASISTENTE in usuario.roles) {
        destinos += DestinoBarra(Rutas.HOME, "Inicio", Icons.Filled.Home)
        destinos += DestinoBarra(Rutas.BOLETOS, "Mis boletos", Icons.Filled.ConfirmationNumber)
    }
    if (Rol.ORGANIZADOR in usuario.roles) {
        destinos += DestinoBarra(Rutas.ORG_HOME, "Mis eventos", Icons.Filled.Event)
    }
    if (Rol.VALIDADOR in usuario.roles) {
        destinos += DestinoBarra(Rutas.VALIDADOR, "Escanear", Icons.Filled.QrCodeScanner)
    }
    if (Rol.ADMIN in usuario.roles) {
        destinos += DestinoBarra(Rutas.ADMIN, "Admin", Icons.Filled.AdminPanelSettings)
    }
    destinos += DestinoBarra(Rutas.PERFIL, "Perfil", Icons.Filled.Person)
    return destinos
}

/**
 * Ruta principal tras iniciar sesión.
 *
 * Un usuario puede ser asistente y organizador a la vez (documento de visión,
 * sección 6): si entró por el acceso normal va a «Inicio», y si entró por el
 * acceso del personal va al panel que le corresponde por rol.
 */
private fun rutaInicioDeRol(): String {
    val usuario = SessionManager.usuario ?: return Rutas.LOGIN
    if (!SessionManager.accesoPersonal && Rol.ASISTENTE in usuario.roles) return Rutas.HOME
    return when (usuario.rolPrincipal) {
        Rol.ADMIN -> Rutas.ADMIN
        Rol.ORGANIZADOR -> Rutas.ORG_HOME
        Rol.VALIDADOR -> Rutas.VALIDADOR
        else -> Rutas.HOME
    }
}

/** Tras autenticarse, se limpia la pila de acceso y se entra al panel del rol. */
private fun irAInicioDeRol(navController: NavHostController) {
    navController.navigate(rutaInicioDeRol()) {
        popUpTo(0) { inclusive = true }
    }
}

/** Cambio de pestaña: mantiene una sola entrada por pestaña sobre la pantalla principal. */
private fun navegarATab(navController: NavHostController, ruta: String, rutaInicio: String) {
    if (ruta == rutaInicio) {
        navController.navigate(ruta) {
            launchSingleTop = true
            popUpTo(rutaInicio) { inclusive = true }
        }
    } else {
        navController.navigate(ruta) {
            launchSingleTop = true
            popUpTo(rutaInicio)
        }
    }
}
