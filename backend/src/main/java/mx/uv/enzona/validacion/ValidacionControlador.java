package mx.uv.enzona.validacion;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.eventos.EventoDtos.EventoDto;
import mx.uv.enzona.validacion.ValidacionDtos.LoteDto;
import mx.uv.enzona.validacion.ValidacionDtos.LoteResultadoDto;
import mx.uv.enzona.validacion.ValidacionDtos.ManifiestoDto;
import mx.uv.enzona.validacion.ValidacionDtos.RegistroDto;
import mx.uv.enzona.validacion.ValidacionDtos.ResultadoDto;
import mx.uv.enzona.validacion.ValidacionDtos.ValidarDto;

@RestController
@RequestMapping("/api")
@Tag(name = "Validación de acceso", description = "Escaneo del QR en la puerta, manifiesto y sincronización sin conexión")
@SecurityRequirement(name = "bearer")
public class ValidacionControlador {

    private static final String PERSONAL = "hasAnyRole('VALIDADOR','ORGANIZADOR','ADMIN')";

    private final ValidacionServicio servicio;

    public ValidacionControlador(ValidacionServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/validaciones")
    @PreAuthorize(PERSONAL)
    @Operation(summary = "Valida un boleto y lo marca como usado de forma atómica",
            description = "Verifica la firma antes de consultar la base. Solo el primer escaneo de un boleto lo admite.")
    public ResultadoDto validar(@Valid @RequestBody ValidarDto datos, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.validar(yo, datos.contenidoQr(), datos.eventoId());
    }

    @PostMapping({"/validaciones/lote", "/validaciones/sincronizar"})
    @PreAuthorize(PERSONAL)
    @Operation(summary = "Sube las validaciones hechas sin conexión y las reconcilia",
            description = "Aplica la misma regla de un solo uso y señala cuáles resultaron duplicadas. "
                    + "`/validaciones/sincronizar` es el nombre que usa el Anexo E.")
    public LoteResultadoDto lote(@Valid @RequestBody LoteDto lote, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.sincronizar(yo, lote);
    }

    @GetMapping("/validaciones")
    @PreAuthorize(PERSONAL)
    @Operation(summary = "Últimos accesos registrados por la cuenta autenticada")
    public List<RegistroDto> bitacora(@RequestParam(required = false) Long eventoId,
            @RequestParam(defaultValue = "20") int limite, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.bitacora(yo, eventoId, limite);
    }

    @GetMapping("/validaciones/eventos")
    @PreAuthorize(PERSONAL)
    @Operation(summary = "Eventos en los que la cuenta está designada como personal de acceso")
    public List<EventoDto> eventos(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.eventosAsignados(yo);
    }

    @GetMapping("/eventos/{id}/manifiesto")
    @PreAuthorize(PERSONAL)
    @Operation(summary = "Manifiesto del evento para validar sin conexión",
            description = "Huellas SHA-256 de `codigo.firma`. La verificación sin conexión es por pertenencia al manifiesto, no por firma.")
    public ManifiestoDto manifiesto(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.manifiesto(id, yo);
    }
}
