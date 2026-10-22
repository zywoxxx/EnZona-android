package mx.uv.enzona.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.eventos.EventoConsultas;
import mx.uv.enzona.eventos.EventoDtos.CategoriaDto;
import mx.uv.enzona.eventos.EventoDtos.EventoDto;
import mx.uv.enzona.eventos.EventoServicio;
import mx.uv.enzona.notificaciones.NotificacionServicio;
import mx.uv.enzona.usuarios.EstadoUsuario;
import mx.uv.enzona.usuarios.Rol;
import mx.uv.enzona.usuarios.Usuario;
import mx.uv.enzona.usuarios.UsuarioDtos.CambiarEstadoDto;
import mx.uv.enzona.usuarios.UsuarioDtos.CambiarRolesDto;
import mx.uv.enzona.usuarios.UsuarioDtos.UsuarioDto;
import mx.uv.enzona.usuarios.UsuarioRepositorio;
import mx.uv.enzona.usuarios.UsuarioServicio;

/**
 * Administración (RF-18): cuentas, roles, categorías y supervisión de eventos.
 * La cancelación administrativa usa el endpoint común
 * `POST /api/eventos/{id}/cancelacion` (motivo obligatorio, sin comisión).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administración", description = "Gestión de usuarios, roles, categorías y supervisión de eventos")
@SecurityRequirement(name = "bearer")
public class AdminControlador {

    public record CategoriaEntradaDto(
            @NotBlank(message = "Escribe el nombre de la categoría.") @Size(max = 60, message = "El nombre admite hasta 60 caracteres.")
            String nombre) {
    }

    private final UsuarioRepositorio usuarios;
    private final UsuarioServicio usuarioServicio;
    private final EventoConsultas eventos;
    private final EventoServicio eventoServicio;
    private final NotificacionServicio notificaciones;

    public AdminControlador(UsuarioRepositorio usuarios, UsuarioServicio usuarioServicio, EventoConsultas eventos,
            EventoServicio eventoServicio, NotificacionServicio notificaciones) {
        this.usuarios = usuarios;
        this.usuarioServicio = usuarioServicio;
        this.eventos = eventos;
        this.eventoServicio = eventoServicio;
        this.notificaciones = notificaciones;
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Todas las cuentas (la CURP siempre enmascarada)")
    public List<UsuarioDto> usuarios() {
        return usuarios.findAll().stream().sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(usuarioServicio::aDto).toList();
    }

    @PutMapping("/usuarios/{id}/estado")
    @Operation(summary = "Activa o bloquea una cuenta", description = "Una cuenta bloqueada pierde el acceso de inmediato, aunque su token siga vigente.")
    public UsuarioDto cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoDto datos,
            @AuthenticationPrincipal UsuarioActual yo) {
        if (id.equals(yo.id()) && datos.estado() != EstadoUsuario.ACTIVO) {
            throw ExcepcionEnZona.regla("CUENTA_PROPIA", "No puedes bloquear tu propia cuenta.");
        }
        Usuario u = usuarioServicio.exigir(id);
        u.setEstado(datos.estado());
        return usuarioServicio.aDto(usuarios.save(u));
    }

    @PutMapping("/usuarios/{id}/roles")
    @Operation(summary = "Reemplaza los roles de una cuenta (filas de usuario_rol)")
    public UsuarioDto cambiarRoles(@PathVariable Long id, @Valid @RequestBody CambiarRolesDto datos,
            @AuthenticationPrincipal UsuarioActual yo) {
        if (id.equals(yo.id()) && !datos.roles().contains(Rol.ADMIN)) {
            throw ExcepcionEnZona.regla("CUENTA_PROPIA", "No puedes quitarte a ti mismo el rol de administración.");
        }
        return usuarioServicio.aDto(usuarioServicio.reemplazarRoles(id, datos.roles()));
    }

    @GetMapping("/eventos")
    @Operation(summary = "Todos los eventos, en cualquier estado")
    public List<EventoDto> eventos() {
        return eventos.todos();
    }

    @GetMapping("/categorias")
    @Operation(summary = "Catálogo de categorías")
    public List<CategoriaDto> categorias() {
        return eventoServicio.categorias();
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una categoría (409 si el nombre ya existe)")
    public CategoriaDto crearCategoria(@Valid @RequestBody CategoriaEntradaDto datos) {
        return eventoServicio.crearCategoria(datos.nombre());
    }

    @PostMapping("/recordatorios/generar")
    @Operation(summary = "Ejecuta ahora la tarea diaria de recordatorios",
            description = "Útil para demostraciones y pruebas; la tarea programada hace lo mismo cada día.")
    public Map<String, Integer> generarRecordatorios() {
        return Map.of("generados", notificaciones.generarRecordatorios());
    }
}
