package mx.uv.enzona.validacion;

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

/** Bitácora de cada intento de acceso, con el motivo y si se registró sin conexión. */
@Entity
@Table(name = "validacion_acceso")
public class ValidacionAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long boletoId;

    private Long validadorId;

    private Instant fechaHora;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "resultado_acceso")
    private ResultadoAcceso resultado;

    private boolean offline;

    private boolean sincronizado;

    private String motivo;

    private String codigoLeido;

    private Long eventoId;

    private Instant fechaEscaneo;

    private String claveCliente;

    public Long getId() {
        return id;
    }

    public Long getBoletoId() {
        return boletoId;
    }

    public void setBoletoId(Long boletoId) {
        this.boletoId = boletoId;
    }

    public Long getValidadorId() {
        return validadorId;
    }

    public void setValidadorId(Long validadorId) {
        this.validadorId = validadorId;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(Instant fechaHora) {
        this.fechaHora = fechaHora;
    }

    public ResultadoAcceso getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoAcceso resultado) {
        this.resultado = resultado;
    }

    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean isSincronizado() {
        return sincronizado;
    }

    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getCodigoLeido() {
        return codigoLeido;
    }

    public void setCodigoLeido(String codigoLeido) {
        this.codigoLeido = codigoLeido;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public Instant getFechaEscaneo() {
        return fechaEscaneo;
    }

    public void setFechaEscaneo(Instant fechaEscaneo) {
        this.fechaEscaneo = fechaEscaneo;
    }

    public String getClaveCliente() {
        return claveCliente;
    }

    public void setClaveCliente(String claveCliente) {
        this.claveCliente = claveCliente;
    }
}
