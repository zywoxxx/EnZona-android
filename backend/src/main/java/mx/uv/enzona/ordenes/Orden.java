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

/** Intención de compra de un asistente. `claveOperacion` guarda la cabecera Idempotency-Key. */
@Entity
@Table(name = "orden")
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long asistenteId;

    private Long eventoId;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_orden")
    private EstadoOrden estado;

    private Instant fechaCreacion;

    private String claveOperacion;

    public Long getId() {
        return id;
    }

    public Long getAsistenteId() {
        return asistenteId;
    }

    public void setAsistenteId(Long asistenteId) {
        this.asistenteId = asistenteId;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrden estado) {
        this.estado = estado;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getClaveOperacion() {
        return claveOperacion;
    }

    public void setClaveOperacion(String claveOperacion) {
        this.claveOperacion = claveOperacion;
    }
}
