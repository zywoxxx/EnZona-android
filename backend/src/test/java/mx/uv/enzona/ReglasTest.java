package mx.uv.enzona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.uv.enzona.common.config.PropiedadesEnZona;
import mx.uv.enzona.common.util.CifradoCurp;
import mx.uv.enzona.common.util.FirmaHmac;
import mx.uv.enzona.common.util.FormatoFecha;
import mx.uv.enzona.eventos.EstadoEvento;
import mx.uv.enzona.eventos.PoliticaCancelacion;
import mx.uv.enzona.eventos.PoliticaCancelacion.Tipo;

/** Reglas puras, sin base de datos ni Spring: política de cancelación, firma, cifrado y formato. */
class ReglasTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");
    private static final Instant FUTURO = AHORA.plus(5, ChronoUnit.DAYS);

    private static PropiedadesEnZona propiedades() {
        return new PropiedadesEnZona(
                new PropiedadesEnZona.Jwt("j".repeat(70), 12),
                new PropiedadesEnZona.Hmac("h".repeat(70)),
                new PropiedadesEnZona.Curp("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"),
                new PropiedadesEnZona.Cors("http://localhost:5173"),
                new PropiedadesEnZona.Otp(10, false), "America/Mexico_City",
                new PropiedadesEnZona.Cancelacion(10), new PropiedadesEnZona.Recordatorios(7, "-"),
                new PropiedadesEnZona.Pagos(false));
    }

    private static PoliticaCancelacion politica(EstadoEvento estado, Instant inicio, boolean esDePago, String cobrado,
            boolean administrativa) {
        return PoliticaCancelacion.evaluar(estado, inicio, esDePago, esDePago, new BigDecimal(cobrado), 3, AHORA,
                administrativa, 10);
    }

    @Test
    @DisplayName("Política de cancelación: los cinco tipos y los tres bloqueos")
    void politicaDeCancelacion() {
        assertThat(politica(EstadoEvento.PUBLICADO, FUTURO, false, "0", false).tipo()).isEqualTo(Tipo.GRATUITO);
        assertThat(politica(EstadoEvento.PUBLICADO, FUTURO, true, "0", false).tipo()).isEqualTo(Tipo.DE_PAGO_SIN_VENTAS);
        assertThat(politica(EstadoEvento.BORRADOR, FUTURO, true, "0", false).tipo()).isEqualTo(Tipo.BORRADOR);

        PoliticaCancelacion conVentas = politica(EstadoEvento.PUBLICADO, FUTURO, true, "1500", false);
        assertThat(conVentas.tipo()).isEqualTo(Tipo.DE_PAGO_CON_COMISION);
        assertThat(conVentas.comision()).isEqualByComparingTo("150.00");
        assertThat(conVentas.reembolso()).isEqualByComparingTo("1500.00");
        assertThat(conVentas.cobraComision()).isTrue();

        PoliticaCancelacion admin = politica(EstadoEvento.PUBLICADO, FUTURO, true, "1500", true);
        assertThat(admin.tipo()).isEqualTo(Tipo.ADMINISTRATIVA);
        assertThat(admin.comision()).isEqualByComparingTo("0.00");
        assertThat(admin.reembolso()).isEqualByComparingTo("1500.00");

        for (PoliticaCancelacion bloqueada : new PoliticaCancelacion[] {
                politica(EstadoEvento.CANCELADO, FUTURO, true, "100", false),
                politica(EstadoEvento.REALIZADO, FUTURO, true, "100", true),
                politica(EstadoEvento.PUBLICADO, AHORA.minusSeconds(1), true, "100", false),
                politica(EstadoEvento.PUBLICADO, AHORA, false, "0", true)}) {
            assertThat(bloqueada.permitida()).isFalse();
            assertThat(bloqueada.tipo()).isNull();
            assertThat(bloqueada.motivoBloqueo()).isNotBlank();
            assertThat(bloqueada.comision()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    @DisplayName("Comisión del 10 % con redondeo HALF_UP a 2 decimales")
    void redondeo() {
        assertThat(politica(EstadoEvento.PUBLICADO, FUTURO, true, "333.35", false).comision().toPlainString())
                .isEqualTo("33.34");
        assertThat(politica(EstadoEvento.PUBLICADO, FUTURO, true, "99.99", false).comision().toPlainString())
                .isEqualTo("10.00");
        assertThat(politica(EstadoEvento.PUBLICADO, FUTURO, true, "0.04", false).comision().toPlainString())
                .isEqualTo("0.00");
        assertThat(politica(EstadoEvento.PUBLICADO, FUTURO, true, "0.05", false).comision().toPlainString())
                .isEqualTo("0.01");
        assertThat(politica(EstadoEvento.PUBLICADO, FUTURO, true, "360", false).aDto().comision()).isEqualTo("36.00");
    }

    @Test
    @DisplayName("Firma HMAC del boleto: determinista, de 32 caracteres y sensible a cualquier cambio")
    void firma() {
        FirmaHmac firma = new FirmaHmac(propiedades());
        String f = firma.firmar("EZ-1234-5678-9012");
        assertThat(f).hasSize(32).matches("[0-9a-f]+").isEqualTo(firma.firmar("EZ-1234-5678-9012"));
        assertThat(firma.esAutentica("EZ-1234-5678-9012", f)).isTrue();
        assertThat(firma.esAutentica("EZ-1234-5678-9012", f.toUpperCase())).isTrue();
        assertThat(firma.esAutentica("EZ-1234-5678-9013", f)).isFalse();
        assertThat(firma.esAutentica("EZ-1234-5678-9012", "0" + f.substring(1))).isEqualTo(f.startsWith("0"));
        assertThat(firma.esAutentica("EZ-1234-5678-9012", null)).isFalse();
        assertThat(FirmaHmac.huella("a.b")).hasSize(64).isNotEqualTo(FirmaHmac.huella("a.c"));
    }

    @Test
    @DisplayName("CURP: cifrado AES-GCM con vector aleatorio, índice ciego estable y máscara")
    void curp() {
        CifradoCurp c = new CifradoCurp(propiedades());
        String cifrada = c.cifrar("orga850101hvzrrn04");
        assertThat(cifrada).doesNotContain("ORGA850101").isNotEqualTo(c.cifrar("ORGA850101HVZRRN04"));
        assertThat(c.descifrar(cifrada)).isEqualTo("ORGA850101HVZRRN04");
        assertThat(c.indice("orga850101hvzrrn04")).isEqualTo(c.indice(" ORGA850101HVZRRN04 ")).hasSize(64);
        assertThat(c.indice("ORGA850101HVZRRN05")).isNotEqualTo(c.indice("ORGA850101HVZRRN04"));
        assertThat(CifradoCurp.enmascarar("ORGA850101HVZRRN04")).isEqualTo("ORGA············04");
        // Un texto cifrado alterado no se descifra (GCM autentica)
        String alterada = cifrada.substring(0, cifrada.length() - 4) + "AAAA";
        assertThatThrownBy(() -> c.descifrar(alterada)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Textos de fecha en la zona del evento y de importe en MXN")
    void formato() {
        FormatoFecha f = new FormatoFecha(propiedades());
        // 2026-09-26T01:00Z son las 19:00 del viernes 25 en Orizaba (UTC-6)
        Instant instante = Instant.parse("2026-09-26T01:00:00Z");
        assertThat(f.corto(instante)).isEqualTo("Vie 25 Sep · 19:00");
        assertThat(f.largo(instante)).isEqualTo("Vie 25 Sep 2026 · 19:00");
        assertThat(FormatoFecha.importe(new BigDecimal("1500"))).isEqualTo("$1,500.00 MXN");
        assertThat(FormatoFecha.mxn(new BigDecimal("10.005")).toPlainString()).isEqualTo("10.01");
    }
}
