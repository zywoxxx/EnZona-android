package mx.uv.enzona.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import mx.uv.enzona.common.config.PropiedadesEnZona;

/**
 * Firma de boletos con HMAC-SHA256 (DA-04, RNF-02). El QR contiene
 * `codigo.firma`; la clave (ENZONA_HMAC_SECRET) nunca sale del servidor.
 *
 * La firma se trunca a 32 caracteres hexadecimales (128 bits): mantiene el QR
 * legible en pantallas pequeñas y sigue siendo inviable de falsificar.
 */
@Component
public class FirmaHmac {

    public static final int LONGITUD_FIRMA = 32;

    private final byte[] clave;

    public FirmaHmac(PropiedadesEnZona propiedades) {
        String secreto = propiedades.hmac().secreto();
        if (secreto == null || secreto.length() < 64) {
            throw new IllegalStateException("ENZONA_HMAC_SECRET debe tener al menos 64 caracteres.");
        }
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
    }

    public String firmar(String codigo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(codigo.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, LONGITUD_FIRMA);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el boleto", e);
        }
    }

    /** Comparación en tiempo constante: no revela cuántos caracteres coinciden. */
    public boolean esAutentica(String codigo, String firma) {
        if (codigo == null || firma == null) {
            return false;
        }
        return MessageDigest.isEqual(firmar(codigo).getBytes(StandardCharsets.UTF_8),
                firma.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** Huella pública del contenido del QR para el manifiesto sin conexión: SHA-256(codigo.firma). */
    public static String huella(String contenidoQr) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contenidoQr.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
