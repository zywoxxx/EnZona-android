package mx.uv.enzona.usuarios;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Cuenta de la plataforma. La contraseña se guarda como hash BCrypt y la CURP
 * cifrada (AES-GCM) con un índice ciego para la unicidad (RNF-01, RNF-03).
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String correo;

    private String telefono;

    /** Columna `curp`: texto cifrado, nunca la CURP en claro. */
    @Column(name = "curp")
    private String curpCifrada;

    @Column(name = "curp_hash")
    private String curpHash;

    private String contrasenaHash;

    private boolean correoVerificado;

    private boolean telefonoVerificado;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_usuario")
    private EstadoUsuario estado;

    private Instant fechaRegistro;

    /** Filas de `usuario_rol`: un usuario puede ser asistente y organizador a la vez. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<RolEntidad> roles = new LinkedHashSet<>();

    public Set<String> nombresDeRoles() {
        return roles.stream().sorted((a, b) -> Short.compare(a.getId(), b.getId())).map(RolEntidad::getNombre)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean tieneRol(Rol rol) {
        return roles.stream().anyMatch(r -> r.getNombre().equals(rol.name()));
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCurpCifrada() {
        return curpCifrada;
    }

    public void setCurpCifrada(String curpCifrada) {
        this.curpCifrada = curpCifrada;
    }

    public String getCurpHash() {
        return curpHash;
    }

    public void setCurpHash(String curpHash) {
        this.curpHash = curpHash;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public boolean isCorreoVerificado() {
        return correoVerificado;
    }

    public void setCorreoVerificado(boolean correoVerificado) {
        this.correoVerificado = correoVerificado;
    }

    public boolean isTelefonoVerificado() {
        return telefonoVerificado;
    }

    public void setTelefonoVerificado(boolean telefonoVerificado) {
        this.telefonoVerificado = telefonoVerificado;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public void setEstado(EstadoUsuario estado) {
        this.estado = estado;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(Instant fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public Set<RolEntidad> getRoles() {
        return roles;
    }

    public void setRoles(Set<RolEntidad> roles) {
        this.roles = roles;
    }
}
