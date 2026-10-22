package com.uv.enzona.data.remote

import com.google.gson.JsonElement
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contrato de la API REST de EnZona (`EnZona_Api`, Spring Boot 3 + Java 21,
 * repositorio https://github.com/WilliamSosa895/EnZona_Api). La especificación
 * completa está en `docs/openapi.yaml` de ese repositorio y, con la API
 * levantada, en `/swagger-ui.html`.
 *
 * Reglas que aplica el servidor (el teléfono propone, el servidor dispone):
 *  - el precio y el total se derivan de `tipo_boleto`, nunca del cliente;
 *  - la compra de N boletos es todo o nada frente al inventario y los asientos;
 *  - `Idempotency-Key` (8 a 64 caracteres): la misma clave devuelve la misma
 *    orden sin volver a cobrar ni emitir;
 *  - la firma HMAC del boleto se calcula y se verifica solo en el servidor.
 *
 * Listas paginadas: el cuerpo es un arreglo y la paginación viaja en cabeceras
 * (`X-Total-Count`…); `size` admite hasta 100. Errores: `application/problem+json`
 * con la propiedad `codigo` (ver `docs/CODIGOS_DE_ERROR.md` de la API).
 * [RepositorioRemoto] los convierte en `ErrorEnZona`.
 *
 * Simulaciones de la API en desarrollo: pasarela `sandbox` (tokens
 * `tok_aprobado`, `tok_rechazado`, `tok_pendiente`) y notificaciones en modo
 * `log` (el código de verificación sale en la consola del servidor).
 */
interface ApiService {

    // ---------------- acceso (RF-01, RF-02, RF-03) ----------------

    /** 201 con la cuenta en PENDIENTE; envía un código por correo y otro por teléfono si se dio uno. */
    @POST("api/auth/registro")
    suspend fun registrar(@Body datos: RegistroDto): RegistroRespuestaDto

    /** Confirma el código de un canal; la cuenta pasa a ACTIVO al completar todas las verificaciones. */
    @POST("api/auth/verificacion")
    suspend fun verificar(@Body datos: VerificacionDto): VerificacionRespuestaDto

    /** 202 siempre, exista o no el correo (mínimo 60 s entre reenvíos). */
    @POST("api/auth/verificacion/reenvio")
    suspend fun reenviarCodigo(@Body datos: ReenvioDto)

    /** `identificador` es el correo o el CURP. 401 CREDENCIALES_INVALIDAS, 403 CUENTA_PENDIENTE/CUENTA_BLOQUEADA, 429 DEMASIADOS_INTENTOS. */
    @POST("api/auth/sesion")
    suspend fun iniciarSesion(@Body datos: SesionDto): SesionRespuestaDto

    // ---------------- cuenta (RF-04) ----------------

    @GET("api/usuarios/me")
    suspend fun yo(): UsuarioDto

    /** Un teléfono nuevo queda sin verificar y recibe un código. */
    @PUT("api/usuarios/me")
    suspend fun actualizarPerfil(@Body datos: PerfilDto): UsuarioDto

    @PUT("api/usuarios/me/contrasena")
    suspend fun cambiarContrasena(@Body datos: CambiarContrasenaDto)

    @POST("api/usuarios/me/roles/organizador")
    suspend fun convertirseEnOrganizador(): UsuarioDto

    /** 409 ROL_EN_USO si tiene eventos publicados vigentes o es su único rol. */
    @DELETE("api/usuarios/me/roles/organizador")
    suspend fun dejarDeSerOrganizador(): UsuarioDto

    // ---------------- eventos (RF-05 a RF-09, RF-13, RF-19) ----------------

    /**
     * Eventos publicados que no han terminado. Con `lat` y `lng` ordena por
     * distancia dentro de `radioKm` y devuelve `distanciaKm` (RF-19).
     */
    @GET("api/eventos")
    suspend fun listarEventos(
        @Query("ciudad") ciudad: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radioKm") radioKm: Double? = null,
        @Query("q") q: String? = null,
        @Query("categoriaId") categoriaId: Int? = null,
        @Query("esDePago") esDePago: Boolean? = null,
        @Query("size") size: Int = TAMANO_PAGINA,
        @Query("page") page: Int = 0,
    ): List<EventoDto>

    /** Detalle con `tiposBoleto`. Un borrador solo lo ve su organizador o un administrador (404 para el resto). */
    @GET("api/eventos/{id}")
    suspend fun obtenerEvento(@Path("id") id: Long): EventoDto

    @GET("api/eventos/{id}/asientos")
    suspend fun asientosDe(@Path("id") id: Long): List<AsientoDto>

    @GET("api/categorias")
    suspend fun categorias(): List<CategoriaDto>

    /** Todos los eventos del organizador autenticado, en cualquier estado. */
    @GET("api/organizador/eventos")
    suspend fun misEventos(): List<EventoDto>

    @POST("api/eventos")
    suspend fun crearEvento(@Body datos: EventoSolicitudDto): EventoDto

    /** Mismo cuerpo que el alta. Los tipos de boleto se envían con `id` para conservarlos (RF-13). */
    @PUT("api/eventos/{id}")
    suspend fun modificarEvento(@Path("id") id: Long, @Body datos: EventoSolicitudDto): EventoDto

    @POST("api/eventos/{id}/publicacion")
    suspend fun publicarEvento(@Path("id") id: Long): EventoDto

    /** Sube o reemplaza la portada (JPEG, PNG o WebP). La API la guarda en su almacén y devuelve `imagenUrl`. */
    @Multipart
    @POST("api/eventos/{id}/imagen")
    suspend fun subirImagen(@Path("id") id: Long, @Part imagen: MultipartBody.Part): EventoDto

    @DELETE("api/eventos/{id}/imagen")
    suspend fun quitarImagen(@Path("id") id: Long): EventoDto

    @GET("api/eventos/{id}/validadores")
    suspend fun validadoresDe(@Path("id") id: Long): List<ValidadorDto>

    /** 201 con la persona designada; le agrega el rol VALIDADOR. */
    @POST("api/eventos/{id}/validadores")
    suspend fun designarValidador(@Path("id") id: Long, @Body datos: DesignarDto): ValidadorDto

    @DELETE("api/eventos/{id}/validadores/{usuarioId}")
    suspend fun quitarValidador(@Path("id") id: Long, @Path("usuarioId") usuarioId: Long)

    // ---------------- cancelación (RF-14) ----------------

    /** Política calculada en servidor: web y Android muestran el mismo cálculo (RNF-10). */
    @GET("api/eventos/{id}/politica-cancelacion")
    suspend fun politicaCancelacion(@Path("id") id: Long): PoliticaCancelacionDto

    /** Panel del evento (RF-17): emitidos, validados, ingresos y ocupación. Organizador dueño o administrador. */
    @GET("api/eventos/{id}/reportes")
    suspend fun reporteDe(@Path("id") id: Long): ReporteDto

    /** 409 EVENTO_NO_CANCELABLE; 422 MOTIVO_REQUERIDO si cancela un administrador sin motivo. */
    @POST("api/eventos/{id}/cancelacion")
    suspend fun cancelarEvento(@Path("id") id: Long, @Body solicitud: CancelacionDto): CancelacionResultadoDto

    // ---------------- órdenes y boletos (RF-10 a RF-12, RF-16) ----------------

    /**
     * Compra o confirmación de N boletos en una sola orden. 201 orden nueva
     * (PAGADA con boletos, o CANCELADA con pago RECHAZADO), 200 si la clave ya se usó.
     */
    @POST("api/ordenes")
    suspend fun crearOrden(
        @Header("Idempotency-Key") claveOperacion: String,
        @Body solicitud: CrearOrdenDto,
    ): OrdenDto

    @GET("api/ordenes/{id}")
    suspend fun obtenerOrden(@Path("id") id: Long): OrdenDto

    @GET("api/boletos")
    suspend fun misBoletos(): List<BoletoDto>

    @GET("api/boletos/{id}")
    suspend fun obtenerBoleto(@Path("id") id: Long): BoletoDto

    // ---------------- validación de acceso (RF-15, RNF-08) ----------------

    /** Un rechazo también responde 200: es un resultado, no un error. */
    @POST("api/validaciones")
    suspend fun validar(@Body datos: ValidarDto): ResultadoValidacionDto

    /** Boletos VALIDO y USADO del evento (código y firma) para validar sin conexión. */
    @GET("api/eventos/{id}/manifiesto")
    suspend fun manifiesto(@Path("id") id: Long): ManifiestoDto

    /** Hasta 2000 escaneos hechos sin conexión; reenviar el mismo lote no duplica registros. */
    @POST("api/validaciones/sincronizar")
    suspend fun sincronizar(@Body lote: SincronizacionDto): SincronizacionResultadoDto

    // ---------------- administración (RF-18) ----------------

    @GET("api/admin/usuarios")
    suspend fun usuarios(@Query("size") size: Int = TAMANO_PAGINA, @Query("page") page: Int = 0): List<UsuarioDto>

    /** ACTIVO o BLOQUEADO; el bloqueo aplica de inmediato aunque el token siga vigente. */
    @PATCH("api/admin/usuarios/{id}/estado")
    suspend fun cambiarEstado(@Path("id") id: Long, @Body datos: EstadoDto): UsuarioDto

    @GET("api/admin/eventos")
    suspend fun todosLosEventos(
        @Query("estado") estado: String? = null,
        @Query("size") size: Int = TAMANO_PAGINA,
        @Query("page") page: Int = 0,
    ): List<EventoDto>

    companion object {
        /** Máximo que admite la API por página. */
        const val TAMANO_PAGINA = 100
    }
}

// ==================================================================
//  DTO. Los campos que el servidor puede omitir (null) se declaran opcionales.
//  Fechas: ISO-8601 con desfase (hora del centro de México). Importes: texto
//  decimal exacto ("360.00").
// ==================================================================

/** Cuerpo de error RFC 9457 con la extensión `codigo`; `errores` es una lista `{campo, mensaje}` en los 400 de validación. */
data class ProblemaDto(
    val status: Int? = null,
    val title: String? = null,
    val detail: String? = null,
    val codigo: String? = null,
    val errores: JsonElement? = null,
    val timestamp: String? = null,
)

data class RegistroDto(
    val nombre: String,
    val correo: String,
    val telefono: String?,           // null si no se dio; si se envía también debe verificarse
    val curp: String,
    val contrasena: String,
    val organizador: Boolean,
    val aceptaAvisoPrivacidad: Boolean,   // LFPDPPP: la API exige true
)

data class RegistroRespuestaDto(val usuarioId: Long, val correo: String?, val estado: String?, val verificacionesPendientes: List<String>?)

data class VerificacionDto(val correo: String, val canal: String, val codigo: String)

data class VerificacionRespuestaDto(val estado: String?, val correoVerificado: Boolean?, val telefonoVerificado: Boolean?, val verificacionesPendientes: List<String>?)

data class ReenvioDto(val correo: String, val canal: String)

data class SesionDto(val identificador: String, val contrasena: String)

data class SesionRespuestaDto(val token: String, val tipo: String?, val expiraEnSegundos: Long?, val expiraEn: String?, val usuario: UsuarioDto)

/** Perfil (`/me`), resumen de sesión (id, nombre, correo, roles) o vista administrativa; la CURP nunca viaja. */
data class UsuarioDto(
    val id: Long,
    val nombre: String,
    val correo: String?,
    val telefono: String?,
    val roles: List<String>?,
    val estado: String?,
    val correoVerificado: Boolean?,
    val telefonoVerificado: Boolean?,
    val fechaRegistro: String?,
)

data class PerfilDto(val nombre: String, val telefono: String?)

data class CambiarContrasenaDto(val actual: String, val nueva: String)

data class CategoriaDto(val id: Int, val nombre: String)

data class EventoDto(
    val id: Long,
    val organizadorId: Long?,
    val nombre: String,
    val descripcion: String?,
    val lugar: String,
    val direccion: String?,
    val ciudad: String?,
    val latitud: Double?,            // puede faltar junto con longitud
    val longitud: Double?,
    val categoriaId: Int?,
    val categoria: String?,
    val fechaHoraInicio: String,     // p. ej. "2026-11-20T19:00:00-06:00"
    val fechaHoraFin: String?,
    val esDePago: Boolean,
    val requiereAsiento: Boolean,
    val aforo: Int,
    val disponibles: Int,            // suma de lo disponible en sus tipos de boleto
    val precioDesde: String?,
    val estado: String?,
    val distanciaKm: Double?,        // solo cuando la búsqueda llevó lat y lng
    val fechaCancelacion: String?,
    val motivoCancelacion: String?,
    val tiposBoleto: List<TipoBoletoDto>?,   // solo en el detalle
    val imagenUrl: String?,                  // absoluta (Cloudinary) o relativa a la API (/api/imagenes/…)
)

data class TipoBoletoDto(val id: Long, val nombre: String, val precio: String?, val cantidadTotal: Int?, val disponibles: Int?)

/** `estado`: DISPONIBLE, RESERVADO u OCUPADO. `numero` es texto en la API. */
data class AsientoDto(val id: Long, val seccion: String?, val fila: String?, val numero: String?, val estado: String?, val etiqueta: String?)

data class SeccionDto(val nombre: String, val filas: Int, val asientosPorFila: Int)

/** Con `id` se conserva un tipo existente; sin `id` se agrega uno nuevo. Un tipo omitido se elimina si no tiene ventas. */
data class TipoBoletoSolicitudDto(val id: Long?, val nombre: String, val precio: Double, val cantidad: Int)

data class EventoSolicitudDto(
    val nombre: String,
    val descripcion: String?,
    val lugar: String,
    val direccion: String?,
    val ciudad: String?,
    val latitud: Double?,
    val longitud: Double?,
    val categoriaId: Int?,
    val fechaHoraInicio: String,
    val fechaHoraFin: String?,
    val aforo: Int,
    val esDePago: Boolean,
    val requiereAsiento: Boolean,
    val tiposBoleto: List<TipoBoletoSolicitudDto>?,   // obligatorio en eventos de pago; nunca en gratuitos
    val secciones: List<SeccionDto>?,                 // obligatorio al crear con asiento; null = sin cambios al modificar
    val publicar: Boolean,
)

data class DesignarDto(val correo: String)

data class ValidadorDto(val usuarioId: Long, val nombre: String, val correo: String)

data class PoliticaCancelacionDto(
    val permitida: Boolean,
    val tipo: String?,               // BORRADOR | GRATUITO | DE_PAGO_SIN_VENTAS | DE_PAGO_CON_VENTAS | ADMINISTRATIVA
    val importeCobrado: String?,
    val comision: String?,           // la API siempre devuelve "0.00" (sin comisión al organizador)
    val reembolso: String?,
    val boletosAfectados: Int?,
    val motivoBloqueo: String?,
)

data class CancelacionDto(
    val motivo: String?,             // obligatorio si quien cancela es administrador
    val aceptaComision: Boolean,     // reservado; la API lo ignora
)

data class ReporteDto(
    val eventoId: Long,
    val evento: String?,
    val estado: String?,
    val aforo: Int?,
    val disponibles: Int?,
    val boletosEmitidos: Long?,      // vigentes: válidos o usados
    val accesosValidados: Long?,
    val accesosRechazados: Long?,
    val boletosCancelados: Long?,
    val ingresos: String?,           // órdenes pagadas
    val reembolsado: String?,
    val ocupacion: Double?,
)

data class CancelacionResultadoDto(
    val eventoId: Long,
    val importeReembolsado: String?,
    val comision: String?,
    val boletosAnulados: Int?,
    val asistentesNotificados: Int?,
    val canceladoPor: Long?,
    val fechaCancelacion: String?,
)

/**
 * Solicitud de compra completa. `metodo`: GRATIS en eventos gratuitos, TARJETA en
 * los de pago (OXXO y SPEI responden 422). `tokenPago` es el token de un solo uso
 * que genera la pasarela; la API nunca recibe datos de tarjeta.
 */
data class CrearOrdenDto(
    val eventoId: Long,
    val tipoBoletoId: Long?,
    val cantidad: Int,
    val asientosIds: List<Long>,     // vacío si el evento no requiere asiento
    val metodo: String,
    val tokenPago: String?,
)

/** La orden y sus boletos (ninguno si el pago no está aprobado). */
data class OrdenDto(
    val ordenId: Long,
    val eventoId: Long,
    val estadoOrden: String?,        // PENDIENTE | PAGADA | CANCELADA | REEMBOLSADA
    val estadoPago: String?,         // APROBADO | RECHAZADO | REEMBOLSADO; null sin cobro o pasarela pendiente
    val total: String?,
    val moneda: String?,
    val referenciaPago: String?,
    val expiraEn: String?,
    val mensaje: String?,
    val boletos: List<BoletoDto>?,
)

/** Boleto con los datos del evento ya resueltos; el QR es `contenidoQr` (= "codigo.firma"). */
data class BoletoDto(
    val id: Long,
    val ordenId: Long,
    val eventoId: Long,
    val tipoBoletoId: Long?,
    val asientoId: Long?,
    val codigo: String,
    val firma: String?,
    val contenidoQr: String?,
    val estado: String?,
    val fechaEmision: String?,
    val fechaUso: String?,
    val eventoNombre: String?,
    val fechaHoraInicio: String?,
    val lugar: String?,
    val tipoNombre: String?,
    val asientoEtiqueta: String?,    // "Platea · Fila A · Asiento 1"
)

data class ValidarDto(val eventoId: Long, val qr: String)

data class ResultadoValidacionDto(
    val resultado: String?,          // PERMITIDO | RECHAZADO
    val motivo: String?,             // FORMATO_INVALIDO | BOLETO_INEXISTENTE | FIRMA_INVALIDA | OTRO_EVENTO | EVENTO_CANCELADO | YA_UTILIZADO | CANCELADO | EXPIRADO
    val mensaje: String?,
    val codigo: String?,
    val tipoBoleto: String?,
    val asiento: String?,
    val fechaUso: String?,
)

data class ManifiestoDto(val eventoId: Long, val evento: String?, val generadoEn: String?, val total: Int?, val boletos: List<EntradaManifiestoDto>?)

data class EntradaManifiestoDto(val codigo: String, val firma: String?, val estado: String?, val tipoBoleto: String?, val asiento: String?)

data class RegistroOfflineDto(val qr: String, val fechaHora: String)

data class SincronizacionDto(val eventoId: Long, val registros: List<RegistroOfflineDto>)

data class ResultadoSincronizadoDto(val qr: String?, val fechaHora: String?, val resultado: String?, val motivo: String?, val conflicto: Boolean?, val duplicado: Boolean?)

data class SincronizacionResultadoDto(val procesados: Int?, val permitidos: Int?, val rechazados: Int?, val conflictos: Int?, val resultados: List<ResultadoSincronizadoDto>?)

data class EstadoDto(val estado: String)
