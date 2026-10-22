package mx.uv.enzona.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import mx.uv.enzona.common.config.PropiedadesEnZona;

/**
 * Textos de fecha e importe para notificaciones y correos. La API intercambia
 * fechas en ISO-8601 (UTC); solo los textos dirigidos a personas se escriben en
 * la zona del evento (America/Mexico_City), con el mismo formato que la app.
 */
@Component
public class FormatoFecha {

    // Abreviaturas fijas: las del JDK cambian con la versión de CLDR ("sep" / "sept") y el texto debe ser estable
    private static final String[] DIAS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final String[] MESES = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct",
            "Nov", "Dic"};

    private final ZoneId zona;

    public FormatoFecha(PropiedadesEnZona propiedades) {
        this.zona = ZoneId.of(propiedades.zonaHoraria());
    }

    public ZoneId zona() {
        return zona;
    }

    private static String diaYMes(ZonedDateTime f) {
        return DIAS[f.getDayOfWeek().getValue() - 1] + " " + f.getDayOfMonth() + " " + MESES[f.getMonthValue() - 1];
    }

    /** "Vie 25 Sep · 19:00" */
    public String corto(Instant instante) {
        ZonedDateTime f = instante.atZone(zona);
        return diaYMes(f) + " · " + f.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** "Vie 25 Sep 2026 · 19:00" */
    public String largo(Instant instante) {
        ZonedDateTime f = instante.atZone(zona);
        return diaYMes(f) + " " + f.getYear() + " · " + f.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** Escala monetaria MXN: 2 decimales, HALF_UP. */
    public static BigDecimal mxn(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    /** "$1,500.00 MXN" */
    public static String importe(BigDecimal importe) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "$" + f.format(mxn(importe)) + " MXN";
    }
}
