package mx.uv.enzona.usuarios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos JSON del módulo de usuarios. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    @Schema(description = "Cuenta de usuario. La CURP nunca se devuelve completa.")
    public record UsuarioDto(
            Long id,
            String nombre,
            String correo,
            String telefono,
            @Schema(description = "CURP enmascarada: 4 primeros y 2 últimos caracteres", example = "ORGA············04")
            String curpEnmascarada,
            List<String> roles,
            String estado,
            boolean correoVerificado,
            boolean telefonoVerificado,
            Instant fechaRegistro) {
    }

    public record ActualizarPerfilDto(
            @NotBlank(message = "Escribe tu nombre.") @Size(max = 120, message = "El nombre admite hasta 120 caracteres.")
            String nombre,
            @Pattern(regexp = "^$|^\\d{10}$", message = "El teléfono debe tener 10 dígitos.")
            String telefono) {
    }

    public record CambiarContrasenaDto(
            @NotBlank(message = "Escribe tu contraseña actual.") String actual,
            @NotBlank(message = "Escribe la nueva contraseña.") String nueva) {
    }

    public record CargoDto(Long id, Long eventoId, String concepto, String importe, boolean pagado, Instant fecha) {

        public static CargoDto de(Long id, Long eventoId, String concepto, BigDecimal importe, boolean pagado, Instant fecha) {
            return new CargoDto(id, eventoId, concepto, importe.toPlainString(), pagado, fecha);
        }
    }

    // ---------------- administración ----------------

    public record CambiarEstadoDto(@NotNull(message = "Indica el estado.") EstadoUsuario estado) {
    }

    public record CambiarRolesDto(@NotEmpty(message = "Una cuenta necesita al menos un rol.") List<Rol> roles) {
    }
}
