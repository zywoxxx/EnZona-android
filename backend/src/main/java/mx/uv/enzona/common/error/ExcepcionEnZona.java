package mx.uv.enzona.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Error de dominio que el manejador global traduce a `ProblemDetail` (RFC 7807).
 *
 * Códigos HTTP usados: 400 validación, 401, 403, 404, 409 conflicto de inventario
 * o de estado, 422 regla de negocio. `codigo` es un identificador estable para
 * que los clientes reaccionen sin interpretar el texto.
 */
public class ExcepcionEnZona extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    private final Map<String, Object> propiedades = new LinkedHashMap<>();

    public ExcepcionEnZona(HttpStatus estado, String codigo, String detalle) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
    }

    public static ExcepcionEnZona solicitudInvalida(String codigo, String detalle) {
        return new ExcepcionEnZona(HttpStatus.BAD_REQUEST, codigo, detalle);
    }

    public static ExcepcionEnZona noAutenticado(String detalle) {
        return new ExcepcionEnZona(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", detalle);
    }

    public static ExcepcionEnZona prohibido(String detalle) {
        return new ExcepcionEnZona(HttpStatus.FORBIDDEN, "PROHIBIDO", detalle);
    }

    public static ExcepcionEnZona noEncontrado(String detalle) {
        return new ExcepcionEnZona(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", detalle);
    }

    /** 409: el estado actual del recurso o del inventario impide la operación. */
    public static ExcepcionEnZona conflicto(String codigo, String detalle) {
        return new ExcepcionEnZona(HttpStatus.CONFLICT, codigo, detalle);
    }

    /** 422: la solicitud es válida pero incumple una regla de negocio. */
    public static ExcepcionEnZona regla(String codigo, String detalle) {
        return new ExcepcionEnZona(HttpStatus.UNPROCESSABLE_ENTITY, codigo, detalle);
    }

    public ExcepcionEnZona con(String clave, Object valor) {
        if (valor != null) {
            propiedades.put(clave, valor);
        }
        return this;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, Object> getPropiedades() {
        return propiedades;
    }
}
