package mx.uv.enzona.notificaciones;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.notificaciones.NotificacionServicio.NotificacionDto;

@RestController
@RequestMapping("/api/usuarios/me/notificaciones")
@Tag(name = "Notificaciones", description = "Avisos de la cuenta: compras, recordatorios, cancelaciones y cambios de fecha")
@SecurityRequirement(name = "bearer")
public class NotificacionControlador {

    private final NotificacionServicio servicio;

    public NotificacionControlador(NotificacionServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Notificaciones de la cuenta, de la más reciente a la más antigua")
    public List<NotificacionDto> listar(@AuthenticationPrincipal UsuarioActual yo,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas) {
        return servicio.listar(yo.id(), soloNoLeidas);
    }

    @PostMapping("/{id}/leida")
    @Operation(summary = "Marca una notificación como leída (404 si no es de la cuenta)")
    public NotificacionDto marcarLeida(@AuthenticationPrincipal UsuarioActual yo, @PathVariable Long id) {
        return servicio.marcarLeida(yo.id(), id);
    }

    @PostMapping("/leidas")
    @Operation(summary = "Marca todas como leídas y devuelve cuántas cambiaron")
    public int marcarTodas(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.marcarTodasLeidas(yo.id());
    }
}
