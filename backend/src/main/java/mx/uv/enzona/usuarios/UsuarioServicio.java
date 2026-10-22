package mx.uv.enzona.usuarios;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.util.CifradoCurp;
import mx.uv.enzona.usuarios.UsuarioDtos.ActualizarPerfilDto;
import mx.uv.enzona.usuarios.UsuarioDtos.CargoDto;
import mx.uv.enzona.usuarios.UsuarioDtos.UsuarioDto;

/** Perfil, contraseña y roles de la cuenta (RF-03, RF-04). */
@Service
public class UsuarioServicio {

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final PasswordEncoder codificador;
    private final CifradoCurp cifrado;
    private final JdbcClient jdbc;

    public UsuarioServicio(UsuarioRepositorio usuarios, RolRepositorio roles, PasswordEncoder codificador,
            CifradoCurp cifrado, JdbcClient jdbc) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
        this.cifrado = cifrado;
        this.jdbc = jdbc;
    }

    public static boolean contrasenaValida(String c) {
        return c != null && c.length() >= 8 && c.chars().anyMatch(Character::isLetter)
                && c.chars().anyMatch(Character::isDigit);
    }

    public UsuarioDto aDto(Usuario u) {
        String curp = u.getCurpCifrada() == null ? null : CifradoCurp.enmascarar(cifrado.descifrar(u.getCurpCifrada()));
        return new UsuarioDto(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(), curp,
                List.copyOf(u.nombresDeRoles()), u.getEstado().name(), u.isCorreoVerificado(),
                u.isTelefonoVerificado(), u.getFechaRegistro());
    }

    public Usuario exigir(Long id) {
        return usuarios.findById(id).orElseThrow(() -> ExcepcionEnZona.noEncontrado("La cuenta no existe."));
    }

    @Transactional(readOnly = true)
    public UsuarioDto obtener(Long id) {
        return aDto(exigir(id));
    }

    @Transactional
    public UsuarioDto actualizarPerfil(Long id, ActualizarPerfilDto datos) {
        Usuario u = exigir(id);
        String telefono = datos.telefono() == null || datos.telefono().isBlank() ? null : datos.telefono().trim();
        if (telefono != null && !telefono.equals(u.getTelefono()) && usuarios.existsByTelefono(telefono)) {
            throw ExcepcionEnZona.conflicto("TELEFONO_DUPLICADO", "Ese teléfono ya está registrado en otra cuenta.");
        }
        if (telefono == null || !telefono.equals(u.getTelefono())) {
            u.setTelefonoVerificado(false);
        }
        u.setNombre(datos.nombre().trim());
        u.setTelefono(telefono);
        return aDto(usuarios.save(u));
    }

    @Transactional
    public void cambiarContrasena(Long id, String actual, String nueva) {
        Usuario u = exigir(id);
        if (!codificador.matches(actual, u.getContrasenaHash())) {
            throw ExcepcionEnZona.regla("CONTRASENA_ACTUAL_INCORRECTA", "La contraseña actual no es correcta.");
        }
        if (!contrasenaValida(nueva)) {
            throw ExcepcionEnZona.solicitudInvalida("CONTRASENA_DEBIL",
                    "La nueva contraseña debe tener al menos 8 caracteres, con letras y números.");
        }
        u.setContrasenaHash(codificador.encode(nueva));
        usuarios.save(u);
    }

    // ------------------------------------------------------------ roles

    private RolEntidad rol(Rol rol) {
        return roles.findByNombre(rol.name())
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + rol + " en el catálogo"));
    }

    /** Añade o quita una fila de `usuario_rol`. Una cuenta nunca queda sin roles. */
    @Transactional
    public Usuario asignarRol(Long usuarioId, Rol rol, boolean asignar) {
        Usuario u = exigir(usuarioId);
        Set<RolEntidad> actuales = new LinkedHashSet<>(u.getRoles());
        if (asignar) {
            actuales.add(rol(rol));
        } else {
            actuales.removeIf(r -> r.getNombre().equals(rol.name()));
        }
        if (actuales.isEmpty()) {
            actuales.add(rol(Rol.ASISTENTE));
        }
        u.setRoles(actuales);
        return usuarios.save(u);
    }

    @Transactional
    public Usuario reemplazarRoles(Long usuarioId, List<Rol> nuevos) {
        Usuario u = exigir(usuarioId);
        Set<RolEntidad> conjunto = new LinkedHashSet<>();
        nuevos.stream().distinct().forEach(r -> conjunto.add(rol(r)));
        u.setRoles(conjunto);
        return usuarios.save(u);
    }

    /** Alta autogestionada como organizador (RF-04): conserva el rol de asistente. */
    @Transactional
    public UsuarioDto convertirseEnOrganizador(Long usuarioId) {
        return aDto(asignarRol(usuarioId, Rol.ORGANIZADOR, true));
    }

    /**
     * Motivo que impide dejar de ser organizador, o null si procede (CP-ROL-03/04):
     * las cuentas de administración no usan esta vía, organizador no puede ser el
     * único rol, y no debe haber eventos publicados con boletos vigentes.
     */
    @Transactional(readOnly = true)
    public String motivoNoPuedeDejarOrganizador(Long usuarioId) {
        Usuario u = exigir(usuarioId);
        if (!u.tieneRol(Rol.ORGANIZADOR)) {
            return "Esta cuenta no es organizadora.";
        }
        if (u.tieneRol(Rol.ADMIN)) {
            return "Las cuentas de administración cambian sus roles desde Administración.";
        }
        if (u.getRoles().size() == 1) {
            return "Organizador es el único rol de esta cuenta.";
        }
        List<String> conBoletos = jdbc.sql("""
                SELECT e.nombre
                  FROM evento e
                 WHERE e.organizador_id = :id AND e.estado = 'PUBLICADO'
                   AND EXISTS (SELECT 1 FROM boleto b JOIN orden o ON o.id = b.orden_id
                                WHERE o.evento_id = e.id AND b.estado = 'VALIDO')
                 ORDER BY e.fecha_hora_inicio
                """).param("id", usuarioId).query(String.class).list();
        if (!conBoletos.isEmpty()) {
            return "Tienes " + conBoletos.size() + " evento(s) publicado(s) con boletos vigentes: "
                    + String.join(", ", conBoletos.stream().map(n -> "«" + n + "»").toList())
                    + ". Cancélalos o espera a que se realicen.";
        }
        return null;
    }

    @Transactional
    public UsuarioDto dejarDeSerOrganizador(Long usuarioId) {
        String motivo = motivoNoPuedeDejarOrganizador(usuarioId);
        if (motivo != null) {
            throw ExcepcionEnZona.conflicto("NO_PUEDE_DEJAR_ROL", motivo);
        }
        asignarRol(usuarioId, Rol.ORGANIZADOR, false);
        return aDto(asignarRol(usuarioId, Rol.ASISTENTE, true));
    }

    @Transactional(readOnly = true)
    public List<CargoDto> cargosDe(Long organizadorId) {
        return jdbc.sql("""
                SELECT id, evento_id, concepto, importe, pagado, fecha
                  FROM cargo_organizador WHERE organizador_id = :id ORDER BY fecha DESC
                """).param("id", organizadorId)
                .query((rs, n) -> CargoDto.de(rs.getLong("id"), (Long) rs.getObject("evento_id"),
                        rs.getString("concepto"), rs.getBigDecimal("importe"), rs.getBoolean("pagado"),
                        rs.getTimestamp("fecha").toInstant()))
                .list();
    }
}
