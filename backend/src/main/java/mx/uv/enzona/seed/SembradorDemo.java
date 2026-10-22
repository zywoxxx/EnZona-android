package mx.uv.enzona.seed;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import mx.uv.enzona.common.config.PropiedadesEnZona;
import mx.uv.enzona.common.util.CifradoCurp;
import mx.uv.enzona.eventos.Categoria;
import mx.uv.enzona.eventos.PlanoAsientos;
import mx.uv.enzona.eventos.EstadoEvento;
import mx.uv.enzona.eventos.Evento;
import mx.uv.enzona.eventos.Repositorios.AsientoRepositorio;
import mx.uv.enzona.eventos.Repositorios.CategoriaRepositorio;
import mx.uv.enzona.eventos.Repositorios.EventoRepositorio;
import mx.uv.enzona.eventos.Repositorios.TipoBoletoRepositorio;
import mx.uv.enzona.eventos.TipoBoleto;
import mx.uv.enzona.usuarios.EstadoUsuario;
import mx.uv.enzona.usuarios.Rol;
import mx.uv.enzona.usuarios.RolEntidad;
import mx.uv.enzona.usuarios.RolRepositorio;
import mx.uv.enzona.usuarios.Usuario;
import mx.uv.enzona.usuarios.UsuarioRepositorio;

/**
 * Datos de demostración: los mismos que trae `MockRepository` en la app.
 *
 * Se eligió un `CommandLineRunner` (perfil `seed`, incluido en `dev`) y no una
 * migración `R__seed_demo.sql` porque tres datos no se pueden escribir en SQL
 * sin los secretos del servidor: el hash BCrypt de la contraseña se genera con
 * sal aleatoria, y la CURP se cifra con AES-GCM y se indexa con HMAC usando
 * ENZONA_CURP_KEY. Una migración SQL obligaría a versionar datos derivados de
 * esa clave.
 *
 * Es re-ejecutable: cada fila se busca por su clave natural (correo, nombre de
 * la categoría, organizador + nombre del evento, evento + nombre del tipo) y
 * solo se crea si falta. No modifica filas existentes ni repone inventario.
 */
@Component
@Profile("seed")
@Order(10)
public class SembradorDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradorDemo.class);

    /** Contraseña de las cuentas de demostración. Solo existe bajo el perfil `seed`. */
    private static final String CONTRASENA_DEMO = "enzona123";

    private record Seccion(String nombre, int filas, int porFila) {
    }

    private record Tipo(String nombre, String precio, int cantidad) {
    }

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final CategoriaRepositorio categorias;
    private final EventoRepositorio eventos;
    private final TipoBoletoRepositorio tipos;
    private final AsientoRepositorio asientos;
    private final PasswordEncoder codificador;
    private final CifradoCurp cifrado;
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;
    private final Clock reloj;
    private final ZoneId zona;

    public SembradorDemo(UsuarioRepositorio usuarios, RolRepositorio roles, CategoriaRepositorio categorias,
            EventoRepositorio eventos, TipoBoletoRepositorio tipos, AsientoRepositorio asientos,
            PasswordEncoder codificador, CifradoCurp cifrado, TransactionTemplate tx, JdbcClient jdbc, Clock reloj,
            PropiedadesEnZona propiedades) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.categorias = categorias;
        this.eventos = eventos;
        this.tipos = tipos;
        this.asientos = asientos;
        this.codificador = codificador;
        this.cifrado = cifrado;
        this.tx = tx;
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.zona = ZoneId.of(propiedades.zonaHoraria());
    }

    @Override
    public void run(String... args) {
        tx.executeWithoutResult(t -> sembrar());
    }

    private void sembrar() {
        int[] creados = new int[3];

        // ---------- roles y categorías ----------
        for (Rol r : Rol.values()) {
            if (roles.findByNombre(r.name()).isEmpty()) {
                jdbc.sql("INSERT INTO rol (nombre) VALUES (:n) ON CONFLICT (nombre) DO NOTHING").param("n", r.name())
                        .update();
            }
        }
        for (String nombre : List.of("Académico", "Música", "Tecnología", "Teatro", "Deportes", "Cultural")) {
            if (categorias.findByNombreIgnoreCase(nombre).isEmpty()) {
                Categoria c = new Categoria();
                c.setNombre(nombre);
                categorias.save(c);
            }
        }

        // ---------- cuentas ----------
        Usuario organizador = cuenta("Nancy Cruz", "organizador@uv.mx", "ORGA850101HVZRRN04",
                Set.of(Rol.ORGANIZADOR, Rol.ASISTENTE), creados);
        Usuario validador = cuenta("Luis Puerta", "validador@uv.mx", "VALI900202HVZLRS08", Set.of(Rol.VALIDADOR),
                creados);
        cuenta("Admin EnZona", "admin@uv.mx", "ADMI880303HVZDMN01", Set.of(Rol.ADMIN, Rol.ASISTENTE), creados);
        cuenta("Ana Estudiante", "asistente@uv.mx", "ASIS020404MVZSNN09", Set.of(Rol.ASISTENTE), creados);

        // ---------- eventos (coordenadas reales de Orizaba, Veracruz) ----------
        List<Evento> demo = new ArrayList<>();
        demo.add(evento(organizador, "Feria de Emprendimiento FCAS",
                "Muestra de proyectos y startups universitarias. Stands, conferencias y networking con empresas de la región.",
                "Facultad de Negocios y Tecnologías", "Campus Orizaba, UV", "Académico",
                LocalDateTime.of(2026, 9, 25, 10, 0), null, false, 300, "18.850300", "-97.103600",
                List.of(new Tipo("Entrada general", "0.00", 300)), List.of(), creados));
        demo.add(evento(organizador, "Concierto: Orquesta Universitaria",
                "Gala de temporada con obras de Márquez y Revueltas. Una noche de música mexicana para toda la comunidad.",
                "Teatro Ignacio de la Llave", "Av. Oriente 6 esq. Sur 5, Centro", "Música",
                LocalDateTime.of(2026, 10, 3, 19, 0), LocalDateTime.of(2026, 10, 3, 21, 30), true, 250, "18.851180",
                "-97.101020",
                List.of(new Tipo("Planta baja", "120.00", 180), new Tipo("Balcón", "220.00", 70)),
                List.of(new Seccion("Planta baja", 10, 18), new Seccion("Balcón", 5, 14)), creados));
        demo.add(evento(organizador, "Hackathon UV 2026",
                "24 horas construyendo soluciones tecnológicas. Premios para los tres primeros lugares y comida incluida.",
                "Centro de Cómputo UV", "Campus Orizaba, UV", "Tecnología", LocalDateTime.of(2026, 10, 9, 17, 0),
                LocalDateTime.of(2026, 10, 10, 17, 0), false, 120, "18.848900", "-97.105200",
                List.of(new Tipo("Entrada general", "0.00", 120)), List.of(), creados));
        demo.add(evento(organizador, "Obra: La Casa de Bernarda Alba",
                "Puesta en escena del grupo de teatro universitario. Función con causa: lo recaudado apoya al taller de artes.",
                "Auditorio Principal", "Campus Orizaba, UV", "Teatro", LocalDateTime.of(2026, 10, 15, 18, 30), null,
                true, 128, "18.847600", "-97.106100", List.of(new Tipo("General", "60.00", 128)),
                List.of(new Seccion("Luneta", 8, 16)), creados));
        demo.add(evento(organizador, "Torneo de Fútbol Rápido Empresarial",
                "Liga relámpago entre empresas locales. Inscribe a tu equipo o asiste a apoyar; habrá venta de comida.",
                "Unidad Deportiva Sur", "Col. Rancho Grande", "Deportes", LocalDateTime.of(2026, 10, 18, 9, 0), null,
                true, 500, "18.839800", "-97.093100",
                List.of(new Tipo("General", "35.00", 400), new Tipo("Palco", "90.00", 100)), List.of(), creados));

        // ---------- personal de acceso: el validador de demostración atiende todos los eventos ----------
        for (Evento e : demo) {
            jdbc.sql("""
                    INSERT INTO evento_personal_acceso (evento_id, usuario_id) VALUES (:e, :u)
                    ON CONFLICT DO NOTHING
                    """).param("e", e.getId()).param("u", validador.getId()).update();
        }

        log.info("Datos de demostración: {} cuentas, {} eventos y {} asientos creados en esta ejecución "
                + "(0 significa que ya existían)", creados[0], creados[1], creados[2]);
    }

    private Usuario cuenta(String nombre, String correo, String curp, Set<Rol> rolesDeseados, int[] creados) {
        return usuarios.findByCorreoIgnoreCase(correo).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre(nombre);
            u.setCorreo(correo);
            u.setCurpCifrada(cifrado.cifrar(curp));
            u.setCurpHash(cifrado.indice(curp));
            u.setContrasenaHash(codificador.encode(CONTRASENA_DEMO));
            u.setEstado(EstadoUsuario.ACTIVO);
            u.setCorreoVerificado(true);
            u.setFechaRegistro(reloj.instant());
            Set<RolEntidad> conjunto = new LinkedHashSet<>();
            for (Rol r : rolesDeseados) {
                conjunto.add(roles.findByNombre(r.name()).orElseThrow());
            }
            u.setRoles(conjunto);
            creados[0]++;
            return usuarios.saveAndFlush(u);
        });
    }

    /**
     * Las fechas de `MockRepository` son fijas (septiembre y octubre de 2026). Si
     * al sembrar una de ellas ya pasó o está a menos de dos días, se recorre por
     * semanas completas hasta quedar en el futuro: conserva el día de la semana y
     * la hora, y el catálogo de demostración nunca nace con eventos vencidos.
     */
    private Instant fechaFutura(LocalDateTime fecha) {
        Instant minimo = reloj.instant().plus(2, ChronoUnit.DAYS);
        LocalDateTime f = fecha;
        while (f.atZone(zona).toInstant().isBefore(minimo)) {
            f = f.plusWeeks(1);
        }
        return f.atZone(zona).toInstant();
    }

    private Evento evento(Usuario organizador, String nombre, String descripcion, String lugar, String direccion,
            String categoria, LocalDateTime inicio, LocalDateTime fin, boolean esDePago, int aforo, String latitud,
            String longitud, List<Tipo> tiposDemo, List<Seccion> secciones, int[] creados) {
        Evento e = eventos.findByOrganizadorIdAndNombre(organizador.getId(), nombre).orElseGet(() -> {
            Instant comienzo = fechaFutura(inicio);
            Evento nuevo = new Evento();
            nuevo.setOrganizadorId(organizador.getId());
            nuevo.setCategoriaId(categorias.findByNombreIgnoreCase(categoria).map(Categoria::getId).orElse(null));
            nuevo.setNombre(nombre);
            nuevo.setDescripcion(descripcion);
            nuevo.setLugar(lugar);
            nuevo.setDireccion(direccion);
            nuevo.setCiudad("Orizaba");
            nuevo.setLatitud(new BigDecimal(latitud));
            nuevo.setLongitud(new BigDecimal(longitud));
            nuevo.setFechaHoraInicio(comienzo);
            // La duración original se conserva aunque la fecha se haya recorrido
            nuevo.setFechaHoraFin(fin == null ? null
                    : comienzo.plus(java.time.Duration.between(inicio, fin)));
            nuevo.setAforo(aforo);
            nuevo.setEsDePago(esDePago);
            nuevo.setRequiereAsiento(!secciones.isEmpty());
            nuevo.setEstado(EstadoEvento.PUBLICADO);
            nuevo.setFechaCreacion(reloj.instant());
            nuevo.setComisionCancelacion(new BigDecimal("0.00"));
            creados[1]++;
            return eventos.saveAndFlush(nuevo);
        });

        List<TipoBoleto> existentes = tipos.findByEventoIdOrderByIdAsc(e.getId());
        for (Tipo t : tiposDemo) {
            if (existentes.stream().noneMatch(x -> x.getNombre().equalsIgnoreCase(t.nombre()))) {
                TipoBoleto tipo = new TipoBoleto();
                tipo.setEventoId(e.getId());
                tipo.setNombre(t.nombre());
                tipo.setPrecio(new BigDecimal(t.precio()));
                tipo.setCantidadTotal(t.cantidad());
                tipo.setCantidadDisponible(t.cantidad());
                tipos.save(tipo);
            }
        }
        if (!secciones.isEmpty() && asientos.countByEventoId(e.getId()) == 0) {
            for (Seccion s : secciones) {
                creados[2] += PlanoAsientos.generar(jdbc, e.getId(), s.nombre(), s.filas(), s.porFila());
            }
        }
        return e;
    }
}
