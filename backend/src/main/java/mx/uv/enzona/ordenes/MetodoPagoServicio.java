package mx.uv.enzona.ordenes;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.ordenes.OrdenDtos.MetodoPagoDto;

/**
 * Métodos de pago guardados. La plataforma NO almacena datos de tarjeta
 * (Visión y Alcance): el servidor solo recibe marca, últimos cuatro dígitos,
 * vencimiento y el token de la pasarela, y rechaza cualquier campo que parezca
 * un número de tarjeta (PAN) o un código de seguridad (CVV).
 */
@Service
public class MetodoPagoServicio {

    public interface MetodoPagoRepositorio extends JpaRepository<MetodoPago, Long> {

        List<MetodoPago> findByUsuarioIdOrderByIdAsc(Long usuarioId);

        Optional<MetodoPago> findByIdAndUsuarioId(Long id, Long usuarioId);
    }

    private static final Set<String> CAMPOS_PERMITIDOS = Set.of("tipo", "marca", "ultimos4", "vencimiento",
            "tokenPasarela", "predeterminado");
    private static final Pattern CAMPO_SENSIBLE = Pattern.compile(
            "(?i).*(numero|número|number|pan|tarjeta|card|cvv|cvc|cv2|cid|csc|codigoseguridad|securitycode|nip|pin).*");
    private static final Pattern VENCIMIENTO = Pattern.compile("^(0[1-9]|1[0-2])/\\d{2}$");
    private static final Pattern ULTIMOS4 = Pattern.compile("^\\d{4}$");
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_\\-.:]{6,180}$");

    private final MetodoPagoRepositorio metodos;
    private final Clock reloj;

    public MetodoPagoServicio(MetodoPagoRepositorio metodos, Clock reloj) {
        this.metodos = metodos;
        this.reloj = reloj;
    }

    /** ¿El texto parece un número de tarjeta? 13 a 19 dígitos, con o sin espacios o guiones. */
    static boolean parecePan(String texto) {
        if (texto == null) {
            return false;
        }
        String compacto = texto.replaceAll("[\\s-]", "");
        return compacto.matches("\\d{13,19}");
    }

    /**
     * Valida el cuerpo recibido campo por campo. Se trabaja sobre el mapa crudo
     * (no sobre un DTO) para detectar campos que no deberían llegar nunca.
     */
    static void rechazarDatosDeTarjeta(Map<String, Object> cuerpo) {
        for (Map.Entry<String, Object> campo : cuerpo.entrySet()) {
            String nombre = campo.getKey();
            if (!CAMPOS_PERMITIDOS.contains(nombre)) {
                if (CAMPO_SENSIBLE.matcher(nombre).matches()) {
                    throw ExcepcionEnZona.solicitudInvalida("DATOS_DE_TARJETA_NO_PERMITIDOS",
                            "EnZona no recibe ni almacena el número de tarjeta ni el código de seguridad. "
                                    + "Envía solo el token de la pasarela, la marca, los últimos 4 dígitos y el vencimiento.")
                            .con("campo", nombre);
                }
                throw ExcepcionEnZona.solicitudInvalida("CAMPO_NO_PERMITIDO",
                        "El campo «" + nombre + "» no forma parte de un método de pago.").con("campo", nombre);
            }
            Object valor = campo.getValue();
            if (valor instanceof Number n && String.valueOf(n.longValue()).length() >= 13) {
                throw datosDeTarjeta(nombre);
            }
            if (valor instanceof String s && parecePan(s)) {
                throw datosDeTarjeta(nombre);
            }
        }
    }

    private static ExcepcionEnZona datosDeTarjeta(String campo) {
        return ExcepcionEnZona.solicitudInvalida("DATOS_DE_TARJETA_NO_PERMITIDOS",
                "El campo «" + campo + "» parece contener un número de tarjeta. EnZona no lo recibe ni lo almacena.")
                .con("campo", campo);
    }

    private static String texto(Map<String, Object> cuerpo, String campo) {
        Object v = cuerpo.get(campo);
        return v == null ? null : String.valueOf(v).trim();
    }

    @Transactional(readOnly = true)
    public List<MetodoPagoDto> listar(Long usuarioId) {
        return metodos.findByUsuarioIdOrderByIdAsc(usuarioId).stream().map(MetodoPagoDto::de).toList();
    }

    public MetodoPago exigirPropio(Long usuarioId, Long metodoId) {
        return metodos.findByIdAndUsuarioId(metodoId, usuarioId)
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El método de pago no existe."));
    }

    @Transactional
    public MetodoPagoDto agregar(Long usuarioId, Map<String, Object> cuerpo) {
        rechazarDatosDeTarjeta(cuerpo);
        String tipoTexto = Optional.ofNullable(texto(cuerpo, "tipo")).orElse("TARJETA").toUpperCase(Locale.ROOT);
        if (!"TARJETA".equals(tipoTexto)) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                    "Solo se guardan tarjetas. OXXO y SPEI se eligen al pagar y no necesitan guardarse.");
        }
        String marca = texto(cuerpo, "marca");
        String ultimos4 = texto(cuerpo, "ultimos4");
        String vencimiento = texto(cuerpo, "vencimiento");
        String token = texto(cuerpo, "tokenPasarela");
        if (marca == null || marca.isBlank() || marca.length() > 40) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Indica la marca de la tarjeta (Visa, Mastercard…).");
        }
        if (ultimos4 == null || !ULTIMOS4.matcher(ultimos4).matches()) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Indica los últimos 4 dígitos de la tarjeta.");
        }
        if (vencimiento == null || !VENCIMIENTO.matcher(vencimiento).matches()) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "La fecha de vencimiento debe tener el formato MM/AA.");
        }
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Falta el token que entrega la pasarela de pago.");
        }
        boolean primero = metodos.findByUsuarioIdOrderByIdAsc(usuarioId).isEmpty();
        boolean predeterminado = primero || Boolean.TRUE.equals(cuerpo.get("predeterminado"));
        if (predeterminado && !primero) {
            quitarPredeterminado(usuarioId);
        }
        MetodoPago m = new MetodoPago();
        m.setUsuarioId(usuarioId);
        m.setTipo(TipoMetodoPago.TARJETA);
        m.setMarca(marca);
        m.setUltimos4(ultimos4);
        m.setVencimiento(vencimiento);
        m.setTokenPasarela(token);
        m.setPredeterminado(predeterminado);
        m.setFechaRegistro(reloj.instant());
        return MetodoPagoDto.de(metodos.saveAndFlush(m));
    }

    /** Deja la cuenta sin método predeterminado (el índice único parcial solo admite uno). */
    private void quitarPredeterminado(Long usuarioId) {
        List<MetodoPago> marcados = metodos.findByUsuarioIdOrderByIdAsc(usuarioId).stream()
                .filter(MetodoPago::isPredeterminado).toList();
        marcados.forEach(m -> m.setPredeterminado(false));
        metodos.saveAllAndFlush(marcados);
    }

    @Transactional
    public List<MetodoPagoDto> eliminar(Long usuarioId, Long metodoId) {
        MetodoPago m = exigirPropio(usuarioId, metodoId);
        boolean era = m.isPredeterminado();
        metodos.delete(m);
        metodos.flush();
        if (era) {
            // Si se borró el predeterminado, el primero que quede toma su lugar
            metodos.findByUsuarioIdOrderByIdAsc(usuarioId).stream().findFirst().ifPresent(siguiente -> {
                siguiente.setPredeterminado(true);
                metodos.saveAndFlush(siguiente);
            });
        }
        return listar(usuarioId);
    }

    @Transactional
    public List<MetodoPagoDto> marcarPredeterminado(Long usuarioId, Long metodoId) {
        MetodoPago m = exigirPropio(usuarioId, metodoId);
        quitarPredeterminado(usuarioId);
        m.setPredeterminado(true);
        metodos.saveAndFlush(m);
        return listar(usuarioId);
    }
}
