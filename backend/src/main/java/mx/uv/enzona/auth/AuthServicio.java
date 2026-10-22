package mx.uv.enzona.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import mx.uv.enzona.auth.AuthDtos.LoginDto;
import mx.uv.enzona.auth.AuthDtos.OtpRespuesta;
import mx.uv.enzona.auth.AuthDtos.RegistroDto;
import mx.uv.enzona.auth.AuthDtos.RegistroRespuesta;
import mx.uv.enzona.auth.AuthDtos.SesionDto;
import mx.uv.enzona.common.config.PropiedadesEnZona;
import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.seguridad.JwtServicio;
import mx.uv.enzona.common.util.CifradoCurp;
import mx.uv.enzona.notificaciones.ServicioCorreo;
import mx.uv.enzona.usuarios.EstadoUsuario;
import mx.uv.enzona.usuarios.Rol;
import mx.uv.enzona.usuarios.RolRepositorio;
import mx.uv.enzona.usuarios.Usuario;
import mx.uv.enzona.usuarios.UsuarioRepositorio;
import mx.uv.enzona.usuarios.UsuarioServicio;

/**
 * Identidad y acceso (RF-01 a RF-03): registro con CURP, verificación con código
 * de un solo uso y sesión con JWT.
 *
 * SIMULACIÓN: no hay proveedor de correo ni de SMS. El código se registra en el
 * log del servidor y, solo en el perfil dev, se devuelve en la respuesta.
 */
@Service
public class AuthServicio {

    private static final Logger log = LoggerFactory.getLogger(AuthServicio.class);
    private static final int MAX_INTENTOS = 5;
    private static final String AVISO_SIMULACION =
            "Simulación: no hay proveedor de correo/SMS. El código aparece en el log del servidor.";

    private enum ResultadoOtp { VERIFICADO, SIN_CODIGO, EXPIRADO, BLOQUEADO, INCORRECTO, YA_ACTIVA }

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final OtpRepositorio otps;
    private final PasswordEncoder codificador;
    private final CifradoCurp cifrado;
    private final JwtServicio jwt;
    private final UsuarioServicio usuarioServicio;
    private final ServicioCorreo correo;
    private final PropiedadesEnZona propiedades;
    private final TransactionTemplate tx;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public AuthServicio(UsuarioRepositorio usuarios, RolRepositorio roles, OtpRepositorio otps,
            PasswordEncoder codificador, CifradoCurp cifrado, JwtServicio jwt, UsuarioServicio usuarioServicio,
            ServicioCorreo correo, PropiedadesEnZona propiedades, TransactionTemplate tx, Clock reloj) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.otps = otps;
        this.codificador = codificador;
        this.cifrado = cifrado;
        this.jwt = jwt;
        this.usuarioServicio = usuarioServicio;
        this.correo = correo;
        this.propiedades = propiedades;
        this.tx = tx;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------ registro

    public RegistroRespuesta registrar(RegistroDto datos) {
        if (!UsuarioServicio.contrasenaValida(datos.contrasena())) {
            throw ExcepcionEnZona.solicitudInvalida("CONTRASENA_DEBIL",
                    "La contraseña debe tener al menos 8 caracteres, con letras y números.");
        }
        String correoNormal = datos.correo().trim().toLowerCase();
        String curp = CifradoCurp.normalizar(datos.curp());
        String telefono = datos.telefono() == null || datos.telefono().isBlank() ? null : datos.telefono().trim();
        String indiceCurp = cifrado.indice(curp);

        String[] codigo = new String[1];
        Usuario creado = tx.execute(estado -> {
            if (usuarios.existsByCorreoIgnoreCase(correoNormal)) {
                throw ExcepcionEnZona.conflicto("CORREO_DUPLICADO", "Ya existe una cuenta con ese correo.");
            }
            if (usuarios.existsByCurpHash(indiceCurp)) {
                throw ExcepcionEnZona.conflicto("CURP_DUPLICADA", "Ya existe una cuenta registrada con esa CURP.");
            }
            if (telefono != null && usuarios.existsByTelefono(telefono)) {
                throw ExcepcionEnZona.conflicto("TELEFONO_DUPLICADO", "Ese teléfono ya está registrado en otra cuenta.");
            }
            Usuario u = new Usuario();
            u.setNombre(datos.nombre().trim());
            u.setCorreo(correoNormal);
            u.setTelefono(telefono);
            u.setCurpCifrada(cifrado.cifrar(curp));
            u.setCurpHash(indiceCurp);
            u.setContrasenaHash(codificador.encode(datos.contrasena()));
            u.setEstado(EstadoUsuario.PENDIENTE);
            u.setFechaRegistro(reloj.instant());
            u.setRoles(Set.of(roles.findByNombre(Rol.ASISTENTE.name()).orElseThrow()));
            Usuario guardado = usuarios.save(u);
            codigo[0] = nuevoOtp(guardado.getId());
            return guardado;
        });
        enviarCodigo(creado, codigo[0]);
        boolean devolver = propiedades.otp().devolverEnRespuesta();
        return new RegistroRespuesta(creado.getId(), creado.getCorreo(), creado.getEstado().name(),
                devolver ? codigo[0] : null, true, AVISO_SIMULACION);
    }

    /** Invalida los códigos anteriores y crea uno nuevo. Debe llamarse dentro de una transacción. */
    private String nuevoOtp(Long usuarioId) {
        otps.invalidarVigentes(usuarioId);
        String codigo = String.format("%06d", azar.nextInt(1_000_000));
        OtpVerificacion otp = new OtpVerificacion();
        otp.setUsuarioId(usuarioId);
        otp.setCodigoHash(codificador.encode(codigo));
        otp.setCreado(reloj.instant());
        otp.setExpira(reloj.instant().plus(propiedades.otp().minutosVigencia(), ChronoUnit.MINUTES));
        otps.save(otp);
        return codigo;
    }

    private void enviarCodigo(Usuario u, String codigo) {
        log.info("SIMULACIÓN de envío de OTP · cuenta {} ({}) · código {} · vigencia {} min",
                u.getId(), u.getCorreo(), codigo, propiedades.otp().minutosVigencia());
        correo.enviar(u.getCorreo(), "Tu código de verificación de EnZona",
                "Tu código es " + codigo + ". Vence en " + propiedades.otp().minutosVigencia() + " minutos.");
    }

    // ------------------------------------------------------------ OTP

    public OtpRespuesta verificar(Long usuarioId, String codigo) {
        ResultadoOtp r = tx.execute(estado -> {
            Usuario u = usuarios.findById(usuarioId).orElse(null);
            if (u == null) {
                return ResultadoOtp.SIN_CODIGO;
            }
            if (u.getEstado() == EstadoUsuario.ACTIVO) {
                return ResultadoOtp.YA_ACTIVA;
            }
            Optional<OtpVerificacion> vigente = otps.findFirstByUsuarioIdAndUsadoFalseOrderByCreadoDesc(usuarioId);
            if (vigente.isEmpty()) {
                return ResultadoOtp.SIN_CODIGO;
            }
            OtpVerificacion otp = vigente.get();
            if (otp.getExpira().isBefore(reloj.instant())) {
                return ResultadoOtp.EXPIRADO;
            }
            if (otp.getIntentos() >= MAX_INTENTOS) {
                return ResultadoOtp.BLOQUEADO;
            }
            if (!codificador.matches(codigo, otp.getCodigoHash())) {
                // El intento fallido se guarda: por eso el resultado se devuelve y el error se lanza fuera
                otp.setIntentos(otp.getIntentos() + 1);
                otps.save(otp);
                return ResultadoOtp.INCORRECTO;
            }
            otp.setUsado(true);
            otps.save(otp);
            if (u.getEstado() == EstadoUsuario.PENDIENTE) {
                u.setEstado(EstadoUsuario.ACTIVO);
            }
            u.setCorreoVerificado(true);
            usuarios.save(u);
            return ResultadoOtp.VERIFICADO;
        });
        return switch (r) {
            case VERIFICADO, YA_ACTIVA -> new OtpRespuesta(true, null, false, null);
            case INCORRECTO -> throw ExcepcionEnZona.regla("OTP_INCORRECTO",
                    "El código no es correcto. Revísalo e inténtalo de nuevo.");
            case EXPIRADO -> throw ExcepcionEnZona.regla("OTP_EXPIRADO", "El código venció. Solicita uno nuevo.");
            case BLOQUEADO -> throw ExcepcionEnZona.regla("OTP_BLOQUEADO",
                    "Se agotaron los intentos para este código. Solicita uno nuevo.");
            case SIN_CODIGO -> throw ExcepcionEnZona.regla("OTP_SIN_CODIGO",
                    "No hay un código vigente para esta cuenta. Solicita uno nuevo.");
        };
    }

    public OtpRespuesta reenviar(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId)
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("La cuenta no existe."));
        if (u.getEstado() != EstadoUsuario.PENDIENTE) {
            throw ExcepcionEnZona.conflicto("CUENTA_YA_VERIFICADA", "Esta cuenta ya está verificada.");
        }
        String codigo = tx.execute(estado -> nuevoOtp(usuarioId));
        enviarCodigo(u, codigo);
        return new OtpRespuesta(false, propiedades.otp().devolverEnRespuesta() ? codigo : null, true, AVISO_SIMULACION);
    }

    // ------------------------------------------------------------ sesión

    public SesionDto iniciarSesion(LoginDto datos) {
        String id = datos.identificador().trim();
        Optional<Usuario> encontrado;
        if (id.contains("@")) {
            encontrado = usuarios.findByCorreoIgnoreCase(id);
        } else if (id.matches("^\\d{10}$")) {
            encontrado = usuarios.findByTelefono(id);
        } else {
            encontrado = usuarios.findByCurpHash(cifrado.indice(id));
        }
        // Mismo mensaje si la cuenta no existe o la contraseña es incorrecta: no se revela cuál de las dos falló
        Usuario u = encontrado.filter(c -> codificador.matches(datos.contrasena(), c.getContrasenaHash()))
                .orElseThrow(() -> new ExcepcionEnZona(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "CREDENCIALES_INVALIDAS", "El correo o la contraseña no son correctos."));

        if (u.getEstado() == EstadoUsuario.PENDIENTE) {
            throw ExcepcionEnZona.prohibido("Tu cuenta aún no está verificada. Escribe el código que te enviamos.")
                    .con("codigo", "CUENTA_NO_VERIFICADA").con("usuarioId", u.getId());
        }
        if (u.getEstado() == EstadoUsuario.BLOQUEADO) {
            throw ExcepcionEnZona.prohibido("Tu cuenta está bloqueada. Contacta al administrador.")
                    .con("codigo", "CUENTA_BLOQUEADA");
        }
        Set<String> nombres = u.nombresDeRoles();
        if (Boolean.TRUE.equals(datos.accesoPersonal()) && nombres.stream().allMatch(Rol.ASISTENTE.name()::equals)) {
            throw ExcepcionEnZona.prohibido(
                    "Este acceso es para organizadores y personal. Entra por el acceso de asistentes.")
                    .con("codigo", "SIN_ROL_DE_PERSONAL");
        }
        JwtServicio.TokenEmitido token = jwt.emitir(u.getId(), u.getNombre(), nombres);
        return new SesionDto(token.token(), "Bearer", token.expira(), usuarioServicio.aDto(u));
    }

    public Instant ahora() {
        return reloj.instant();
    }
}
