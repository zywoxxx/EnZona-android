package mx.uv.enzona.ordenes;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.ordenes.OrdenDtos.CrearOrdenDto;
import mx.uv.enzona.ordenes.OrdenDtos.MetodoPagoDto;
import mx.uv.enzona.ordenes.OrdenDtos.OrdenCreadaDto;

@RestController
@RequestMapping("/api")
@Tag(name = "Órdenes y pagos", description = "Compra de N boletos, pagos (pasarela SIMULADA) y métodos de pago guardados")
@SecurityRequirement(name = "bearer")
public class OrdenControlador {

    private final OrdenServicio ordenes;
    private final MetodoPagoServicio metodos;

    public OrdenControlador(OrdenServicio ordenes, MetodoPagoServicio metodos) {
        this.ordenes = ordenes;
        this.metodos = metodos;
    }

    @PostMapping("/ordenes")
    @Operation(summary = "Compra o confirmación de N boletos en una sola orden (todo o nada)",
            description = """
                    El precio y el total se derivan de `tipo_boleto`. El inventario se descuenta por lote con UPDATE \
                    condicional y los asientos se toman solo si siguen disponibles; si falta uno solo, no se emite nada.

                    201 orden creada · 200 la clave `Idempotency-Key` ya se había procesado (misma orden, sin cobrar ni \
                    emitir de nuevo) · 409 sin cupo, asientos ocupados o evento no disponible · 422 pago rechazado o regla \
                    de negocio.

                    La pasarela es SIMULADA: no hay cargos reales. Una tarjeta cuyo token termina en `-rechazo` se rechaza; \
                    OXXO y SPEI quedan PENDIENTE y los boletos se emiten al confirmar el pago.""")
    public ResponseEntity<OrdenCreadaDto> crear(
            @Parameter(description = "Identificador de la operación generado por el cliente (8 a 64 caracteres)")
            @RequestHeader("Idempotency-Key") String claveOperacion,
            @Valid @RequestBody CrearOrdenDto solicitud,
            @AuthenticationPrincipal UsuarioActual yo) {
        OrdenServicio.Creacion c = ordenes.crear(yo, claveOperacion, solicitud);
        return c.nueva() ? ResponseEntity.created(URI.create("/api/ordenes/" + c.orden().ordenId())).body(c.orden())
                : ResponseEntity.ok(c.orden());
    }

    @GetMapping("/ordenes")
    @Operation(summary = "Órdenes de la cuenta autenticada")
    public List<OrdenCreadaDto> mias(@AuthenticationPrincipal UsuarioActual yo) {
        return ordenes.deUsuario(yo.id());
    }

    @GetMapping("/ordenes/{id}")
    @Operation(summary = "Una orden con todos sus boletos")
    public OrdenCreadaDto obtener(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return ordenes.obtener(id, yo);
    }

    @PostMapping("/pagos/{id}/confirmar")
    @Operation(summary = "SIMULACIÓN: confirma un pago pendiente (OXXO/SPEI) y emite los boletos",
            description = "Sustituye al aviso (webhook) de la pasarela mientras no hay proveedor. Disponible para el titular "
                    + "de la orden solo en el perfil dev, y para un administrador en cualquier perfil.")
    public OrdenCreadaDto confirmarPago(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return ordenes.confirmarPago(id, yo);
    }

    // ---------------- métodos de pago guardados ----------------

    @GetMapping("/usuarios/me/metodos-pago")
    @Operation(summary = "Tarjetas guardadas (marca, últimos 4 y vencimiento; nunca el número)")
    public List<MetodoPagoDto> metodos(@AuthenticationPrincipal UsuarioActual yo) {
        return metodos.listar(yo.id());
    }

    @PostMapping("/usuarios/me/metodos-pago")
    @Operation(summary = "Guarda una tarjeta ya tokenizada por la pasarela",
            description = "Campos admitidos: `marca`, `ultimos4`, `vencimiento` (MM/AA), `tokenPasarela` y `predeterminado`. "
                    + "400 si llega cualquier campo que parezca un número de tarjeta (PAN) o un código de seguridad (CVV).")
    public ResponseEntity<MetodoPagoDto> agregar(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "marca, ultimos4, vencimiento, tokenPasarela, predeterminado")
            @RequestBody Map<String, Object> cuerpo,
            @AuthenticationPrincipal UsuarioActual yo) {
        MetodoPagoDto creado = metodos.agregar(yo.id(), cuerpo);
        return ResponseEntity.created(URI.create("/api/usuarios/me/metodos-pago/" + creado.id())).body(creado);
    }

    @DeleteMapping("/usuarios/me/metodos-pago/{id}")
    @Operation(summary = "Elimina una tarjeta guardada y devuelve las que quedan")
    public List<MetodoPagoDto> eliminar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return metodos.eliminar(yo.id(), id);
    }

    @PostMapping("/usuarios/me/metodos-pago/{id}/predeterminado")
    @Operation(summary = "Marca una tarjeta como predeterminada")
    public List<MetodoPagoDto> predeterminado(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual yo) {
        return metodos.marcarPredeterminado(yo.id(), id);
    }
}
