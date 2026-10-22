package mx.uv.enzona.usuarios;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.usuarios.UsuarioDtos.ActualizarPerfilDto;
import mx.uv.enzona.usuarios.UsuarioDtos.CambiarContrasenaDto;
import mx.uv.enzona.usuarios.UsuarioDtos.CargoDto;
import mx.uv.enzona.usuarios.UsuarioDtos.UsuarioDto;

@RestController
@RequestMapping("/api/usuarios/me")
@Tag(name = "Usuarios", description = "Perfil, contraseña y roles de la cuenta autenticada")
@SecurityRequirement(name = "bearer")
public class UsuarioControlador {

    private final UsuarioServicio servicio;

    public UsuarioControlador(UsuarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Cuenta autenticada, con sus roles vigentes")
    public UsuarioDto yo(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.obtener(yo.id());
    }

    @PutMapping
    @Operation(summary = "Actualiza nombre y teléfono")
    public UsuarioDto actualizar(@AuthenticationPrincipal UsuarioActual yo, @Valid @RequestBody ActualizarPerfilDto datos) {
        return servicio.actualizarPerfil(yo.id(), datos);
    }

    @PutMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambia la contraseña (exige la actual)")
    public void cambiarContrasena(@AuthenticationPrincipal UsuarioActual yo, @Valid @RequestBody CambiarContrasenaDto datos) {
        servicio.cambiarContrasena(yo.id(), datos.actual(), datos.nueva());
    }

    @PostMapping("/roles/organizador")
    @Operation(summary = "Añade el rol ORGANIZADOR a la cuenta (fila en usuario_rol)")
    public UsuarioDto convertirseEnOrganizador(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.convertirseEnOrganizador(yo.id());
    }

    @DeleteMapping("/roles/organizador")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Quita el rol ORGANIZADOR",
            description = "409 si tiene eventos publicados con boletos vigentes, si es su único rol o si la cuenta es de administración.")
    public UsuarioDto dejarDeSerOrganizador(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.dejarDeSerOrganizador(yo.id());
    }

    @GetMapping("/roles/organizador/puede-dejar")
    @Operation(summary = "Indica si la cuenta puede dejar de ser organizadora y, si no, el motivo")
    public Map<String, Object> puedeDejar(@AuthenticationPrincipal UsuarioActual yo) {
        String motivo = servicio.motivoNoPuedeDejarOrganizador(yo.id());
        return motivo == null ? Map.of("puede", true) : Map.of("puede", false, "motivo", motivo);
    }

    @GetMapping("/cargos")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    @Operation(summary = "Cargos del organizador (comisiones de cancelación)")
    public List<CargoDto> cargos(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.cargosDe(yo.id());
    }
}
