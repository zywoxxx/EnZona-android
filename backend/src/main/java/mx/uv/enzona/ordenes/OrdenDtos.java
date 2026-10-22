package mx.uv.enzona.ordenes;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import mx.uv.enzona.boletos.BoletoDtos.BoletoDto;

/** Contratos JSON de órdenes, pagos y métodos de pago. Mismos nombres que `ApiService.kt` de la app. */
public final class OrdenDtos {

    private OrdenDtos() {
    }

    @Schema(name = "CrearOrden", description = "Compra o confirmación de N boletos. `cantidad = 1` es la compra sencilla.")
    public record CrearOrdenDto(
            @NotNull(message = "Indica el evento.") Long eventoId,
            @Schema(description = "Obligatorio en eventos de pago; en los gratuitos puede omitirse") Long tipoBoletoId,
            @Min(value = 1, message = "Elige al menos un boleto.") int cantidad,
            @Schema(description = "Vacío si el evento no requiere asiento; si lo requiere, uno por boleto y sin repetir")
            List<Long> asientosIds,
            @Schema(description = "Tarjeta guardada; null para OXXO, SPEI o tarjeta con token de un solo uso") Long metodoPagoId,
            @Schema(description = "TARJETA | OXXO | SPEI | GRATIS") String metodo,
            @Schema(description = "Token de la pasarela cuando no se usa una tarjeta guardada. En la pasarela SIMULADA, "
                    + "un token terminado en `-rechazo` produce un pago rechazado.")
            String tokenPasarela) {
    }

    @Schema(name = "OrdenCreada", description = "La orden y todos sus boletos (ninguno si el pago no está aprobado)")
    public record OrdenCreadaDto(
            Long ordenId,
            Long eventoId,
            Long asistenteId,
            @Schema(description = "PENDIENTE | PAGADA | CANCELADA | REEMBOLSADA") String estadoOrden,
            @Schema(description = "PENDIENTE | APROBADO | RECHAZADO | REEMBOLSADO; null en órdenes sin cobro") String estadoPago,
            @Schema(description = "Importe exacto como texto", example = "360.00") String total,
            String moneda,
            @Schema(description = "Referencia OXXO o CLABE SPEI cuando el pago queda pendiente") String referenciaPago,
            Long pagoId,
            String medioPago,
            Instant fechaCreacion,
            @Schema(description = "true: la pasarela es simulada y no hubo movimiento real de dinero") boolean pagoSimulado,
            List<BoletoDto> boletos) {
    }

    @Schema(name = "MetodoPago", description = "Nunca incluye el número de tarjeta ni el CVV")
    public record MetodoPagoDto(Long id, String tipo, String marca, String ultimos4, String vencimiento,
            boolean predeterminado, Instant fechaRegistro) {

        public static MetodoPagoDto de(MetodoPago m) {
            return new MetodoPagoDto(m.getId(), m.getTipo().name(), m.getMarca(),
                    m.getUltimos4() == null ? null : m.getUltimos4().trim(),
                    m.getVencimiento() == null ? null : m.getVencimiento().trim(), m.isPredeterminado(),
                    m.getFechaRegistro());
        }
    }
}
