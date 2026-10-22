package mx.uv.enzona.ordenes;

import java.math.BigDecimal;

/**
 * Puerto hacia la pasarela de pago externa (DA-07). La plataforma no custodia
 * fondos ni almacena datos de tarjeta: solo conoce el token que la pasarela
 * entrega y las referencias de cada movimiento.
 *
 * La elección del proveedor sigue abierta (Anexo E, tabla E.8); cambiarlo es
 * escribir otra implementación de esta interfaz.
 */
public interface PasarelaPagos {

    enum Estado {
        APROBADO, RECHAZADO, PENDIENTE
    }

    /**
     * @param medio TARJETA, OXXO o SPEI
     * @param tokenPasarela token de la tarjeta (solo TARJETA); nunca el número
     * @param referenciaOrden identificador propio para conciliar (p. ej. "orden-123")
     */
    record Cobro(TipoMetodoPago medio, String tokenPasarela, BigDecimal monto, String moneda, String referenciaOrden) {
    }

    /**
     * @param referencia identificador del movimiento en la pasarela; en OXXO/SPEI es la
     *                   referencia o CLABE con la que paga el asistente
     */
    record Resultado(Estado estado, String referencia, String mensaje) {
    }

    Resultado cobrar(Cobro cobro);

    /** Solicita el reembolso total de un cobro aprobado y devuelve la referencia del reembolso. */
    String reembolsar(String referenciaPago, BigDecimal monto);

    /** true si la implementación no mueve dinero real. */
    boolean esSimulada();
}
