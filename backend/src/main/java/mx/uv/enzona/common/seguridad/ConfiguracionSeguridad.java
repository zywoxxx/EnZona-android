package mx.uv.enzona.common.seguridad;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import mx.uv.enzona.common.config.PropiedadesEnZona;
import mx.uv.enzona.common.error.ManejadorGlobalErrores;

/**
 * Seguridad sin estado con JWT (DA-03). Lo público es el acceso (registro, OTP,
 * login), el catálogo de eventos publicados y la documentación; todo lo demás
 * exige token, y el rol se comprueba con `@PreAuthorize` en cada operación.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    @Bean
    public SecurityFilterChain cadena(HttpSecurity http, FiltroJwt filtroJwt, ObjectMapper json) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categorias").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/eventos", "/api/eventos/{id:\\d+}",
                                "/api/eventos/{id:\\d+}/tipos").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, json, HttpStatus.UNAUTHORIZED,
                                "NO_AUTENTICADO", "Inicia sesión para continuar. El token falta, expiró o no es válido."))
                        .accessDeniedHandler((req, res, ex) -> escribir(res, json, HttpStatus.FORBIDDEN,
                                "PROHIBIDO", "Tu cuenta no tiene permiso para realizar esta acción.")))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void escribir(HttpServletResponse res, ObjectMapper json, HttpStatus estado, String codigo, String detalle)
            throws java.io.IOException {
        ProblemDetail p = ManejadorGlobalErrores.problema(estado, codigo, detalle);
        res.setStatus(estado.value());
        res.setCharacterEncoding("UTF-8");
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(res.getWriter(), p);
    }

    /** BCrypt con factor de costo 10 (RNF-01). */
    @Bean
    public PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder(10);
    }

    /** La autenticación es propia (JWT); se declara para que Spring no genere un usuario con contraseña por defecto. */
    @Bean
    public UserDetailsService sinUsuariosEnMemoria() {
        return nombre -> {
            throw new UsernameNotFoundException("La autenticación se hace con JWT");
        };
    }

    @Bean
    public CorsConfigurationSource cors(PropiedadesEnZona propiedades) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(propiedades.cors().origenes().split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept"));
        c.setExposedHeaders(List.of("Location"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", c);
        return fuente;
    }
}
