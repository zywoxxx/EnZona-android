package mx.uv.enzona.common.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import mx.uv.enzona.common.config.PropiedadesEnZona;

/**
 * Cifrado de la CURP en reposo (RNF-03, LFPDPPP) con AES-256-GCM.
 *
 * - `cifrar` usa un vector de 12 bytes aleatorio por operación y guarda
 *   Base64(vector || texto cifrado + etiqueta).
 * - `indice` es un índice ciego (HMAC-SHA256 con una clave derivada) que permite
 *   comprobar unicidad y buscar por CURP sin descifrar ni guardar el dato en claro.
 * - La CURP nunca se devuelve completa: `enmascarar` deja 4 + 2 caracteres.
 */
@Component
public class CifradoCurp {

    private static final int BITS_ETIQUETA = 128;
    private static final int BYTES_VECTOR = 12;

    private final SecretKeySpec claveAes;
    private final SecretKeySpec claveIndice;
    private final SecureRandom azar = new SecureRandom();

    public CifradoCurp(PropiedadesEnZona propiedades) {
        String hex = propiedades.curp().clave();
        if (hex == null || !hex.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("ENZONA_CURP_KEY debe ser una clave AES-256 en hexadecimal (64 caracteres).");
        }
        byte[] clave = HexFormat.of().parseHex(hex);
        this.claveAes = new SecretKeySpec(clave, "AES");
        try {
            byte[] derivada = MessageDigest.getInstance("SHA-256")
                    .digest(("enzona-indice-curp:" + hex.toLowerCase()).getBytes(StandardCharsets.UTF_8));
            this.claveIndice = new SecretKeySpec(derivada, "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String normalizar(String curp) {
        return curp == null ? "" : curp.trim().toUpperCase();
    }

    public String cifrar(String curp) {
        try {
            byte[] vector = new byte[BYTES_VECTOR];
            azar.nextBytes(vector);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, claveAes, new GCMParameterSpec(BITS_ETIQUETA, vector));
            byte[] cifrado = c.doFinal(normalizar(curp).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(vector.length + cifrado.length).put(vector).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la CURP", e);
        }
    }

    public String descifrar(String guardado) {
        try {
            byte[] todo = Base64.getDecoder().decode(guardado);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, claveAes, new GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_VECTOR));
            return new String(c.doFinal(todo, BYTES_VECTOR, todo.length - BYTES_VECTOR), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar la CURP", e);
        }
    }

    public String indice(String curp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveIndice);
            return HexFormat.of().formatHex(mac.doFinal(normalizar(curp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** "ORGA850101HVZRRN04" → "ORGA············04". */
    public static String enmascarar(String curp) {
        String c = normalizar(curp);
        if (c.length() < 8) {
            return "····";
        }
        return c.substring(0, 4) + "·".repeat(c.length() - 6) + c.substring(c.length() - 2);
    }
}
