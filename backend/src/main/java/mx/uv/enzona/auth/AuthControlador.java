package mx.uv.enzona.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.uv.enzona.auth.AuthDtos.LoginDto;
import mx.uv.enzona.auth.AuthDtos.OtpRespuesta;
import mx.uv.enzona.auth.AuthDtos.ReenviarOtpDto;
import mx.uv.enzona.auth.AuthDtos.RegistroDto;
import mx.uv.enzona.auth.AuthDtos.RegistroRespuesta;
import mx.uv.enzona.auth.AuthDtos.SesionDto;
import mx.uv.enzona.auth.AuthDtos.VerificarOtpDto;

/**
 * Acceso público. Las rutas `/verificacion` y `/sesion` son los nombres que usa
 * la tabla E.4 del Anexo E; se conservan como alias de `/otp/verificar` y `/login`.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Acceso", description = "Registro, verificación por código de un solo uso e inicio de sesión")
public class AuthControlador {

    private final AuthServicio servicio;

    public AuthControlador(AuthServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea la cuenta en estado pendiente y genera el código de verificación",
            description = "SIMULACIÓN: sin proveedor de correo/SMS, el código se escribe en el log y, solo en el perfil dev, se devuelve en `otpSimulado`.")
    public RegistroRespuesta registro(@Valid @RequestBody RegistroDto datos) {
        return servicio.registrar(datos);
    }

    @PostMapping({"/otp/verificar", "/verificacion"})
    @Operation(summary = "Activa la cuenta con el código de un solo uso")
    public OtpRespuesta verificar(@Valid @RequestBody VerificarOtpDto datos) {
        return servicio.verificar(datos.usuarioId(), datos.codigo());
    }

    @PostMapping("/otp/reenviar")
    @Operation(summary = "Genera un código nuevo e invalida el anterior")
    public OtpRespuesta reenviar(@Valid @RequestBody ReenviarOtpDto datos) {
        return servicio.reenviar(datos.usuarioId());
    }

    @PostMapping({"/login", "/sesion"})
    @Operation(summary = "Autentica por correo, teléfono o CURP y devuelve el JWT (12 h)")
    public SesionDto login(@Valid @RequestBody LoginDto datos) {
        return servicio.iniciarSesion(datos);
    }
}
