package mx.uv.enzona.boletos;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import mx.uv.enzona.eventos.EventoDtos.AsientoDto;
import mx.uv.enzona.eventos.EventoDtos.EventoDto;

public final class BoletoDtos {

    private BoletoDtos() {
    }

    /**
     * Boleto con sus relaciones ya resueltas: es el `BoletoDetalle` de la app
     * (boleto + orden + evento + tipo + asiento) en una sola respuesta.
     */
    @Schema(name = "Boleto")
    public record BoletoDto(
            Long id,
            Long ordenId,
            Long eventoId,
            Long tipoBoletoId,
            Long asientoId,
            String codigo,
            @Schema(description = "Firma HMAC-SHA256 del código (32 caracteres hexadecimales)") String firma,
            @Schema(description = "Contenido del QR: `codigo.firma`") String contenidoQr,
            @Schema(description = "VALIDO | USADO | CANCELADO | EXPIRADO") String estado,
            Instant fechaEmision,
            Instant fechaUso,
            Long asistenteId,
            String tipoNombre,
            String precio,
            @Schema(description = "Posición del boleto dentro de su orden (1..N)") int posicion,
            @Schema(description = "Boletos de la orden") int totalEnOrden,
            String totalOrden,
            String estadoOrden,
            Instant fechaOrden,
            AsientoDto asiento,
            EventoDto evento) {
    }
}
