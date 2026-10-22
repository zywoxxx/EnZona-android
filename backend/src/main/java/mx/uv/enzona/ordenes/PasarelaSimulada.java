package mx.uv.enzona.ordenes;

import java.math.BigDecimal;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SIMULACIÓN de pasarela: NO realiza ningún cargo ni reembolso real.
 *
 *  - TARJETA: aprobada, salvo que el token termine en `-rechazo` (reproduce CP-10
 *    y CP-MULTI-06).
 *  - OXXO y SPEI: devuelven una referencia y quedan PENDIENTE; la confirmación se
 *    simula con `POST /api/pagos/{id}/confirmar`.
 *
 * Las referencias empiezan por `SIM-` para que nadie las confunda con las de un
 * proveedor real.
 */
@Component
public class PasarelaSimulada implements PasarelaPagos {

    public static final String SUFIJO_RECHAZO = "-rechazo";

    private static final Logger log = LoggerFactory.getLogger(PasarelaSimulada.class);

    private final SecureRandom azar = new SecureRandom();

    private String digitos(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(azar.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public Resultado cobrar(Cobro cobro) {
        Resultado r = switch (cobro.medio()) {
            case TARJETA -> {
                String token = cobro.tokenPasarela() == null ? "" : cobro.tokenPasarela();
                yield token.endsWith(SUFIJO_RECHAZO)
                        ? new Resultado(Estado.RECHAZADO, "SIM-RECH-" + digitos(8),
                                "La pasarela rechazó el pago. No se realizó ningún cargo.")
                        : new Resultado(Estado.APROBADO, "SIM-TARJ-" + digitos(10), "Pago aprobado (simulación).");
            }
            case OXXO -> new Resultado(Estado.PENDIENTE, "SIM-OXXO-" + digitos(14),
                    "Paga en OXXO con esta referencia (simulación). Vigencia de 24 horas.");
            case SPEI -> new Resultado(Estado.PENDIENTE, "SIM-SPEI-" + digitos(18),
                    "Transfiere por SPEI a esta CLABE (simulación). Vigencia de 24 horas.");
        };
        log.info("SIMULACIÓN de pasarela · cobro {} {} por {} {} → {} ({})", cobro.medio(), cobro.referenciaOrden(),
                cobro.monto(), cobro.moneda(), r.estado(), r.referencia());
        return r;
    }

    @Override
    public String reembolsar(String referenciaPago, BigDecimal monto) {
        String referencia = "SIM-REEMB-" + digitos(10);
        log.info("SIMULACIÓN de pasarela · reembolso de {} MXN del pago {} → {}", monto, referenciaPago, referencia);
        return referencia;
    }

    @Override
    public boolean esSimulada() {
        return true;
    }
}
