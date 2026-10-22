package mx.uv.enzona.ordenes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Lo pedido y el precio confirmado al ordenar; permite emitir cuando la pasarela confirma un pago pendiente. */
@Entity
@Table(name = "orden_detalle")
public class OrdenDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ordenId;

    private Long tipoBoletoId;

    private int cantidad;

    private BigDecimal precioUnitario;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "asientos_ids", columnDefinition = "bigint[]")
    private Long[] asientosIds;

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

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public Long[] getAsientosIds() {
        return asientosIds;
    }

    public void setAsientosIds(Long[] asientosIds) {
        this.asientosIds = asientosIds;
    }
}
