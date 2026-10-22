package mx.uv.enzona.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración propia de EnZona (prefijo `enzona` en application.yml). Los secretos llegan por variables de entorno. */
@ConfigurationProperties(prefix = "enzona")
public record PropiedadesEnZona(
        Jwt jwt,
        Hmac hmac,
        Curp curp,
        Cors cors,
        Otp otp,
        String zonaHoraria,
        Cancelacion cancelacion,
        Recordatorios recordatorios,
        Pagos pagos) {

    public record Jwt(String secreto, int horasExpiracion) { }

    public record Hmac(String secreto) { }

    public record Curp(String clave) { }

    public record Cors(String origenes) { }

    public record Otp(int minutosVigencia, boolean devolverEnRespuesta) { }

    public record Cancelacion(int porcentajeComision) { }

    public record Recordatorios(int diasAntes, String cron) { }

    public record Pagos(boolean confirmacionSimulada) { }
}
