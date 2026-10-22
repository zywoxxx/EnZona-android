package mx.uv.enzona.eventos;

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

/** Evento publicable con su aforo, modalidad, ubicación y registro de cancelación (RF-08, RF-14, RF-19). */
@Entity
@Table(name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizadorId;

    private Integer categoriaId;

    private String nombre;

    private String descripcion;

    private String lugar;

    private String direccion;

    private String ciudad;

    private BigDecimal latitud;

    private BigDecimal longitud;

    private Instant fechaHoraInicio;

    private Instant fechaHoraFin;

    private int aforo;

    private boolean esDePago;

    private boolean requiereAsiento;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_evento")
    private EstadoEvento estado;

    private Instant fechaCreacion;

    private Instant fechaCancelacion;

    private Long canceladoPor;

    private String motivoCancelacion;

    private BigDecimal comisionCancelacion;

    public Long getId() {
        return id;
    }

    public Long getOrganizadorId() {
        return organizadorId;
    }

    public void setOrganizadorId(Long organizadorId) {
        this.organizadorId = organizadorId;
    }

    public Integer getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(Integer categoriaId) {
        this.categoriaId = categoriaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getLugar() {
        return lugar;
    }

    public void setLugar(String lugar) {
        this.lugar = lugar;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public void setLatitud(BigDecimal latitud) {
        this.latitud = latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public void setLongitud(BigDecimal longitud) {
        this.longitud = longitud;
    }

    public Instant getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public void setFechaHoraInicio(Instant fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    public Instant getFechaHoraFin() {
        return fechaHoraFin;
    }

    public void setFechaHoraFin(Instant fechaHoraFin) {
        this.fechaHoraFin = fechaHoraFin;
    }

    public int getAforo() {
        return aforo;
    }

    public void setAforo(int aforo) {
        this.aforo = aforo;
    }

    public boolean isEsDePago() {
        return esDePago;
    }

    public void setEsDePago(boolean esDePago) {
        this.esDePago = esDePago;
    }

    public boolean isRequiereAsiento() {
        return requiereAsiento;
    }

    public void setRequiereAsiento(boolean requiereAsiento) {
        this.requiereAsiento = requiereAsiento;
    }

    public EstadoEvento getEstado() {
        return estado;
    }

    public void setEstado(EstadoEvento estado) {
        this.estado = estado;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public Instant getFechaCancelacion() {
        return fechaCancelacion;
    }

    public void setFechaCancelacion(Instant fechaCancelacion) {
        this.fechaCancelacion = fechaCancelacion;
    }

    public Long getCanceladoPor() {
        return canceladoPor;
    }

    public void setCanceladoPor(Long canceladoPor) {
        this.canceladoPor = canceladoPor;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public void setMotivoCancelacion(String motivoCancelacion) {
        this.motivoCancelacion = motivoCancelacion;
    }

    public BigDecimal getComisionCancelacion() {
        return comisionCancelacion;
    }

    public void setComisionCancelacion(BigDecimal comisionCancelacion) {
        this.comisionCancelacion = comisionCancelacion;
    }
}
