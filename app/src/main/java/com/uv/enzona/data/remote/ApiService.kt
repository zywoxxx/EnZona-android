package com.uv.enzona.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato de la API REST del backend Spring Boot (aún no conectado; el
 * backend y la web no forman parte de este repositorio).
 *
 * v2 · compra múltiple. `POST /api/ordenes` recibe la compra completa
 * (evento, tipo, cantidad y asientos) y devuelve la orden con TODOS los boletos
 * emitidos. Una compra de cantidad 1 es el mismo contrato con `cantidad = 1` y
 * `asientosIds` de un elemento (o vacío si el evento no tiene asientos).
 *
 * Reglas que el servidor debe aplicar (no confiar en el teléfono):
 *  - derivar el precio unitario y el total desde `tipo_boleto`, nunca del cliente;
 *  - validar que tipo y asientos pertenezcan al evento, que los asientos sean
 *    distintos y estén libres, y que haya cupo y stock para N (por lote);
 *  - emitir N boletos firmados (HMAC) o ninguno, en una operación atómica
 *    frente al inventario; el cobro externo se compensa si no puede cumplirse;
 *  - procesar `Idempotency-Key` de forma idempotente: la misma clave devuelve la
 *    misma orden sin volver a cobrar ni emitir;
 *  - en OXXO/SPEI responder `estadoPago = PENDIENTE` y emitir los boletos al
 *    confirmar la pasarela (requiere ampliar `estado_pago` en el esquema).
 *
 * Cuando el backend esté desplegado:
 *  1. Cambia BASE_URL por la URL real (en el emulador, http://10.0.2.2:8080/ apunta
 *     a tu máquina donde corre Spring Boot).
 *  2. Crea un RemoteRepository que use este servicio y reemplaza MockRepository
 *     en las pantallas, manteniendo `comprar(SolicitudCompra)`.
 */
interface ApiService {

    @GET("api/eventos")
    suspend fun listarEventos(): List<EventoDto>

    @GET("api/eventos/{id}")
    suspend fun obtenerEvento(@Path("id") id: Long): EventoDto

    /** Compra o confirmación de N boletos en una sola orden. */
    @POST("api/ordenes")
    suspend fun crearOrden(
        @Header("Idempotency-Key") claveOperacion: String,
        @Body solicitud: CrearOrdenDto,
    ): OrdenCreadaDto

    @GET("api/ordenes/{id}")
    suspend fun obtenerOrden(@Path("id") id: Long): OrdenCreadaDto

    @GET("api/boletos")
    suspend fun misBoletos(): List<BoletoDto>

    // ---------------- v3 · cancelación (RF-14) — pendiente de backend ----------------

    /**
     * Política de cancelación calculada en servidor con la misma regla que
     * `politicaCancelacion()` para que web y Android muestren el mismo cálculo (RNF-10).
     */
    @GET("api/eventos/{id}/politica-cancelacion")
    suspend fun politicaCancelacion(@Path("id") id: Long): PoliticaCancelacionDto

    /**
     * Cancela el evento. Errores: 409 evento iniciado o ya cancelado; 422 comisión
     * no aceptada (o motivo ausente en cancelación administrativa); 403 rol sin permiso.
     */
    @POST("api/eventos/{id}/cancelacion")
    suspend fun cancelarEvento(@Path("id") id: Long, @Body solicitud: CancelacionDto): CancelacionResultadoDto

    // ---------------- v3 · rol de organizador — pendiente de backend ----------------

    /** Añade el rol ORGANIZADOR a la cuenta autenticada (fila en usuario_rol). */
    @POST("api/usuarios/me/roles/organizador")
    suspend fun convertirseEnOrganizador(): UsuarioDto

    /** Quita el rol ORGANIZADOR. 409 si tiene eventos publicados con boletos vigentes o es su único rol. */
    @DELETE("api/usuarios/me/roles/organizador")
    suspend fun dejarDeSerOrganizador(): UsuarioDto
}

data class PoliticaCancelacionDto(
    val permitida: Boolean,
    val tipo: String?,               // BORRADOR | GRATUITO | DE_PAGO_SIN_VENTAS | DE_PAGO_CON_COMISION | ADMINISTRATIVA
    val importeCobrado: String,      // decimal exacto "1500.00"
    val comision: String,            // "150.00"
    val reembolso: String,           // "1500.00"
    val boletosAfectados: Int,
    val motivoBloqueo: String?,
)

data class CancelacionDto(
    val motivo: String?,             // obligatorio si quien cancela es administrador
    val aceptaComision: Boolean,     // obligatorio true cuando la política cobra comisión
)

data class CancelacionResultadoDto(
    val eventoId: Long,
    val importeReembolsado: String,
    val comision: String,
    val boletosAnulados: Int,
    val asistentesNotificados: Int,
    val canceladoPor: Long,
    val fechaCancelacion: String,    // ISO-8601 con zona
)

data class UsuarioDto(
    val id: Long,
    val nombre: String,
    val roles: List<String>,
)

data class EventoDto(
    val id: Long,
    val nombre: String,
    val descripcion: String?,
    val lugar: String,
    val direccion: String?,
    val ciudad: String,              // v2
    val latitud: Double?,            // v2, puede faltar junto con longitud
    val longitud: Double?,           // v2
    val categoria: String?,
    val fechaHoraInicio: String,     // ISO-8601 con zona, p. ej. "2026-09-25T19:00:00-06:00" (TIMESTAMPTZ); ver FechaEvento.iso
    val fechaHoraFin: String?,
    val esDePago: Boolean,
    val requiereAsiento: Boolean,
    val aforo: Int,
    val disponibles: Int,            // calculado en servidor (aforo − boletos vigentes)
)

/** Solicitud de compra completa. `cantidad = 1` representa la compra sencilla. */
data class CrearOrdenDto(
    val eventoId: Long,
    val tipoBoletoId: Long?,
    val cantidad: Int,
    val asientosIds: List<Long>,     // vacío si el evento no requiere asiento
    val metodoPagoId: Long?,         // tarjeta guardada; null para OXXO/SPEI
    val metodo: String,              // "TARJETA" | "OXXO" | "SPEI" | "GRATIS"
)

/** Respuesta: la orden y todos los boletos emitidos (o ninguno si el pago no está aprobado). */
data class OrdenCreadaDto(
    val ordenId: Long,
    val estadoOrden: String,         // PENDIENTE | PAGADA | CANCELADA
    val estadoPago: String?,         // PENDIENTE | APROBADO | RECHAZADO (PENDIENTE requiere ampliar el enum)
    val total: String,               // decimal exacto como texto, p. ej. "360.00"
    val moneda: String,              // "MXN"
    val referenciaPago: String?,     // referencia OXXO / CLABE SPEI cuando aplica
    val boletos: List<BoletoDto>,
)

data class BoletoDto(
    val id: Long,
    val ordenId: Long,
    val eventoId: Long,
    val tipoBoletoId: Long?,
    val asientoId: Long?,
    val codigo: String,
    val firma: String,
    val estado: String,
)

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8080/"

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
