package mx.uv.enzona.eventos;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.eventos.EventoDtos.AsientoDto;
import mx.uv.enzona.eventos.EventoDtos.AsientosEntradaDto;
import mx.uv.enzona.eventos.EventoDtos.CancelacionDto;
import mx.uv.enzona.eventos.EventoDtos.CancelacionResultadoDto;
import mx.uv.enzona.eventos.EventoDtos.CategoriaDto;
import mx.uv.enzona.eventos.EventoDtos.DesignarPersonalDto;
import mx.uv.enzona.eventos.EventoDtos.EventoDto;
import mx.uv.enzona.eventos.EventoDtos.EventoEntradaDto;
import mx.uv.enzona.eventos.EventoDtos.PersonalDto;
import mx.uv.enzona.eventos.EventoDtos.PoliticaCancelacionDto;
import mx.uv.enzona.eventos.EventoDtos.ReporteDto;
import mx.uv.enzona.eventos.EventoDtos.TipoBoletoDto;

@RestController
@RequestMapping("/api")
@Tag(name = "Eventos", description = "Catálogo, ciclo de vida del evento, asientos, personal de acceso y cancelación")
public class EventoControlador {

    private static final String GESTION = "hasAnyRole('ORGANIZADOR','ADMIN')";

    private final EventoServicio servicio;
    private final CancelacionServicio cancelaciones;

    public EventoControlador(EventoServicio servicio, CancelacionServicio cancelaciones) {
        this.servicio = servicio;
        this.cancelaciones = cancelaciones;
    }

    // ---------------- catálogo (público) ----------------

    @GetMapping("/eventos")
    @Operation(summary = "Eventos publicados, con búsqueda, filtros y orden por distancia",
            description = "Con `lat` y `lng` la lista se ordena por distancia real (Haversine) y cada evento lleva `distanciaKm`. "
                    + "Los eventos sin coordenadas van al final, sin distancia. Sin `lat`/`lng` se ordena por fecha.")
    public List<EventoDto> catalogo(
            @RequestParam(required = false) String ciudad,
            @Parameter(description = "Nombre o id de la categoría") @RequestParam(required = false) String categoria,
            @Parameter(description = "ISO-8601 con zona o fecha AAAA-MM-DD") @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radioKm,
            @Parameter(description = "Texto a buscar en nombre, lugar, dirección y categoría") @RequestParam(required = false) String q,
            @Parameter(description = "GRATIS o DE_PAGO") @RequestParam(required = false) String precio,
            @RequestParam(required = false) String lugar,
            @RequestParam(required = false) Integer limite) {
        return servicio.catalogo(ciudad, categoria, desde, hasta, lat, lng, radioKm, q, precio, lugar, limite);
    }

    @GetMapping("/eventos/{id}")
    @Operation(summary = "Detalle del evento (los borradores solo los ve su organizador o un administrador)")
    public EventoDto detalle(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.detalle(id, yo);
    }

    @GetMapping("/eventos/{id}/tipos")
    @Operation(summary = "Tipos de boleto del evento con su precio y disponibilidad")
    public List<TipoBoletoDto> tipos(@PathVariable Long id) {
        return servicio.tiposDe(id);
    }

    @GetMapping("/eventos/{id}/asientos")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Plano de butacas con su estado (DISPONIBLE, RESERVADO u OCUPADO)")
    public List<AsientoDto> asientos(@PathVariable Long id) {
        return servicio.asientosDe(id);
    }

    @GetMapping("/categorias")
    @Operation(summary = "Catálogo de categorías")
    public List<CategoriaDto> categorias() {
        return servicio.categorias();
    }

    // ---------------- organizador ----------------

    @GetMapping("/eventos/mios")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Eventos del organizador autenticado, en cualquier estado")
    public List<EventoDto> mios(@AuthenticationPrincipal UsuarioActual yo) {
        return servicio.misEventos(yo.id());
    }

    @PostMapping("/eventos")
    @PreAuthorize(GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Crea un evento en borrador (o lo publica si `publicar` es true)")
    public EventoDto crear(@Valid @RequestBody EventoEntradaDto datos, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.crear(datos, yo);
    }

    @PutMapping("/eventos/{id}")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Modifica el evento aplicando las políticas de negocio",
            description = "422 si el aforo queda por debajo de lo ya entregado. Si cambia la fecha se avisa a quien tiene boleto.")
    public EventoDto modificar(@PathVariable Long id, @Valid @RequestBody EventoEntradaDto datos,
            @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.modificar(id, datos, yo);
    }

    @PostMapping("/eventos/{id}/publicar")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Publica un borrador: exige coordenadas, fecha futura y al menos un tipo de boleto")
    public EventoDto publicar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.publicar(id, yo);
    }

    @PostMapping("/eventos/{id}/realizado")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Marca como finalizado un evento publicado que ya comenzó")
    public EventoDto realizado(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.marcarRealizado(id, yo);
    }

    @PutMapping("/eventos/{id}/asientos")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Activa, cambia o desactiva el plano de asientos (409 si ya hay boletos emitidos)")
    public EventoDto cambiarAsientos(@PathVariable Long id, @Valid @RequestBody AsientosEntradaDto datos,
            @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.cambiarAsientos(id, datos, yo);
    }

    @GetMapping("/eventos/{id}/personal-acceso")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Personal de acceso designado para el evento")
    public List<PersonalDto> personal(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.personalDe(id, yo);
    }

    @PostMapping("/eventos/{id}/personal-acceso")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Designa como validador a una cuenta ya registrada, buscándola por correo")
    public List<PersonalDto> designar(@PathVariable Long id, @Valid @RequestBody DesignarPersonalDto datos,
            @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.designar(id, datos.correo(), yo);
    }

    @DeleteMapping("/eventos/{id}/personal-acceso/{usuarioId}")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Retira a una persona del personal de acceso del evento")
    public List<PersonalDto> retirar(@PathVariable Long id, @PathVariable Long usuarioId,
            @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.retirar(id, usuarioId, yo);
    }

    @GetMapping("/eventos/{id}/reportes")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Conteo de boletos, ingresos y accesos validados")
    public ReporteDto reportes(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return servicio.reporte(id, yo);
    }

    // ---------------- cancelación (RF-14) ----------------

    @GetMapping("/eventos/{id}/politica-cancelacion")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Política de cancelación calculada en servidor",
            description = "Es administrativa cuando la consulta un administrador que no organiza el evento.")
    public PoliticaCancelacionDto politica(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return cancelaciones.politica(id, yo).aDto();
    }

    @PostMapping("/eventos/{id}/cancelacion")
    @PreAuthorize(GESTION)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Cancela el evento, anula boletos y registra reembolsos",
            description = "409 evento iniciado, finalizado o ya cancelado; 422 comisión no aceptada o motivo ausente en "
                    + "cancelación administrativa; 403 rol sin permiso. Los reembolsos son de la pasarela SIMULADA.")
    public CancelacionResultadoDto cancelar(@PathVariable Long id, @Valid @RequestBody CancelacionDto solicitud,
            @AuthenticationPrincipal UsuarioActual yo) {
        return cancelaciones.cancelar(id, solicitud, yo);
    }
}
