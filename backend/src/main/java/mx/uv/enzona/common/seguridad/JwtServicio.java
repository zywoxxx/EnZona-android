package mx.uv.enzona.common.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import mx.uv.enzona.common.config.PropiedadesEnZona;

/**
 * Emisión y verificación del JWT (DA-03, RNF-01). Firmado con ENZONA_JWT_SECRET,
 * expira a las 12 horas y lleva `sub` (id de usuario), `roles` y `nombre`.
 */
@Service
public class JwtServicio {

    public record TokenEmitido(String token, Instant expira) { }

    private final SecretKey clave;
    private final int horas;
    private final Clock reloj;

    public JwtServicio(PropiedadesEnZona propiedades, Clock reloj) {
        byte[] secreto = propiedades.jwt().secreto().getBytes(StandardCharsets.UTF_8);
        if (secreto.length < 64) {
            throw new IllegalStateException("ENZONA_JWT_SECRET debe tener al menos 64 caracteres.");
        }
        this.clave = Keys.hmacShaKeyFor(secreto);
        this.horas = propiedades.jwt().horasExpiracion();
        this.reloj = reloj;
    }

    public TokenEmitido emitir(Long usuarioId, String nombre, Collection<String> roles) {
        Instant ahora = reloj.instant();
        Instant expira = ahora.plus(horas, ChronoUnit.HOURS);
        String token = Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("roles", List.copyOf(roles))
                .claim("nombre", nombre)
                .issuer("enzona-api")
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(clave)
                .compact();
        return new TokenEmitido(token, expira);
    }

    /** Devuelve el id de usuario si el token es auténtico y no ha expirado. */
    public Optional<Long> verificar(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(clave).clock(() -> Date.from(reloj.instant())).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(Long.parseLong(c.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
