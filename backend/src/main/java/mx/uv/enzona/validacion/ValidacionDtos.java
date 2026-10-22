package mx.uv.enzona.validacion;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ValidacionDtos {

    private ValidacionDtos() {
    }

    /** Motivo detallado de cada intento de acceso (columna `validacion_acceso.motivo`). */
    public enum Motivo {
        PERMITIDO("Acceso permitido"),
        YA_USADO("Boleto ya utilizado"),
        CANCELADO("Boleto cancelado"),
        EVENTO_CANCELADO("Evento cancelado"),
        EXPIRADO("Boleto expirado"),
        FIRMA_INVALIDA("Boleto falsificado"),
        NO_ENCONTRADO("Boleto no encontrado"),
        OTRO_EVENTO("Boleto de otro evento");

        private final String titulo;

        Motivo(String titulo) {
            this.titulo = titulo;
        }

        public String titulo() {
            return titulo;
        }
    }

    public record ValidarDto(
            @NotBlank(message = "Falta el contenido del código QR.") @Size(max = 400, message = "El contenido del QR es demasiado largo.")
            String contenidoQr,
            @NotNull(message = "Indica el evento en el que estás validando.") Long eventoId) {
    }

    @Schema(name = "ResultadoValidacion", description = "Resultado de un escaneo. Un rechazo también responde 200: es un resultado, no un error.")
    public record ResultadoDto(
            boolean permitido,
            @Schema(description = "PERMITIDO | YA_USADO | CANCELADO | EVENTO_CANCELADO | EXPIRADO | FIRMA_INVALIDA | NO_ENCONTRADO | OTRO_EVENTO")
            String resultado,
            String titulo,
            String detalle,
            String codigo,
            String asiento,
            String eventoNombre,
            String tipoNombre,
            Instant fechaUso,
            @Schema(description = "Solo en lotes: el boleto ya había sido admitido por otro escaneo") Boolean duplicada,
            @Schema(description = "Solo en lotes: identificador del escaneo que envió el dispositivo") String claveCliente) {
    }

    public record EscaneoOfflineDto(
            @NotBlank(message = "Falta el contenido del código QR.") @Size(max = 400) String contenidoQr,
            @NotNull(message = "Falta la fecha y hora del escaneo.") OffsetDateTime fechaEscaneo,
            @Schema(description = "Identificador único del escaneo en el dispositivo; reenviar el lote no lo duplica")
            @NotBlank(message = "Falta el identificador del escaneo.") @Size(min = 8, max = 64) String claveCliente) {
    }

    public record LoteDto(
            @NotNull(message = "Indica el evento.") Long eventoId,
            @NotEmpty(message = "El lote no tiene validaciones.") @Size(max = 500, message = "Un lote admite hasta 500 validaciones.")
            @Valid List<EscaneoOfflineDto> validaciones) {
    }

    @Schema(name = "ResultadoLote")
    public record LoteResultadoDto(int recibidas, int permitidas, int rechazadas, int duplicadas,
            List<ResultadoDto> resultados) {
    }

    @Schema(name = "Manifiesto", description = """
            Lista de boletos de un evento para validar sin conexión. Cada boleto se identifica por la huella \
            SHA-256 de `codigo.firma`. La clave HMAC nunca sale del servidor: sin conexión, el dispositivo comprueba \
            que la huella del QR escaneado PERTENECE al manifiesto; no verifica la firma.""")
    public record ManifiestoDto(Long eventoId, String eventoNombre, Instant generado, String algoritmo, int total,
            List<EntradaManifiestoDto> boletos) {
    }

    public record EntradaManifiestoDto(
            @Schema(description = "SHA-256 en hexadecimal de `codigo.firma`") String huella,
            String estado,
            String tipo,
            String asiento,
            @Schema(description = "Últimos 4 caracteres del código, para mostrarlo al validador") String terminacion) {
    }

    @Schema(name = "RegistroValidacion")
    public record RegistroDto(Long id, String codigo, Long eventoId, String eventoNombre, Instant fechaHora,
            Instant fechaEscaneo, String resultado, String motivo, String titulo, boolean offline,
            boolean sincronizado) {
    }
}
