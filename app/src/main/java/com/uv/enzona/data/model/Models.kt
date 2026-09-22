package com.uv.enzona.data.model

import java.time.LocalDateTime

/**
 * Modelos del dominio de EnZona.
 *
 * Siguen el modelo relacional de `esquema_enzona.sql`:
 *   usuario ─< usuario_rol >─ rol
 *   usuario ─< evento >─ categoria
 *   evento ─< tipo_boleto, evento ─< asiento
 *   usuario ─< orden >─ evento,  orden ─< pago,  orden ─< boleto
 *   boleto ─ tipo_boleto,  boleto ─ asiento,  boleto ─< validacion_acceso
 */

enum class Rol { ASISTENTE, ORGANIZADOR, VALIDADOR, ADMIN }

enum class EstadoUsuario { PENDIENTE, ACTIVO, BLOQUEADO }

enum class EstadoEvento { BORRADOR, PUBLICADO, REALIZADO, CANCELADO }

enum class EstadoOrden { PENDIENTE, PAGADA, CANCELADA, REEMBOLSADA }

enum class EstadoPago { APROBADO, RECHAZADO, REEMBOLSADO }

enum class EstadoBoleto { VALIDO, USADO, CANCELADO, EXPIRADO }

enum class EstadoAsiento { DISPONIBLE, RESERVADO, OCUPADO }

enum class ResultadoAcceso { PERMITIDO, RECHAZADO }

// ---------------------------------------------------------------- usuario / rol

data class Usuario(
    val id: Long,
    val nombre: String,
    val correo: String,
    val telefono: String = "",
    val curp: String,
    val contrasena: String,           // en producción: hash BCrypt en el servidor (RNF-01)
    val roles: Set<Rol> = setOf(Rol.ASISTENTE),
    val estado: EstadoUsuario = EstadoUsuario.PENDIENTE,
    val correoVerificado: Boolean = false,
) {
    val esPersonal: Boolean get() = roles.any { it != Rol.ASISTENTE }

    /** Rol con el que se decide la pantalla principal tras iniciar sesión. */
    val rolPrincipal: Rol
        get() = when {
            Rol.ADMIN in roles -> Rol.ADMIN
            Rol.ORGANIZADOR in roles -> Rol.ORGANIZADOR
            Rol.VALIDADOR in roles -> Rol.VALIDADOR
            else -> Rol.ASISTENTE
        }
}

// ---------------------------------------------------------------- evento

data class Evento(
    val id: Long,
    val organizadorId: Long,
    val nombre: String,
    val descripcion: String,
    val lugar: String,
    val direccion: String,
    val categoria: String,
    val fecha: LocalDateTime,        // fecha_hora_inicio (zona America/Mexico_City); ver util/FechaEvento
    val fechaFin: LocalDateTime? = null,   // fecha_hora_fin, opcional; nunca anterior al inicio
    val esDePago: Boolean,
    val precioDesde: Double,         // 0.0 si es gratuito
    val aforo: Int,
    val disponibles: Int,
    val requiereAsiento: Boolean = false,   // columna requiere_asiento
    val estado: EstadoEvento = EstadoEvento.PUBLICADO,
    val colorSemilla: Int = 0,
    // v2 (esquema_enzona_v2.sql): ciudad del evento y coordenadas opcionales.
    // latitud y longitud pueden faltar juntas; nunca se sustituyen por 0.
    val ciudad: String = "Orizaba",
    val latitud: Double? = null,
    val longitud: Double? = null,
    // v3 (RF-14): registro de la cancelación
    val fechaCancelacion: LocalDateTime? = null,
    val canceladoPor: Long? = null,
    val motivoCancelacion: String? = null,
    val comisionCancelacion: Double = 0.0,
) {
    val tieneCoordenadas: Boolean get() = latitud != null && longitud != null
    val cancelado: Boolean get() = estado == EstadoEvento.CANCELADO
    val vendidos: Int get() = aforo - disponibles
    val agotado: Boolean get() = disponibles <= 0
}

data class TipoBoleto(
    val id: Long,
    val eventoId: Long,
    val nombre: String,              // General, Preferente…
    val precio: Double,
    val cantidadTotal: Int,
    val cantidadDisponible: Int,
)

/** Asiento físico de un evento. Único por (evento, sección, fila, número). */
data class Asiento(
    val id: Long,
    val eventoId: Long,
    val seccion: String,
    val fila: String,                // A, B, C…
    val numero: Int,
    val estado: EstadoAsiento = EstadoAsiento.DISPONIBLE,
) {
    val etiquetaCorta: String get() = "$fila$numero"
    val etiquetaLarga: String get() = "$seccion · Fila $fila · Asiento $numero"
    val libre: Boolean get() = estado == EstadoAsiento.DISPONIBLE
}

/** Distribución de una sección al generar el mapa de asientos. */
data class LayoutSeccion(
    val nombre: String,
    val filas: Int,
    val asientosPorFila: Int,
)

// ---------------------------------------------------------------- orden / pago / boleto

data class Orden(
    val id: Long,
    val asistenteId: Long,
    val eventoId: Long,
    val total: Double,
    val estado: EstadoOrden = EstadoOrden.PENDIENTE,
    val fechaCreacion: String,
)

data class Pago(
    val id: Long,
    val ordenId: Long,
    val monto: Double,
    val moneda: String = "MXN",
    val estado: EstadoPago,
    val referenciaPasarela: String?,
    val fecha: String,
)

/**
 * Boleto electrónico. El contenido del QR es "codigo.qr_firma":
 * el backend firma el código con HMAC-SHA256 (RNF-02) y el validador
 * puede verificar la firma incluso sin conexión (RNF-08).
 */
data class Boleto(
    val id: Long,
    val ordenId: Long,
    val tipoBoletoId: Long?,         // null en eventos gratuitos sin tipos de boleto
    val asientoId: Long?,            // null si el evento no requiere asiento
    val codigo: String,
    val qrFirma: String,
    val estado: EstadoBoleto = EstadoBoleto.VALIDO,
    val fechaEmision: String,
    val fechaUso: String? = null,
) {
    val contenidoQr: String get() = "$codigo.$qrFirma"
}

/**
 * Vista de un boleto con sus relaciones ya resueltas (la "consulta con JOIN"
 * que necesitan las pantallas). No existe como tabla.
 */
data class BoletoDetalle(
    val boleto: Boleto,
    val orden: Orden,
    val evento: Evento,
    val tipoBoleto: TipoBoleto?,
    val asiento: Asiento?,
) {
    val id: Long get() = boleto.id
    val codigo: String get() = boleto.codigo
    val estado: EstadoBoleto get() = boleto.estado
    val fechaUso: String? get() = boleto.fechaUso
    val contenidoQr: String get() = boleto.contenidoQr
    val asistenteId: Long get() = orden.asistenteId
    val nombreTipo: String get() = tipoBoleto?.nombre ?: "Entrada general"
    val etiquetaAsiento: String? get() = asiento?.etiquetaLarga
}

// ---------------------------------------------------------------- métodos de pago

enum class TipoMetodoPago { TARJETA, OXXO, SPEI }

/**
 * Método de pago guardado por el usuario.
 *
 * IMPORTANTE: el Documento de Visión y Alcance establece que «la plataforma no
 * almacena datos de tarjetas». Por eso aquí NO se guarda el número: solo la
 * marca, los últimos cuatro dígitos —que sirven para que la persona reconozca
 * su tarjeta— y el token que devuelve la pasarela, que es lo único con lo que
 * se puede volver a cobrar. El número completo nunca toca la base de datos.
 */
data class MetodoPago(
    val id: Long,
    val usuarioId: Long,
    val tipo: TipoMetodoPago,
    val marca: String,               // Visa, Mastercard, American Express…
    val ultimos4: String,            // "4242"
    val vencimiento: String,         // "12/29"
    val tokenPasarela: String,       // referencia opaca de la pasarela
    val predeterminado: Boolean = false,
) {
    val etiqueta: String
        get() = when (tipo) {
            TipoMetodoPago.TARJETA -> "$marca •••• $ultimos4"
            TipoMetodoPago.OXXO -> "Pago en efectivo en OXXO"
            TipoMetodoPago.SPEI -> "Transferencia SPEI"
        }
}

// ---------------------------------------------------------------- validación

/** Bitácora de accesos; soporta registros hechos sin conexión (RNF-08). */
data class ValidacionAcceso(
    val id: Long,
    val codigoBoleto: String,
    val eventoNombre: String,
    val validadorId: Long,
    val fechaHora: String,
    val resultado: ResultadoAcceso,
    val motivo: String,
    val offline: Boolean = false,
    val sincronizado: Boolean = true,
)

/** Resultado que se muestra en pantalla al escanear un QR. */
data class ResultadoValidacion(
    val permitido: Boolean,
    val titulo: String,
    val detalle: String,
    val codigo: String? = null,
    val asiento: String? = null,
)
