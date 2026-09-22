package com.uv.enzona.data.model

/**
 * Solicitud de compra o confirmación: un evento, un tipo de boleto (si el
 * evento es de pago), una cantidad y, cuando `requiere_asiento`, la lista de
 * asientos elegidos (sin duplicados, uno por boleto).
 *
 * Es un modelo de presentación/transporte: viaja por la navegación y por la
 * API. No corresponde a una tabla nueva; el esquema ya permite N filas de
 * `boleto` con el mismo `orden_id`.
 *
 * `claveOperacion` identifica la operación de forma persistente para que un
 * doble toque, una recomposición o un reintento no emitan dos veces.
 */
data class SolicitudCompra(
    val eventoId: Long,
    val tipoBoletoId: Long?,
    val cantidad: Int,
    val asientosIds: List<Long> = emptyList(),
    val claveOperacion: String = "",
) {
    val requiereAsientos: Boolean get() = asientosIds.isNotEmpty()

    fun conAsientos(ids: Collection<Long>) = copy(asientosIds = ids.distinct())
}

/**
 * Resultado de una compra aprobada o de una confirmación gratuita: la orden,
 * el pago (null en eventos gratuitos) y los N boletos emitidos, en el orden en
 * que se crearon.
 */
data class CompraRealizada(
    val orden: Orden,
    val pago: Pago?,
    val boletos: List<BoletoDetalle>,
) {
    val cantidad: Int get() = boletos.size
    val total: Double get() = orden.total
}

/** Motivos de rechazo de una compra antes de emitir nada. */
sealed class ErrorCompra(mensaje: String) : IllegalStateException(mensaje) {
    class EventoNoDisponible(mensaje: String) : ErrorCompra(mensaje)
    class CantidadInvalida(mensaje: String) : ErrorCompra(mensaje)
    class SinCupo(val disponibles: Int, mensaje: String) : ErrorCompra(mensaje)
    class AsientosInvalidos(mensaje: String) : ErrorCompra(mensaje)
    class AsientosOcupados(val etiquetas: List<String>, mensaje: String) : ErrorCompra(mensaje)
    class PoliticaUsuario(mensaje: String) : ErrorCompra(mensaje)
    class PagoRechazado(mensaje: String) : ErrorCompra(mensaje)
}
