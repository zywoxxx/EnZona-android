package mx.uv.enzona.boletos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Entrada emitida, firmada (HMAC) y de un solo uso. El contenido del QR es `codigo.qrFirma`. */
@Entity
@Table(name = "boleto")
public class Boleto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ordenId;

    private Long tipoBoletoId;

    private Long asientoId;

    private String codigo;

    private String qrFirma;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_boleto")
    private EstadoBoleto estado;

    private Instant fechaEmision;

    private Instant fechaUso;

    public Long getId() {
        return id;
    }

    public Long getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(Long ordenId) {
        this.ordenId = ordenId;
    }

    public Long getTipoBoletoId() {
        return tipoBoletoId;
    }

    public void setTipoBoletoId(Long tipoBoletoId) {
        this.tipoBoletoId = tipoBoletoId;
    }

    public Long getAsientoId() {
        return asientoId;
    }

    public void setAsientoId(Long asientoId) {
        this.asientoId = asientoId;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getQrFirma() {
        return qrFirma;
    }

    public void setQrFirma(String qrFirma) {
        this.qrFirma = qrFirma;
    }

    public EstadoBoleto getEstado() {
        return estado;
    }

    public void setEstado(EstadoBoleto estado) {
        this.estado = estado;
    }

    public Instant getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(Instant fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public Instant getFechaUso() {
        return fechaUso;
    }

    public void setFechaUso(Instant fechaUso) {
        this.fechaUso = fechaUso;
    }
}
