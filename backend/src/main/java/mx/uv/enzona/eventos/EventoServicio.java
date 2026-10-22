package mx.uv.enzona.eventos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.common.util.FormatoFecha;
import mx.uv.enzona.eventos.EventoConsultas.Filtro;
import mx.uv.enzona.eventos.EventoDtos.AsientoDto;
import mx.uv.enzona.eventos.EventoDtos.AsientosEntradaDto;
import mx.uv.enzona.eventos.EventoDtos.CategoriaDto;
import mx.uv.enzona.eventos.EventoDtos.EventoDto;
import mx.uv.enzona.eventos.EventoDtos.EventoEntradaDto;
import mx.uv.enzona.eventos.EventoDtos.PersonalDto;
import mx.uv.enzona.eventos.EventoDtos.ReporteDto;
import mx.uv.enzona.eventos.EventoDtos.ReporteTipoDto;
import mx.uv.enzona.eventos.EventoDtos.SeccionDto;
import mx.uv.enzona.eventos.EventoDtos.TipoBoletoDto;
import mx.uv.enzona.eventos.EventoDtos.TipoEntradaDto;
import mx.uv.enzona.eventos.Repositorios.AsientoRepositorio;
import mx.uv.enzona.eventos.Repositorios.CategoriaRepositorio;
import mx.uv.enzona.eventos.Repositorios.EventoRepositorio;
import mx.uv.enzona.eventos.Repositorios.TipoBoletoRepositorio;
import mx.uv.enzona.notificaciones.NotificacionServicio;
import mx.uv.enzona.notificaciones.TipoNotificacion;
import mx.uv.enzona.usuarios.Rol;
import mx.uv.enzona.usuarios.Usuario;
import mx.uv.enzona.usuarios.UsuarioRepositorio;
import mx.uv.enzona.usuarios.UsuarioServicio;

/**
 * Ciclo de vida del evento (RF-05 a RF-09, RF-13, RF-19): catálogo, alta,
 * edición con políticas, publicación, plano de asientos y personal de acceso.
 */
@Service
public class EventoServicio {

    private final EventoRepositorio eventos;
    private final TipoBoletoRepositorio tipos;
    private final AsientoRepositorio asientos;
    private final CategoriaRepositorio categorias;
    private final EventoConsultas consultas;
    private final UsuarioRepositorio usuarios;
    private final UsuarioServicio usuarioServicio;
    private final NotificacionServicio notificaciones;
    private final FormatoFecha formato;
    private final JdbcClient jdbc;
    private final Clock reloj;

    public EventoServicio(EventoRepositorio eventos, TipoBoletoRepositorio tipos, AsientoRepositorio asientos,
            CategoriaRepositorio categorias, EventoConsultas consultas, UsuarioRepositorio usuarios,
            UsuarioServicio usuarioServicio, NotificacionServicio notificaciones, FormatoFecha formato,
            JdbcClient jdbc, Clock reloj) {
        this.eventos = eventos;
        this.tipos = tipos;
        this.asientos = asientos;
        this.categorias = categorias;
        this.consultas = consultas;
        this.usuarios = usuarios;
        this.usuarioServicio = usuarioServicio;
        this.notificaciones = notificaciones;
        this.formato = formato;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ==================================================================
    //  Lectura
    // ==================================================================

    /** Acepta un instante ISO-8601 con zona o una fecha (`2026-10-03`) en la zona del área de estudio. */
    private Instant fecha(String texto, boolean finDelDia) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(texto.trim()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                LocalDate dia = LocalDate.parse(texto.trim());
                return (finDelDia ? dia.plusDays(1).atStartOfDay(formato.zona()).minusNanos(1)
                        : dia.atStartOfDay(formato.zona())).toInstant();
            } catch (DateTimeParseException e2) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                        "La fecha «" + texto + "» no es válida. Usa ISO-8601, por ejemplo 2026-10-03 o 2026-10-03T19:00:00-06:00.");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<EventoDto> catalogo(String ciudad, String categoria, String desde, String hasta, Double lat,
            Double lng, Double radioKm, String q, String precio, String lugar, Integer limite) {
        if ((lat == null) != (lng == null)) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Envía lat y lng juntos, o ninguno.");
        }
        if (lat != null && (lat < -90 || lat > 90 || lng < -180 || lng > 180)) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Las coordenadas están fuera de rango.");
        }
        if (radioKm != null && radioKm <= 0) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "El radio debe ser mayor que cero.");
        }
        Boolean esDePago = precio == null || precio.isBlank() ? null
                : switch (precio.trim().toUpperCase(Locale.ROOT)) {
                    case "GRATIS" -> Boolean.FALSE;
                    case "DE_PAGO" -> Boolean.TRUE;
                    default -> throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                            "El filtro de precio admite GRATIS o DE_PAGO.");
                };
        return consultas.publicados(new Filtro(ciudad, categoria, fecha(desde, false), fecha(hasta, true), lat, lng,
                radioKm, q, esDePago, lugar, limite == null ? 100 : limite));
    }

    /** Los borradores solo los ven su organizador y la administración. */
    @Transactional(readOnly = true)
    public EventoDto detalle(Long id, UsuarioActual yo) {
        EventoDto e = consultas.porId(id).orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        if ("BORRADOR".equals(e.estado())
                && (yo == null || !(yo.esAdmin() || yo.id().equals(e.organizadorId())))) {
            throw ExcepcionEnZona.noEncontrado("El evento no existe.");
        }
        return e;
    }

    @Transactional(readOnly = true)
    public List<EventoDto> misEventos(Long organizadorId) {
        return consultas.deOrganizador(organizadorId);
    }

    @Transactional(readOnly = true)
    public List<TipoBoletoDto> tiposDe(Long eventoId) {
        exigir(eventoId);
        return tipos.findByEventoIdOrderByIdAsc(eventoId).stream().map(TipoBoletoDto::de).toList();
    }

    @Transactional(readOnly = true)
    public List<AsientoDto> asientosDe(Long eventoId) {
        exigir(eventoId);
        return asientos.findByEventoIdOrderByIdAsc(eventoId).stream().map(AsientoDto::de).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaDto> categorias() {
        return categorias.findAllByOrderByNombreAsc().stream().map(c -> new CategoriaDto(c.getId(), c.getNombre()))
                .toList();
    }

    @Transactional
    public CategoriaDto crearCategoria(String nombre) {
        String limpio = nombre.trim();
        if (categorias.findByNombreIgnoreCase(limpio).isPresent()) {
            throw ExcepcionEnZona.conflicto("CATEGORIA_DUPLICADA", "Ya existe una categoría con ese nombre.");
        }
        Categoria c = new Categoria();
        c.setNombre(limpio);
        c = categorias.save(c);
        return new CategoriaDto(c.getId(), c.getNombre());
    }

    public Evento exigir(Long id) {
        return eventos.findById(id).orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
    }

    /** El organizador dueño del evento o un administrador. */
    public void exigirGestion(Evento e, UsuarioActual yo) {
        if (!yo.esAdmin() && !e.getOrganizadorId().equals(yo.id())) {
            throw ExcepcionEnZona.prohibido("Solo el organizador del evento o un administrador pueden gestionarlo.");
        }
    }

    // ==================================================================
    //  Alta y edición
    // ==================================================================

    private Integer categoriaDe(EventoEntradaDto d) {
        if (d.categoriaId() != null) {
            return categorias.findById(d.categoriaId())
                    .orElseThrow(() -> ExcepcionEnZona.solicitudInvalida("VALIDACION", "La categoría no existe."))
                    .getId();
        }
        if (d.categoria() == null || d.categoria().isBlank()) {
            return null;
        }
        return categorias.findByNombreIgnoreCase(d.categoria().trim())
                .orElseThrow(() -> ExcepcionEnZona.solicitudInvalida("VALIDACION",
                        "La categoría «" + d.categoria() + "» no existe."))
                .getId();
    }

    private static void validarCoordenadas(Double latitud, Double longitud) {
        if ((latitud == null) != (longitud == null)) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Latitud y longitud van juntas o ninguna.");
        }
    }

    private static BigDecimal coordenada(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    private static void validarFechas(Instant inicio, Instant fin) {
        if (fin != null && fin.isBefore(inicio)) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "La fecha de fin no puede ser anterior a la de inicio.");
        }
    }

    private static String textoONulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Transactional
    public EventoDto crear(EventoEntradaDto d, UsuarioActual yo) {
        boolean esDePago = Boolean.TRUE.equals(d.esDePago());
        boolean conAsientos = Boolean.TRUE.equals(d.requiereAsiento());
        validarCoordenadas(d.latitud(), d.longitud());
        Instant inicio = d.fechaHoraInicio().toInstant();
        Instant fin = d.fechaHoraFin() == null ? null : d.fechaHoraFin().toInstant();
        validarFechas(inicio, fin);

        List<SeccionDto> secciones = d.secciones() == null ? List.of() : d.secciones();
        if (conAsientos && secciones.isEmpty()) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                    "Un evento con asientos numerados necesita al menos una sección con filas y asientos.");
        }
        int aforo = conAsientos ? secciones.stream().mapToInt(s -> s.filas() * s.asientosPorFila()).sum()
                : (d.aforo() == null ? 0 : d.aforo());
        if (aforo <= 0) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "El aforo debe ser mayor que cero.");
        }
        List<TipoEntradaDto> tiposPedidos = tiposPara(d, esDePago, aforo);

        Evento e = new Evento();
        e.setOrganizadorId(yo.id());
        e.setCategoriaId(categoriaDe(d));
        e.setNombre(d.nombre().trim());
        e.setDescripcion(textoONulo(d.descripcion()));
        e.setLugar(d.lugar().trim());
        e.setDireccion(textoONulo(d.direccion()));
        e.setCiudad(d.ciudad() == null || d.ciudad().isBlank() ? "Orizaba" : d.ciudad().trim());
        e.setLatitud(coordenada(d.latitud()));
        e.setLongitud(coordenada(d.longitud()));
        e.setFechaHoraInicio(inicio);
        e.setFechaHoraFin(fin);
        e.setAforo(aforo);
        e.setEsDePago(esDePago);
        e.setRequiereAsiento(conAsientos);
        e.setEstado(EstadoEvento.BORRADOR);
        e.setFechaCreacion(reloj.instant());
        e.setComisionCancelacion(BigDecimal.ZERO.setScale(2));
        e = eventos.save(e);

        for (TipoEntradaDto t : tiposPedidos) {
            TipoBoleto tipo = new TipoBoleto();
            tipo.setEventoId(e.getId());
            tipo.setNombre(t.nombre().trim());
            tipo.setPrecio(FormatoFecha.mxn(t.precio()));
            tipo.setCantidadTotal(t.cantidad());
            tipo.setCantidadDisponible(t.cantidad());
            tipos.save(tipo);
        }
        if (conAsientos) {
            generarAsientos(e.getId(), secciones);
        }
        if (Boolean.TRUE.equals(d.publicar())) {
            publicarInterno(e);
        }
        return consultas.porId(e.getId()).orElseThrow();
    }

    /** Tipos de boleto del evento: los enviados o uno por defecto con todo el aforo. */
    private static List<TipoEntradaDto> tiposPara(EventoEntradaDto d, boolean esDePago, int aforo) {
        if (d.tipos() != null && !d.tipos().isEmpty()) {
            long distintos = d.tipos().stream().map(t -> t.nombre().trim().toLowerCase(Locale.ROOT)).distinct().count();
            if (distintos != d.tipos().size()) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Hay tipos de boleto con el mismo nombre.");
            }
            int suma = d.tipos().stream().mapToInt(TipoEntradaDto::cantidad).sum();
            if (suma != aforo) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                        "La suma de los tipos de boleto (" + suma + ") debe ser igual al aforo (" + aforo + ").");
            }
            boolean hayPrecio = d.tipos().stream().anyMatch(t -> t.precio().signum() > 0);
            if (esDePago && !hayPrecio) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                        "Un evento de pago necesita al menos un tipo de boleto con precio mayor que cero.");
            }
            if (!esDePago && hayPrecio) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                        "Un evento gratuito no puede tener tipos de boleto con precio.");
            }
            return d.tipos();
        }
        if (esDePago) {
            if (d.precio() == null || d.precio().signum() <= 0) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                        "Indica el precio del boleto (mayor que cero) para un evento de pago.");
            }
            return List.of(new TipoEntradaDto("General", d.precio(), aforo));
        }
        return List.of(new TipoEntradaDto("Entrada general", BigDecimal.ZERO, aforo));
    }

    private int generarAsientos(Long eventoId, List<SeccionDto> secciones) {
        long distintas = secciones.stream().map(s -> s.nombre().trim().toLowerCase(Locale.ROOT)).distinct().count();
        if (distintas != secciones.size()) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Hay secciones con el mismo nombre.");
        }
        int total = 0;
        for (SeccionDto s : secciones) {
            total += PlanoAsientos.generar(jdbc, eventoId, s.nombre().trim(), s.filas(), s.asientosPorFila());
        }
        return total;
    }

    /**
     * Edición con las políticas de RF-13: el aforo no baja de lo ya entregado
     * (CP-15), un evento publicado conserva su ubicación y, si cambia la fecha,
     * se avisa a quien tiene boleto vigente.
     */
    @Transactional
    public EventoDto modificar(Long id, EventoEntradaDto d, UsuarioActual yo) {
        Evento e = eventos.buscarParaModificar(id)
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        exigirGestion(e, yo);
        if (e.getEstado() == EstadoEvento.CANCELADO || e.getEstado() == EstadoEvento.REALIZADO) {
            throw ExcepcionEnZona.conflicto("EVENTO_CERRADO",
                    "Un evento " + (e.getEstado() == EstadoEvento.CANCELADO ? "cancelado" : "finalizado")
                            + " ya no se puede modificar.");
        }
        validarCoordenadas(d.latitud(), d.longitud());
        Instant inicio = d.fechaHoraInicio().toInstant();
        Instant fin = d.fechaHoraFin() == null ? null : d.fechaHoraFin().toInstant();
        validarFechas(inicio, fin);
        if (e.getEstado() == EstadoEvento.PUBLICADO && d.latitud() == null) {
            throw ExcepcionEnZona.regla("UBICACION_OBLIGATORIA", "Un evento publicado necesita su ubicación en el mapa.");
        }
        boolean cambiaFecha = !inicio.equals(e.getFechaHoraInicio());
        if (cambiaFecha && e.getEstado() == EstadoEvento.PUBLICADO && !inicio.isAfter(reloj.instant())) {
            throw ExcepcionEnZona.regla("FECHA_PASADA", "La nueva fecha de inicio debe ser futura.");
        }

        List<TipoBoleto> delEvento = tipos.findByEventoIdOrderByIdAsc(id);
        int vendidos = delEvento.stream().mapToInt(t -> t.getCantidadTotal() - t.getCantidadDisponible()).sum();
        if (!e.isRequiereAsiento() && d.aforo() != null && d.aforo() != e.getAforo()) {
            int nuevoAforo = d.aforo();
            if (nuevoAforo <= 0) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "El aforo debe ser mayor que cero.");
            }
            if (nuevoAforo < vendidos) {
                throw ExcepcionEnZona.regla("AFORO_MENOR_A_LO_ENTREGADO", "No puedes reducir el aforo a " + nuevoAforo
                        + ": ya hay " + vendidos + " boletos entregados.").con("vendidos", vendidos);
            }
            ajustarInventario(delEvento, nuevoAforo - e.getAforo());
            e.setAforo(nuevoAforo);
        }
        if (d.precio() != null && e.isEsDePago() && delEvento.size() == 1
                && d.precio().compareTo(delEvento.get(0).getPrecio()) != 0) {
            if (d.precio().signum() <= 0) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "El precio debe ser mayor que cero.");
            }
            if (vendidos > 0) {
                throw ExcepcionEnZona.regla("PRECIO_CON_VENTAS",
                        "El precio no se puede cambiar: ya hay boletos vendidos a " + FormatoFecha.importe(delEvento.get(0).getPrecio()) + ".");
            }
            delEvento.get(0).setPrecio(FormatoFecha.mxn(d.precio()));
            tipos.save(delEvento.get(0));
        }

        e.setNombre(d.nombre().trim());
        e.setDescripcion(textoONulo(d.descripcion()));
        e.setLugar(d.lugar().trim());
        e.setDireccion(textoONulo(d.direccion()));
        if (d.ciudad() != null && !d.ciudad().isBlank()) {
            e.setCiudad(d.ciudad().trim());
        }
        if (d.categoriaId() != null || (d.categoria() != null && !d.categoria().isBlank())) {
            e.setCategoriaId(categoriaDe(d));
        }
        e.setLatitud(coordenada(d.latitud()));
        e.setLongitud(coordenada(d.longitud()));
        e.setFechaHoraInicio(inicio);
        e.setFechaHoraFin(fin);
        eventos.saveAndFlush(e);

        if (cambiaFecha) {
            avisarCambioDeFecha(e);
        }
        return consultas.porId(id).orElseThrow();
    }

    /** Reparte la diferencia de aforo sobre los tipos: crece en el primero; al reducir, quita de donde hay disponibles. */
    private void ajustarInventario(List<TipoBoleto> delEvento, int diferencia) {
        if (delEvento.isEmpty() || diferencia == 0) {
            return;
        }
        if (diferencia > 0) {
            TipoBoleto t = delEvento.get(0);
            t.setCantidadTotal(t.getCantidadTotal() + diferencia);
            t.setCantidadDisponible(t.getCantidadDisponible() + diferencia);
            tipos.save(t);
            return;
        }
        int porQuitar = -diferencia;
        for (int i = delEvento.size() - 1; i >= 0 && porQuitar > 0; i--) {
            TipoBoleto t = delEvento.get(i);
            int quita = Math.min(porQuitar, t.getCantidadDisponible());
            t.setCantidadTotal(t.getCantidadTotal() - quita);
            t.setCantidadDisponible(t.getCantidadDisponible() - quita);
            tipos.save(t);
            porQuitar -= quita;
        }
    }

    private void avisarCambioDeFecha(Evento e) {
        record Titular(long usuarioId, long boletoId) {
        }
        List<Titular> titulares = jdbc.sql("""
                SELECT o.asistente_id, MIN(b.id) AS boleto_id
                  FROM boleto b JOIN orden o ON o.id = b.orden_id
                 WHERE o.evento_id = :evento AND b.estado = 'VALIDO'
                 GROUP BY o.asistente_id
                """).param("evento", e.getId())
                .query((rs, n) -> new Titular(rs.getLong("asistente_id"), rs.getLong("boleto_id"))).list();
        for (Titular t : titulares) {
            notificaciones.crear(t.usuarioId(), e.getId(), TipoNotificacion.FECHA_CAMBIADA,
                    "«" + e.getNombre() + "» cambió de fecha: ahora es el " + formato.largo(e.getFechaHoraInicio())
                            + ". Tus boletos siguen siendo válidos.",
                    t.boletoId());
        }
    }

    // ==================================================================
    //  Publicación y cierre
    // ==================================================================

    private void publicarInterno(Evento e) {
        if (e.getEstado() != EstadoEvento.BORRADOR) {
            throw ExcepcionEnZona.conflicto("ESTADO_INVALIDO", "Solo se puede publicar un evento en borrador.");
        }
        if (e.getLatitud() == null || e.getLongitud() == null) {
            throw ExcepcionEnZona.regla("UBICACION_OBLIGATORIA", "Para publicar, fija la ubicación del evento en el mapa.");
        }
        if (!e.getFechaHoraInicio().isAfter(reloj.instant())) {
            throw ExcepcionEnZona.regla("FECHA_PASADA", "Para publicar, la fecha de inicio debe ser futura.");
        }
        if (tipos.findByEventoIdOrderByIdAsc(e.getId()).isEmpty()) {
            throw ExcepcionEnZona.regla("SIN_TIPOS_DE_BOLETO", "Para publicar, el evento necesita al menos un tipo de boleto.");
        }
        e.setEstado(EstadoEvento.PUBLICADO);
        eventos.saveAndFlush(e);
    }

    @Transactional
    public EventoDto publicar(Long id, UsuarioActual yo) {
        Evento e = eventos.buscarParaModificar(id)
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        exigirGestion(e, yo);
        publicarInterno(e);
        return consultas.porId(id).orElseThrow();
    }

    /** Marca como finalizado un evento publicado que ya comenzó (no se puede cancelar). */
    @Transactional
    public EventoDto marcarRealizado(Long id, UsuarioActual yo) {
        Evento e = eventos.buscarParaModificar(id)
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        exigirGestion(e, yo);
        if (e.getEstado() != EstadoEvento.PUBLICADO) {
            throw ExcepcionEnZona.conflicto("ESTADO_INVALIDO", "Solo un evento publicado puede marcarse como finalizado.");
        }
        if (e.getFechaHoraInicio().isAfter(reloj.instant())) {
            throw ExcepcionEnZona.regla("EVENTO_NO_INICIADO",
                    "El evento aún no comienza. Si no se va a realizar, cancélalo.");
        }
        e.setEstado(EstadoEvento.REALIZADO);
        eventos.saveAndFlush(e);
        // Los boletos que nadie usó dejan de tener vigencia
        jdbc.sql("""
                UPDATE boleto SET estado = 'EXPIRADO'
                 WHERE estado = 'VALIDO' AND orden_id IN (SELECT id FROM orden WHERE evento_id = :evento)
                """).param("evento", id).update();
        return consultas.porId(id).orElseThrow();
    }

    // ==================================================================
    //  Plano de asientos (RF-09)
    // ==================================================================

    private boolean tieneBoletos(Long eventoId) {
        // Cuentan los boletos emitidos y las órdenes vivas; una orden cancelada por pago rechazado no bloquea el plano
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM boleto b JOIN orden o ON o.id = b.orden_id WHERE o.evento_id = :evento)
                    OR EXISTS (SELECT 1 FROM orden WHERE evento_id = :evento AND estado IN ('PENDIENTE', 'PAGADA'))
                """).param("evento", eventoId).query(Boolean.class).single();
    }

    @Transactional
    public EventoDto cambiarAsientos(Long id, AsientosEntradaDto d, UsuarioActual yo) {
        Evento e = eventos.buscarParaModificar(id)
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        exigirGestion(e, yo);
        if (e.getEstado() == EstadoEvento.CANCELADO || e.getEstado() == EstadoEvento.REALIZADO) {
            throw ExcepcionEnZona.conflicto("EVENTO_CERRADO", "El evento ya no admite cambios.");
        }
        if (tieneBoletos(id)) {
            throw ExcepcionEnZona.conflicto("PLANO_CON_BOLETOS",
                    "No se puede cambiar el plano: este evento ya tiene órdenes o boletos emitidos.");
        }
        asientos.borrarDelEvento(id);
        if (Boolean.TRUE.equals(d.requiereAsiento())) {
            List<SeccionDto> secciones = d.secciones() == null ? List.of() : d.secciones();
            if (secciones.isEmpty()) {
                throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Indica al menos una sección con filas y asientos.");
            }
            int total = generarAsientos(id, secciones);
            List<TipoBoleto> delEvento = tipos.findByEventoIdOrderByIdAsc(id);
            if (delEvento.size() == 1) {
                delEvento.get(0).setCantidadTotal(total);
                delEvento.get(0).setCantidadDisponible(total);
                tipos.save(delEvento.get(0));
            } else if (delEvento.stream().mapToInt(TipoBoleto::getCantidadTotal).sum() != total) {
                throw ExcepcionEnZona.regla("TIPOS_NO_COINCIDEN", "El plano genera " + total
                        + " butacas y no coincide con la suma de los tipos de boleto del evento.");
            }
            e.setRequiereAsiento(true);
            e.setAforo(total);
        } else {
            e.setRequiereAsiento(false);
        }
        eventos.saveAndFlush(e);
        return consultas.porId(id).orElseThrow();
    }

    // ==================================================================
    //  Personal de acceso (el organizador designa validadores por correo)
    // ==================================================================

    @Transactional(readOnly = true)
    public List<PersonalDto> personalDe(Long eventoId, UsuarioActual yo) {
        exigirGestion(exigir(eventoId), yo);
        return listarPersonal(eventoId);
    }

    private List<PersonalDto> listarPersonal(Long eventoId) {
        return jdbc.sql("""
                SELECT u.id, u.nombre, u.correo, pa.designado
                  FROM evento_personal_acceso pa JOIN usuario u ON u.id = pa.usuario_id
                 WHERE pa.evento_id = :evento ORDER BY u.nombre
                """).param("evento", eventoId)
                .query((rs, n) -> new PersonalDto(rs.getLong("id"), rs.getString("nombre"), rs.getString("correo"),
                        rs.getTimestamp("designado").toInstant()))
                .list();
    }

    @Transactional
    public List<PersonalDto> designar(Long eventoId, String correo, UsuarioActual yo) {
        exigirGestion(exigir(eventoId), yo);
        Usuario u = usuarios.findByCorreoIgnoreCase(correo.trim()).orElseThrow(() -> ExcepcionEnZona.noEncontrado(
                "No hay ninguna cuenta registrada con ese correo. Pídele que se registre primero."));
        int filas = jdbc.sql("""
                INSERT INTO evento_personal_acceso (evento_id, usuario_id) VALUES (:evento, :usuario)
                ON CONFLICT DO NOTHING
                """).param("evento", eventoId).param("usuario", u.getId()).update();
        if (filas == 0) {
            throw ExcepcionEnZona.conflicto("YA_DESIGNADO", u.getNombre() + " ya es personal de acceso de este evento.");
        }
        usuarioServicio.asignarRol(u.getId(), Rol.VALIDADOR, true);
        return listarPersonal(eventoId);
    }

    @Transactional
    public List<PersonalDto> retirar(Long eventoId, Long usuarioId, UsuarioActual yo) {
        exigirGestion(exigir(eventoId), yo);
        int filas = jdbc.sql("DELETE FROM evento_personal_acceso WHERE evento_id = :evento AND usuario_id = :usuario")
                .param("evento", eventoId).param("usuario", usuarioId).update();
        if (filas == 0) {
            throw ExcepcionEnZona.noEncontrado("Esa persona no es personal de acceso de este evento.");
        }
        boolean sigueDesignado = jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM evento_personal_acceso WHERE usuario_id = :usuario)")
                .param("usuario", usuarioId).query(Boolean.class).single();
        if (!sigueDesignado) {
            // Sin eventos asignados, la cuenta deja de ser validadora (conserva sus demás roles)
            usuarioServicio.asignarRol(usuarioId, Rol.VALIDADOR, false);
        }
        return listarPersonal(eventoId);
    }

    public boolean esPersonalDe(Long eventoId, Long usuarioId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM evento_personal_acceso WHERE evento_id = :evento AND usuario_id = :usuario)
                """).param("evento", eventoId).param("usuario", usuarioId).query(Boolean.class).single();
    }

    // ==================================================================
    //  Reporte del organizador
    // ==================================================================

    @Transactional(readOnly = true)
    public ReporteDto reporte(Long eventoId, UsuarioActual yo) {
        exigirGestion(exigir(eventoId), yo);
        record Conteos(int emitidos, int validos, int usados, int cancelados) {
        }
        Conteos c = jdbc.sql("""
                SELECT COUNT(*) AS emitidos,
                       COUNT(*) FILTER (WHERE b.estado = 'VALIDO') AS validos,
                       COUNT(*) FILTER (WHERE b.estado = 'USADO') AS usados,
                       COUNT(*) FILTER (WHERE b.estado = 'CANCELADO') AS cancelados
                  FROM boleto b JOIN orden o ON o.id = b.orden_id WHERE o.evento_id = :evento
                """).param("evento", eventoId)
                .query((rs, n) -> new Conteos(rs.getInt("emitidos"), rs.getInt("validos"), rs.getInt("usados"),
                        rs.getInt("cancelados")))
                .single();
        BigDecimal ingresos = jdbc
                .sql("SELECT COALESCE(SUM(total), 0) FROM orden WHERE evento_id = :evento AND estado = 'PAGADA'")
                .param("evento", eventoId).query(BigDecimal.class).single();
        record Accesos(int permitidos, int rechazados) {
        }
        Accesos a = jdbc.sql("""
                SELECT COUNT(*) FILTER (WHERE resultado = 'PERMITIDO') AS permitidos,
                       COUNT(*) FILTER (WHERE resultado = 'RECHAZADO') AS rechazados
                  FROM validacion_acceso WHERE evento_id = :evento
                """).param("evento", eventoId)
                .query((rs, n) -> new Accesos(rs.getInt("permitidos"), rs.getInt("rechazados"))).single();
        List<ReporteTipoDto> porTipo = tipos.findByEventoIdOrderByIdAsc(eventoId).stream()
                .map(t -> new ReporteTipoDto(t.getId(), t.getNombre(), t.getPrecio().toPlainString(),
                        t.getCantidadTotal() - t.getCantidadDisponible(), t.getCantidadDisponible()))
                .toList();
        return new ReporteDto(eventoId, c.emitidos(), c.validos(), c.usados(), c.cancelados(),
                FormatoFecha.mxn(ingresos).toPlainString(), a.permitidos(), a.rechazados(), porTipo);
    }
}
