package mx.uv.enzona.eventos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import mx.uv.enzona.eventos.EventoDtos.PoliticaCancelacionDto;

/**
 * Regla de negocio de cancelación de eventos (RF-14). Es la misma regla que
 * `politicaCancelacion()` de la app (data/model/Cancelacion.kt): aquí vive la
 * versión que manda, y web y Android muestran lo que el servidor calcula (RNF-10).
 *
 *  - Un evento CANCELADO, REALIZADO o que ya comenzó no se cancela por ningún rol.
 *  - Administrativa (RF-18): reembolso del 100 %, motivo obligatorio, sin comisión.
 *  - Borrador: se cancela sin comisión.
 *  - Gratuito (ningún tipo con precio mayor que cero): comisión 0.
 *  - De pago sin órdenes cobradas: procede como gratuito, comisión 0.
 *  - De pago con órdenes cobradas: comisión del 10 % sobre lo cobrado, redondeo
 *    HALF_UP a 2 decimales; los asistentes reciben el 100 % de reembolso.
 */
public record PoliticaCancelacion(
        boolean permitida,
        Tipo tipo,
        BigDecimal importeCobrado,
        BigDecimal comision,
        BigDecimal reembolso,
        int boletosAfectados,
        String motivoBloqueo) {

    public enum Tipo {
        BORRADOR, GRATUITO, DE_PAGO_SIN_VENTAS, DE_PAGO_CON_COMISION, ADMINISTRATIVA
    }

    public boolean cobraComision() {
        return comision.signum() > 0;
    }

    private static BigDecimal mxn(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Función pura.
     *
     * @param cobrado suma de `orden.total` de las órdenes PAGADA del evento
     * @param tienePrecio true si algún tipo de boleto del evento cuesta más de cero
     * @param boletosVigentes boletos VALIDO o USADO que quedarían anulados
     */
    public static PoliticaCancelacion evaluar(EstadoEvento estado, Instant inicio, boolean esDePago,
            boolean tienePrecio, BigDecimal cobrado, int boletosVigentes, Instant ahora, boolean administrativa,
            int porcentajeComision) {
        BigDecimal importe = mxn(cobrado == null ? BigDecimal.ZERO : cobrado);
        BigDecimal cero = mxn(BigDecimal.ZERO);

        String bloqueo = null;
        if (estado == EstadoEvento.CANCELADO) {
            bloqueo = "El evento ya está cancelado.";
        } else if (estado == EstadoEvento.REALIZADO) {
            bloqueo = "El evento ya se realizó; solo puede quedar como finalizado.";
        } else if (!inicio.isAfter(ahora)) {
            bloqueo = "El evento ya comenzó. No se puede cancelar; márcalo como finalizado.";
        }
        if (bloqueo != null) {
            return new PoliticaCancelacion(false, null, importe, cero, cero, boletosVigentes, bloqueo);
        }

        Tipo tipo;
        if (administrativa) {
            tipo = Tipo.ADMINISTRATIVA;
        } else if (estado == EstadoEvento.BORRADOR) {
            tipo = Tipo.BORRADOR;
        } else if (!(esDePago && tienePrecio)) {
            tipo = Tipo.GRATUITO;
        } else if (importe.signum() == 0) {
            tipo = Tipo.DE_PAGO_SIN_VENTAS;
        } else {
            tipo = Tipo.DE_PAGO_CON_COMISION;
        }
        BigDecimal comision = tipo == Tipo.DE_PAGO_CON_COMISION
                ? mxn(importe.multiply(BigDecimal.valueOf(porcentajeComision)).divide(BigDecimal.valueOf(100)))
                : cero;
        return new PoliticaCancelacion(true, tipo, importe, comision, importe, boletosVigentes, null);
    }

    public PoliticaCancelacionDto aDto() {
        return new PoliticaCancelacionDto(permitida, tipo == null ? null : tipo.name(), importeCobrado.toPlainString(),
                comision.toPlainString(), reembolso.toPlainString(), boletosAfectados, motivoBloqueo);
    }
}
