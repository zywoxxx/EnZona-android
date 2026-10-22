package mx.uv.enzona.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos JSON del módulo de eventos. Las fechas viajan en ISO-8601 con zona. */
public final class EventoDtos {

    private EventoDtos() {
    }

    @Schema(name = "Evento")
    public record EventoDto(
            Long id,
            String nombre,
            String descripcion,
            String lugar,
            String direccion,
            String ciudad,
            @Schema(description = "Puede faltar junto con longitud; nunca se rellena con 0") Double latitud,
            Double longitud,
            String categoria,
            Integer categoriaId,
            Instant fechaHoraInicio,
            Instant fechaHoraFin,
            boolean esDePago,
            boolean requiereAsiento,
            int aforo,
            @Schema(description = "Lugares que se pueden pedir ahora: inventario de los tipos y, si hay plano, asientos libres")
            int disponibles,
            @Schema(description = "Boletos vigentes (válidos o usados)") int vendidos,
            String estado,
            Long organizadorId,
            String organizadorNombre,
            @Schema(description = "Precio más bajo entre los tipos de boleto; \"0.00\" si es gratuito") String precioDesde,
            @Schema(description = "Distancia en km al punto enviado en lat/lng; ausente si no se envió o el evento no tiene coordenadas")
            Double distanciaKm,
            Instant fechaCreacion,
            boolean tieneBoletos,
            int asientosTotales,
            Instant fechaCancelacion,
            Long canceladoPor,
            String canceladoPorNombre,
            String motivoCancelacion,
            String comisionCancelacion) {
    }

    @Schema(name = "TipoBoleto")
    public record TipoBoletoDto(Long id, Long eventoId, String nombre, String precio, int cantidadTotal,
            int cantidadDisponible) {

        public static TipoBoletoDto de(TipoBoleto t) {
            return new TipoBoletoDto(t.getId(), t.getEventoId(), t.getNombre(), t.getPrecio().toPlainString(),
                    t.getCantidadTotal(), t.getCantidadDisponible());
        }
    }

    @Schema(name = "Asiento", description = "Estados reales del esquema: DISPONIBLE (libre), RESERVADO y OCUPADO")
    public record AsientoDto(Long id, Long eventoId, String seccion, String fila, String numero, String estado) {

        public static AsientoDto de(Asiento a) {
            return new AsientoDto(a.getId(), a.getEventoId(), a.getSeccion(), a.getFila(), a.getNumero(),
                    a.getEstado().name());
        }
    }

    @Schema(name = "Categoria")
    public record CategoriaDto(Integer id, String nombre) {
    }

    public record SeccionDto(
            @NotBlank(message = "Escribe el nombre de la sección.") @Size(max = 40, message = "La sección admite hasta 40 caracteres.")
            String nombre,
            @Min(value = 1, message = "Indica al menos una fila.") @Max(value = 26, message = "Máximo 26 filas (A–Z).")
            int filas,
            @Min(value = 1, message = "Indica al menos un asiento por fila.") @Max(value = 40, message = "Máximo 40 asientos por fila.")
            int asientosPorFila) {
    }

    public record TipoEntradaDto(
            @NotBlank(message = "Escribe el nombre del tipo de boleto.") @Size(max = 80) String nombre,
            @NotNull(message = "Indica el precio.") @DecimalMin(value = "0.00", message = "El precio no puede ser negativo.")
            BigDecimal precio,
            @Min(value = 1, message = "La cantidad debe ser mayor que cero.") int cantidad) {
    }

    /** Alta y edición de evento. En la edición se ignoran `esDePago`, `tipos` y `publicar`. */
    @Schema(name = "EventoEntrada")
    public record EventoEntradaDto(
            @NotBlank(message = "Escribe el nombre del evento.") @Size(max = 160, message = "El nombre admite hasta 160 caracteres.")
            String nombre,
            String descripcion,
            @NotBlank(message = "Escribe el lugar del evento.") @Size(max = 160, message = "El lugar admite hasta 160 caracteres.")
            String lugar,
            @Size(max = 240, message = "La dirección admite hasta 240 caracteres.") String direccion,
            @Size(max = 80) String ciudad,
            @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90.")
            @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90.") Double latitud,
            @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180.")
            @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180.") Double longitud,
            @Schema(description = "Nombre de la categoría (catálogo)") String categoria,
            Integer categoriaId,
            @NotNull(message = "Elige la fecha y la hora de inicio.") OffsetDateTime fechaHoraInicio,
            OffsetDateTime fechaHoraFin,
            Boolean esDePago,
            @Schema(description = "Precio único cuando no se envían `tipos`") BigDecimal precio,
            @Schema(description = "Se ignora si el evento tiene plano de asientos") Integer aforo,
            Boolean requiereAsiento,
            @Valid List<SeccionDto> secciones,
            @Valid List<TipoEntradaDto> tipos,
            @Schema(description = "true para crear y publicar en un paso") Boolean publicar) {
    }

    public record AsientosEntradaDto(
            @NotNull(message = "Indica si el evento requiere asientos.") Boolean requiereAsiento,
            @Valid List<SeccionDto> secciones) {
    }

    public record DesignarPersonalDto(
            @NotBlank(message = "Escribe el correo de la persona.") @Email(message = "El correo no tiene un formato válido.")
            String correo) {
    }

    @Schema(name = "PersonalAcceso")
    public record PersonalDto(Long usuarioId, String nombre, String correo, Instant designado) {
    }

    // ---------------- cancelación (mismos nombres que ApiService.kt de la app) ----------------

    @Schema(name = "PoliticaCancelacion")
    public record PoliticaCancelacionDto(
            boolean permitida,
            @Schema(description = "BORRADOR | GRATUITO | DE_PAGO_SIN_VENTAS | DE_PAGO_CON_COMISION | ADMINISTRATIVA")
            String tipo,
            String importeCobrado,
            String comision,
            String reembolso,
            int boletosAfectados,
            String motivoBloqueo) {
    }

    public record CancelacionDto(
            @Size(max = 255, message = "El motivo admite hasta 255 caracteres.") String motivo,
            boolean aceptaComision) {
    }

    @Schema(name = "CancelacionResultado")
    public record CancelacionResultadoDto(
            Long eventoId,
            String importeReembolsado,
            String comision,
            int boletosAnulados,
            int asistentesNotificados,
            Long canceladoPor,
            Instant fechaCancelacion) {
    }

    @Schema(name = "ReporteEvento")
    public record ReporteDto(
            Long eventoId,
            int boletosEmitidos,
            int boletosValidos,
            int boletosUsados,
            int boletosCancelados,
            String ingresos,
            int accesosPermitidos,
            int accesosRechazados,
            List<ReporteTipoDto> porTipo) {
    }

    public record ReporteTipoDto(Long tipoBoletoId, String nombre, String precio, int vendidos, int disponibles) {
    }
}
