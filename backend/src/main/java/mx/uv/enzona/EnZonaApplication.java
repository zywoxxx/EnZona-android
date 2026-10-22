package mx.uv.enzona;

import java.time.Clock;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API REST de EnZona. Monolito modular por capas (Anexo E, DA-01):
 * auth, usuarios, eventos, ordenes, boletos, validacion, notificaciones y admin,
 * más `common` (seguridad, errores y utilidades).
 *
 * El servidor trabaja en UTC; los clientes formatean con America/Mexico_City.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class EnZonaApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(EnZonaApplication.class, args);
    }

    /** Reloj inyectable: las pruebas pueden fijar el instante. */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
