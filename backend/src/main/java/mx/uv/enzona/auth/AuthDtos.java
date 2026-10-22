package mx.uv.enzona.auth;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.uv.enzona.usuarios.UsuarioDtos.UsuarioDto;

/** Contratos JSON del acceso: registro, verificación por OTP e inicio de sesión. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Mismo formato que `util/Validaciones.kt` de la app: 4 letras, 6 dígitos, H/M, 5 letras, homoclave y dígito. */
    public static final String PATRON_CURP = "^[A-Za-z]{4}\\d{6}[HMhm][A-Za-z]{5}[A-Za-z0-9]\\d$";

    public record RegistroDto(
            @NotBlank(message = "Escribe tu nombre completo.") @Size(max = 120, message = "El nombre admite hasta 120 caracteres.")
            String nombre,
            @NotBlank(message = "Escribe tu correo.") @Email(regexp = "^[\\w.+-]+@[\\w-]+\\.[\\w.-]{2,}$", message = "El correo no tiene un formato válido.")
            @Size(max = 180, message = "El correo admite hasta 180 caracteres.")
            String correo,
            @Pattern(regexp = "^$|^\\d{10}$", message = "El teléfono debe tener 10 dígitos.")
            String telefono,
            @NotBlank(message = "Escribe tu CURP.") @Pattern(regexp = PATRON_CURP, message = "La CURP no tiene un formato válido (18 caracteres).")
            String curp,
            @NotBlank(message = "Escribe una contraseña.")
            String contrasena) {
    }

    @Schema(description = "Resultado del registro. `otpSimulado` solo aparece en el perfil dev: no hay proveedor de correo/SMS.")
    public record RegistroRespuesta(
            Long usuarioId,
            String correo,
            String estado,
            @Schema(description = "SIMULACIÓN. Código de verificación; solo se devuelve en el perfil dev.")
            String otpSimulado,
            boolean simulacion,
            String aviso) {
    }

    public record VerificarOtpDto(
            @NotNull(message = "Falta el identificador de la cuenta.") Long usuarioId,
            @NotBlank(message = "Escribe el código de verificación.") @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos.")
            String codigo) {
    }

    public record ReenviarOtpDto(@NotNull(message = "Falta el identificador de la cuenta.") Long usuarioId) {
    }

    public record OtpRespuesta(
            boolean verificado,
            @Schema(description = "SIMULACIÓN. Solo en el perfil dev, al reenviar el código.") String otpSimulado,
            boolean simulacion,
            String aviso) {
    }

    public record LoginDto(
            @Schema(description = "Correo, teléfono (10 dígitos) o CURP")
            @NotBlank(message = "Escribe tu correo, teléfono o CURP.") String identificador,
            @NotBlank(message = "Escribe tu contraseña.") String contrasena,
            @Schema(description = "true cuando se entra por el acceso de organizadores y personal")
            Boolean accesoPersonal) {
    }

    public record SesionDto(String token, String tipo, Instant expira, UsuarioDto usuario) {
    }
}
