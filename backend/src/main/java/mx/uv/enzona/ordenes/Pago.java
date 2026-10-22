package mx.uv.enzona.ordenes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Movimiento contra la pasarela. Guarda referencias de la transacción externa, nunca datos de tarjeta. */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ordenId;

    private BigDecimal monto;

    private String moneda;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_pago")
    private EstadoPago estado;

    private String referenciaPasarela;

    private Instant fecha;

    private String referenciaReembolso;

    private Instant fechaReembolso;

    private Long metodoPagoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "tipo_metodo_pago")
    private TipoMetodoPago medio;

    public Long getId() {
        return id;
    }

    public Long getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(Long ordenId) {
        this.ordenId = ordenId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public void setEstado(EstadoPago estado) {
        this.estado = estado;
    }

    public String getReferenciaPasarela() {
        return referenciaPasarela;
    }

    public void setReferenciaPasarela(String referenciaPasarela) {
        this.referenciaPasarela = referenciaPasarela;
    }

    public Instant getFecha() {
        return fecha;
    }

    public void setFecha(Instant fecha) {
        this.fecha = fecha;
    }

    public String getReferenciaReembolso() {
        return referenciaReembolso;
    }

    public void setReferenciaReembolso(String referenciaReembolso) {
        this.referenciaReembolso = referenciaReembolso;
    }

    public Instant getFechaReembolso() {
        return fechaReembolso;
    }

    public void setFechaReembolso(Instant fechaReembolso) {
        this.fechaReembolso = fechaReembolso;
    }

    public Long getMetodoPagoId() {
        return metodoPagoId;
    }

    public void setMetodoPagoId(Long metodoPagoId) {
        this.metodoPagoId = metodoPagoId;
    }

    public TipoMetodoPago getMedio() {
        return medio;
    }

    public void setMedio(TipoMetodoPago medio) {
        this.medio = medio;
    }
}
