package mx.uv.enzona.common.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;

/**
 * Traduce cualquier error a `application/problem+json` (RFC 7807) con el mismo
 * formato: `status`, `title`, `detail` (texto para mostrar al usuario) y `codigo`.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);
    private static final String BASE_TIPO = "https://enzona.uv.mx/errores/";

    public static ProblemDetail problema(HttpStatus estado, String codigo, String detalle) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setTitle(titulo(estado));
        p.setType(URI.create(BASE_TIPO + codigo.toLowerCase().replace('_', '-')));
        p.setProperty("codigo", codigo);
        return p;
    }

    private static String titulo(HttpStatus estado) {
        return switch (estado) {
            case BAD_REQUEST -> "Solicitud inválida";
            case UNAUTHORIZED -> "No autenticado";
            case FORBIDDEN -> "Sin permiso";
            case NOT_FOUND -> "No encontrado";
            case CONFLICT -> "Conflicto";
            case UNPROCESSABLE_ENTITY -> "Regla de negocio";
            default -> estado.getReasonPhrase();
        };
    }

    @ExceptionHandler(ExcepcionEnZona.class)
    public ResponseEntity<ProblemDetail> dominio(ExcepcionEnZona e) {
        ProblemDetail p = problema(e.getEstado(), e.getCodigo(), e.getMessage());
        e.getPropiedades().forEach(p::setProperty);
        return ResponseEntity.status(e.getEstado()).body(p);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> sinPermiso(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problema(HttpStatus.FORBIDDEN, "PROHIBIDO", "Tu cuenta no tiene permiso para realizar esta acción."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> noAutenticado(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problema(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Inicia sesión para continuar."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> restriccion(ConstraintViolationException e) {
        String detalle = e.getConstraintViolations().stream().map(v -> v.getMessage()).findFirst()
                .orElse("Los datos enviados no son válidos.");
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "VALIDACION", detalle));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> tipoIncorrecto(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "El parámetro «" + e.getName() + "» no tiene un valor válido."));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> faltaCabecera(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Falta la cabecera «" + e.getHeaderName() + "»."));
    }

    /** Red de seguridad: una restricción de la base que la aplicación no anticipó. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integridad(DataIntegrityViolationException e) {
        log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema(HttpStatus.CONFLICT, "CONFLICTO_DE_DATOS",
                "La operación entra en conflicto con el estado actual de los datos. Actualiza e inténtalo de nuevo."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception e) {
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problema(HttpStatus.INTERNAL_SERVER_ERROR,
                "ERROR_INTERNO", "Ocurrió un error inesperado. Inténtalo de nuevo en unos minutos."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> errores.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String detalle = errores.values().stream().findFirst().orElse("Los datos enviados no son válidos.");
        ProblemDetail p = problema(HttpStatus.BAD_REQUEST, "VALIDACION", detalle);
        p.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "El cuerpo de la solicitud no es un JSON válido o tiene campos con formato incorrecto."));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus estado = HttpStatus.resolve(statusCode.value());
        if (body instanceof ProblemDetail p) {
            if (p.getProperties() == null || !p.getProperties().containsKey("codigo")) {
                p.setProperty("codigo", estado != null ? estado.name() : "ERROR");
            }
            if (estado != null) {
                p.setTitle(titulo(estado));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
