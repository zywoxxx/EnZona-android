package com.uv.enzona.data

import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.CargoOrganizador
import com.uv.enzona.data.model.Categoria
import com.uv.enzona.data.model.CompraRealizada
import com.uv.enzona.data.model.EstadisticasAdmin
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.LayoutSeccion
import com.uv.enzona.data.model.MetodoPago
import com.uv.enzona.data.model.NotificacionAsistente
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.data.model.ResultadoCancelacion
import com.uv.enzona.data.model.ResultadoValidacion
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.data.model.TipoMetodoPago
import com.uv.enzona.data.model.Usuario
import com.uv.enzona.data.model.ValidacionAcceso
import java.time.LocalDateTime

/**
 * Lo que las pantallas necesitan de los datos, sin saber de dónde vienen.
 *
 * Hay dos implementaciones:
 *  - [RepositorioRemoto] (data/remote): la API REST de EnZona (`EnZona_Api`).
 *  - [RepositorioMock]: los datos de prueba en memoria de `MockRepository`.
 *
 * Todas las operaciones son `suspend`: contra la API hay red de por medio. Un
 * fallo se comunica con [ErrorEnZona] (o con una subclase de `ErrorCompra` en la
 * compra), cuyo `message` es el texto que se muestra a la persona.
 *
 * Las operaciones actúan sobre la cuenta con sesión iniciada; el servidor la
 * identifica por el token, nunca por un id que envíe el teléfono.
 *
 * No todas las fuentes soportan todo: [capacidades] dice qué partes de la app
 * tienen respaldo y la interfaz oculta las que no.
 */
interface RepositorioEnZona {

    /** true cuando los datos son de demostración en memoria (se muestra la etiqueta «Datos de prueba»). */
    val esDemostracion: Boolean

    /** Funciones que esta fuente de datos implementa. Por defecto, todas. */
    val capacidades: Capacidades get() = Capacidades()

    // ------------------------------------------------------------ identidad

    suspend fun iniciarSesion(identificador: String, contrasena: String, accesoPersonal: Boolean): Usuario

    /** Recupera la sesión guardada al abrir la app. Null si no hay sesión o ya expiró. */
    suspend fun restaurarSesion(): Usuario?

    /** true si la sesión guardada se abrió por el acceso de organizadores y personal. */
    fun sesionDePersonal(): Boolean = false

    suspend fun cerrarSesion()

    /**
     * Crea la cuenta en estado PENDIENTE y envía el código de verificación al
     * correo (y otro al teléfono si se dio uno). `aceptaAvisoPrivacidad` debe ser
     * true (LFPDPPP); la API lo exige.
     */
    suspend fun registrar(
        nombre: String,
        correo: String,
        telefono: String,
        curp: String,
        contrasena: String,
        aceptaAvisoPrivacidad: Boolean,
    ): RegistroPendiente

    /**
     * Confirma el código del canal pendiente. Si después queda otro canal por
     * verificar (el teléfono), el resultado lo indica para pedir el segundo código.
     */
    suspend fun verificarOtp(usuarioId: Long, codigo: String): EstadoVerificacion

    /** Devuelve el código solo cuando el envío es una simulación (datos de prueba); con la API siempre es null. */
    suspend fun reenviarOtp(usuarioId: Long): String?

    suspend fun usuarioActual(): Usuario

    suspend fun actualizarPerfil(nombre: String, telefono: String): Usuario

    suspend fun cambiarContrasena(actual: String, nueva: String)

    suspend fun convertirseEnOrganizador(): Usuario

    suspend fun dejarDeSerOrganizador(): Usuario

    /** Motivo que impide dejar el rol de organizador, o null si procede. */
    suspend fun motivoNoPuedeDejarOrganizador(): String?

    // ------------------------------------------------------------ eventos

    suspend fun categorias(): List<Categoria>

    /**
     * Eventos publicados. Con `ubicacion` el servidor ordena por distancia real
     * (CP-17); sin ella se filtra por `ciudad`.
     */
    suspend fun eventosPublicados(ciudad: String?, ubicacion: Ubicacion?): List<Evento>

    suspend fun evento(id: Long): Evento

    suspend fun tiposDe(eventoId: Long): List<TipoBoleto>

    suspend fun asientosDe(eventoId: Long): List<Asiento>

    suspend fun misEventos(): List<Evento>

    suspend fun crearEvento(datos: DatosEvento, publicar: Boolean): Evento

    suspend fun modificarEvento(eventoId: Long, datos: DatosEvento): Evento

    suspend fun publicarEvento(eventoId: Long): Evento

    /** Solo si `capacidades.marcarRealizado`; la API lo hace sola cuando el evento termina. */
    suspend fun marcarRealizado(eventoId: Long): Evento

    /** `secciones` vacío desactiva el plano de asientos. */
    suspend fun cambiarAsientos(eventoId: Long, secciones: List<LayoutSeccion>): Evento

    /**
     * Sube o reemplaza la portada del evento. Con la API, el archivo viaja a
     * `POST /api/eventos/{id}/imagen` y es el servidor quien lo guarda en su almacén
     * (local o Cloudinary): el teléfono nunca conoce credenciales del almacén.
     */
    suspend fun subirImagenEvento(eventoId: Long, imagen: ImagenParaSubir): Evento

    suspend fun quitarImagenEvento(eventoId: Long): Evento

    suspend fun politicaCancelacion(eventoId: Long, administrativa: Boolean): PoliticaCancelacion

    suspend fun cancelarEvento(eventoId: Long, motivo: String?, aceptaComision: Boolean, administrativa: Boolean): ResultadoCancelacion

    suspend fun boletosDeEvento(eventoId: Long): List<BoletoDetalle>

    /** Vacío si `capacidades.cargosOrganizador` es false. */
    suspend fun cargosDeOrganizador(): List<CargoOrganizador>

    suspend fun personalDeAcceso(eventoId: Long): List<PersonalAcceso>

    suspend fun designarValidador(eventoId: Long, correo: String): List<PersonalAcceso>

    suspend fun quitarValidador(eventoId: Long, usuarioId: Long): List<PersonalAcceso>

    // ------------------------------------------------------------ compra y boletos

    /**
     * Compra de N boletos o confirmación gratuita. Lanza una subclase de
     * `ErrorCompra` si no procede (un pago rechazado es `ErrorCompra.PagoRechazado`,
     * recuperable). Con OXXO o SPEI, solo si `capacidades.pagosDiferidos`.
     */
    suspend fun comprar(solicitud: SolicitudCompra, pago: PagoElegido?): CompraRealizada

    suspend fun orden(ordenId: Long): CompraRealizada

    /** SIMULACIÓN de datos de prueba: confirma un pago pendiente (en producción lo hace el aviso de la pasarela). */
    suspend fun confirmarPagoPendiente(pagoId: Long): CompraRealizada

    suspend fun misBoletos(): List<BoletoDetalle>

    suspend fun boleto(boletoId: Long): BoletoDetalle

    /** Vacío si `capacidades.metodosGuardados` es false. */
    suspend fun metodosDePago(): List<MetodoPago>

    /**
     * Guarda una tarjeta. El número completo solo se usa en el teléfono para
     * deducir la marca y los últimos cuatro dígitos y simular la tokenización;
     * nunca se envía ni se almacena.
     */
    suspend fun agregarTarjeta(numero: String, vencimiento: String, titular: String): MetodoPago

    suspend fun eliminarMetodo(metodoId: Long)

    suspend fun marcarPredeterminado(metodoId: Long)

    // ------------------------------------------------------------ validación de acceso

    suspend fun eventosParaValidar(): List<Evento>

    suspend fun validarQr(contenido: String, eventoId: Long): ResultadoValidacion

    suspend fun ultimasValidaciones(eventoId: Long?): List<ValidacionAcceso>

    /** Descarga el manifiesto del evento para validar sin conexión. Devuelve cuántos boletos vigentes trae. */
    suspend fun prepararModoSinConexion(eventoId: Long): Int

    /** Valida contra el manifiesto descargado y deja el escaneo en cola para sincronizar. */
    suspend fun validarSinConexion(contenido: String, eventoId: Long): ResultadoValidacion

    fun pendientesDeSincronizar(): Int

    suspend fun sincronizarValidaciones(): ResumenSincronizacion

    // ------------------------------------------------------------ notificaciones (solo si capacidades.notificaciones)

    suspend fun notificaciones(): List<NotificacionAsistente>

    suspend fun notificacionesNoLeidas(): Int

    suspend fun marcarNotificacionLeida(id: Long)

    suspend fun marcarNotificacionesLeidas()

    // ------------------------------------------------------------ administración

    suspend fun usuarios(): List<Usuario>

    /** Solo si `capacidades.rolesPorAdministrador`. */
    suspend fun asignarRol(usuarioId: Long, rol: Rol, asignar: Boolean): Usuario

    suspend fun cambiarEstadoUsuario(usuarioId: Long, estado: EstadoUsuario): Usuario

    suspend fun todosLosEventos(): List<Evento>

    /** Indicadores de toda la plataforma: cuentas, eventos, boletos, accesos e ingresos (solo administración). */
    suspend fun estadisticasAdmin(): EstadisticasAdmin
}

// ==================================================================
//  Tipos de apoyo
// ==================================================================

/**
 * Qué partes de la app respalda la fuente de datos. Lo que no se soporta no se
 * muestra: la app no ofrece acciones que el servidor va a rechazar.
 */
data class Capacidades(
    /** Tarjetas guardadas (pantalla «Métodos de pago» y pago con tarjeta guardada). */
    val metodosGuardados: Boolean = true,
    /** Pago en OXXO y por SPEI (orden pendiente con referencia). */
    val pagosDiferidos: Boolean = true,
    /** Centro de notificaciones dentro de la app (campana de Inicio). */
    val notificaciones: Boolean = true,
    /** Comisión de cancelación cargada al organizador. */
    val cargosOrganizador: Boolean = true,
    /** El administrador asigna o quita roles. */
    val rolesPorAdministrador: Boolean = true,
    /** El organizador marca a mano un evento como realizado. */
    val marcarRealizado: Boolean = true,
)

/**
 * Error de una operación. `message` es el texto para la persona (el `detail` del
 * ProblemDetail de la API). `estado` es el código HTTP, o 0 si no hubo respuesta.
 */
open class ErrorEnZona(
    val estado: Int,
    val codigo: String,
    mensaje: String,
    val propiedades: Map<String, Any?> = emptyMap(),
) : Exception(mensaje) {
    val sinConexion: Boolean get() = estado == 0
    val sesionVencida: Boolean get() = estado == 401 && codigo == "NO_AUTENTICADO"
}

data class Ubicacion(val latitud: Double, val longitud: Double)

/** Medio por el que se envía el código de un solo uso (RF-02). */
enum class CanalVerificacion { CORREO, TELEFONO }

data class RegistroPendiente(
    val usuarioId: Long,
    /** Código de verificación cuando el envío es una simulación; null si se envió de verdad. */
    val otpSimulado: String?,
    val correo: String = "",
    /** Canales que faltan por verificar, en el orden en que se piden. */
    val canalesPendientes: List<CanalVerificacion> = listOf(CanalVerificacion.CORREO),
)

/** Resultado de confirmar un código: la cuenta quedó activa, o falta el código de otro canal. */
data class EstadoVerificacion(
    val completa: Boolean,
    val siguienteCanal: CanalVerificacion? = null,
)

data class PersonalAcceso(val usuarioId: Long, val nombre: String, val correo: String)

/** Medio con el que se paga una orden. `simularRechazo` usa el token que la pasarela simulada rechaza (CP-10). */
data class PagoElegido(
    val medio: TipoMetodoPago,
    val metodoPagoId: Long? = null,
    val simularRechazo: Boolean = false,
)

data class ResumenSincronizacion(val enviadas: Int, val permitidas: Int, val rechazadas: Int, val duplicadas: Int)

/**
 * Imagen elegida en el teléfono, ya reducida y comprimida (JPEG), lista para subir.
 * `uriLocal` apunta a la copia en caché; los datos de prueba la usan como portada.
 */
class ImagenParaSubir(val bytes: ByteArray, val tipoMime: String, val uriLocal: String)

/** Datos del formulario de evento (alta y edición). */
data class DatosEvento(
    val nombre: String,
    val descripcion: String,
    val lugar: String,
    val direccion: String,
    val categoria: String,
    val fecha: LocalDateTime,
    val fechaFin: LocalDateTime?,
    val esDePago: Boolean,
    val precio: Double,
    val aforo: Int,
    val latitud: Double?,
    val longitud: Double?,
    val ciudad: String = "Orizaba",
    /** Vacío = sin plano de asientos. Solo se usa en el alta. */
    val secciones: List<LayoutSeccion> = emptyList(),
)
