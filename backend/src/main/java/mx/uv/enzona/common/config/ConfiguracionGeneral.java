package mx.uv.enzona.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Repositorios JPA (incluidos los declarados dentro de cada servicio) y
 * documento OpenAPI de la API (DA-02).
 */
@Configuration
@EnableJpaRepositories(basePackages = "mx.uv.enzona", considerNestedRepositories = true)
public class ConfiguracionGeneral {

    @Bean
    public OpenAPI documentoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EnZona API")
                        .version("v5")
                        .description("""
                                API REST de EnZona: descubrimiento y gestión de eventos locales con boletos \
                                electrónicos validados por código QR. La consumen la app Android y la web React.

                                Simulaciones vigentes (no hay proveedor contratado): la pasarela de pago, el envío de \
                                correo y el envío del código de verificación (OTP). Ninguna mueve dinero ni envía \
                                mensajes reales.

                                Errores: `application/problem+json` (RFC 7807) con `status`, `title`, `detail` y `codigo`. \
                                400 validación · 401 sin sesión · 403 sin permiso · 404 no existe · 409 conflicto de \
                                inventario o de estado · 422 regla de negocio.

                                Fechas: ISO-8601 con zona; el servidor trabaja en UTC y los clientes muestran \
                                America/Mexico_City. Importes: texto decimal exacto en MXN.""")
                        .contact(new Contact().name("Equipo EnZona · Universidad Veracruzana")))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Token devuelto por POST /api/auth/login (expira a las 12 horas)")));
    }
}
