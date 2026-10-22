package mx.uv.enzona.common.seguridad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica la petición a partir de `Authorization: Bearer <jwt>`.
 *
 * El token prueba la identidad; los roles y el estado de la cuenta se leen de la
 * base en cada petición. Así, conceder o retirar un rol (usuario_rol) o bloquear
 * una cuenta surte efecto de inmediato, sin esperar a que el token expire.
 */
@Component
public class FiltroJwt extends OncePerRequestFilter {

    private final JwtServicio jwt;
    private final JdbcClient jdbc;

    public FiltroJwt(JwtServicio jwt, JdbcClient jdbc) {
        this.jwt = jwt;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.regionMatches(true, 0, "Bearer ", 0, 7)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwt.verificar(cabecera.substring(7).trim()).flatMap(this::cargar).ifPresent(u -> {
                List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
                u.roles().forEach(r -> autoridades.add(new SimpleGrantedAuthority("ROLE_" + r)));
                if (u.esAdmin()) {
                    autoridades.add(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
                }
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, autoridades));
            });
        }
        chain.doFilter(request, response);
    }

    /** Solo las cuentas ACTIVO se autentican; una cuenta bloqueada deja de tener acceso aunque su token siga vigente. */
    private Optional<UsuarioActual> cargar(Long id) {
        return jdbc.sql("""
                SELECT u.id, u.nombre, u.correo,
                       COALESCE(string_agg(r.nombre, ',' ORDER BY r.id), '') AS roles
                  FROM usuario u
                  LEFT JOIN usuario_rol ur ON ur.usuario_id = u.id
                  LEFT JOIN rol r ON r.id = ur.rol_id
                 WHERE u.id = :id AND u.estado = 'ACTIVO'
                 GROUP BY u.id, u.nombre, u.correo
                """)
                .param("id", id)
                .query((rs, n) -> {
                    Set<String> roles = new LinkedHashSet<>();
                    Arrays.stream(rs.getString("roles").split(",")).filter(s -> !s.isBlank()).forEach(roles::add);
                    return new UsuarioActual(rs.getLong("id"), rs.getString("nombre"), rs.getString("correo"), roles);
                })
                .optional();
    }
}
