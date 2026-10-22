package mx.uv.enzona.boletos;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mx.uv.enzona.boletos.BoletoDtos.BoletoDto;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.eventos.EventoServicio;

@RestController
@RequestMapping("/api")
@Tag(name = "Boletos", description = "Boletos electrónicos con su código QR firmado")
@SecurityRequirement(name = "bearer")
public class BoletoControlador {

    private final BoletoServicio servicio;
    private final EventoServicio eventos;

    public BoletoControlador(BoletoServicio servicio, EventoServicio eventos) {
        this.servicio = servicio;
        this.eventos = eventos;
    }

    @GetMapping("/boletos")
    @Operation(summary = "Boletos de la cuenta autenticada, con evento, tipo y asiento resueltos")
    public List<BoletoDto> mios(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.deUsuario(yo.id());
    }

    @GetMapping("/boletos/{id}")
    @Operation(summary = "Un boleto (su titular, el organizador del evento o un administrador)")
    public BoletoDto obtener(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.obtener(id, yo);
    }

    @GetMapping("/eventos/{id}/boletos")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    @Operation(summary = "Boletos emitidos de un evento, para el panel de su organizador")
    public List<BoletoDto> deEvento(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        eventos.exigirGestion(eventos.exigir(id), yo);
        return servicio.deEvento(id).stream()
                // El organizador ve el estado y el tipo, no el contenido del QR de sus asistentes
                .map(b -> new BoletoDto(b.id(), b.ordenId(), b.eventoId(), b.tipoBoletoId(), b.asientoId(), b.codigo(),
                        null, null, b.estado(), b.fechaEmision(), b.fechaUso(), b.asistenteId(), b.tipoNombre(),
                        b.precio(), b.posicion(), b.totalEnOrden(), b.totalOrden(), b.estadoOrden(), b.fechaOrden(),
                        b.asiento(), null))
                .toList();
    }
}
