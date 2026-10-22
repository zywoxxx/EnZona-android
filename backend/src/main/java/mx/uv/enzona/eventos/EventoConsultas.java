package mx.uv.enzona.eventos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import mx.uv.enzona.eventos.EventoDtos.EventoDto;

/**
 * Consultas de lectura del catálogo. Se escriben en SQL para resolver en una
 * sola ida a la base la disponibilidad, el precio mínimo y la distancia
 * (fórmula de Haversine, RF-19 / CP-17) sin cargar entidades una a una.
 */
@Repository
public class EventoConsultas {

    /** Radio medio de la Tierra en km (IUGG). */
    private static final double RADIO_TIERRA_KM = 6371.0088;

    private static final String COLUMNAS = """
            SELECT e.id, e.nombre, e.descripcion, e.lugar, e.direccion, e.ciudad, e.latitud, e.longitud,
                   c.nombre AS categoria, e.categoria_id, e.fecha_hora_inicio, e.fecha_hora_fin,
                   e.es_de_pago, e.requiere_asiento, e.aforo, e.estado, e.organizador_id,
                   u.nombre AS organizador_nombre, e.fecha_creacion,
                   e.fecha_cancelacion, e.cancelado_por, cp.nombre AS cancelado_por_nombre,
                   e.motivo_cancelacion, e.comision_cancelacion,
                   COALESCE((SELECT SUM(t.cantidad_disponible) FROM tipo_boleto t WHERE t.evento_id = e.id), 0) AS inventario,
                   COALESCE((SELECT MIN(t.precio) FROM tipo_boleto t WHERE t.evento_id = e.id), 0) AS precio_desde,
                   (SELECT COUNT(*) FROM asiento a WHERE a.evento_id = e.id) AS asientos_totales,
                   (SELECT COUNT(*) FROM asiento a WHERE a.evento_id = e.id AND a.estado = 'DISPONIBLE') AS asientos_libres,
                   (SELECT COUNT(*) FROM boleto b JOIN orden o ON o.id = b.orden_id
                     WHERE o.evento_id = e.id AND b.estado IN ('VALIDO', 'USADO')) AS vendidos,
                   EXISTS (SELECT 1 FROM boleto b JOIN orden o ON o.id = b.orden_id WHERE o.evento_id = e.id) AS tiene_boletos,
                   %s AS distancia_km
              FROM evento e
              JOIN usuario u ON u.id = e.organizador_id
              LEFT JOIN categoria c ON c.id = e.categoria_id
              LEFT JOIN usuario cp ON cp.id = e.cancelado_por
            """;

    /** Distancia en km entre el punto (:lat, :lng) y el evento; NULL si el evento no tiene coordenadas. */
    private static final String HAVERSINE = """
            CASE WHEN e.latitud IS NOT NULL AND e.longitud IS NOT NULL THEN
                 %s * 2 * asin(sqrt(LEAST(1.0,
                     power(sin(radians((CAST(e.latitud AS double precision) - :lat) / 2)), 2)
                     + cos(radians(:lat)) * cos(radians(CAST(e.latitud AS double precision)))
                       * power(sin(radians((CAST(e.longitud AS double precision) - :lng) / 2)), 2))))
            END""".formatted(RADIO_TIERRA_KM);

    private final JdbcClient jdbc;

    public EventoConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Filtros del catálogo. Cualquier campo puede ser null. */
    public record Filtro(String ciudad, String categoria, Instant desde, Instant hasta, Double lat, Double lng,
            Double radioKm, String q, Boolean esDePago, String lugar, int limite) {
    }

    private static String seleccion(boolean conDistancia) {
        return COLUMNAS.formatted(conDistancia ? HAVERSINE : "CAST(NULL AS double precision)");
    }

    public List<EventoDto> publicados(Filtro f) {
        boolean conDistancia = f.lat() != null && f.lng() != null;
        Map<String, Object> p = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM (").append(seleccion(conDistancia))
                .append(" WHERE e.estado = 'PUBLICADO'");
        if (conDistancia) {
            p.put("lat", f.lat());
            p.put("lng", f.lng());
        }
        if (tiene(f.ciudad())) {
            sql.append(" AND lower(e.ciudad) = lower(:ciudad)");
            p.put("ciudad", f.ciudad().trim());
        }
        if (tiene(f.categoria())) {
            if (f.categoria().trim().matches("\\d+")) {
                sql.append(" AND e.categoria_id = :categoriaId");
                p.put("categoriaId", Integer.parseInt(f.categoria().trim()));
            } else {
                sql.append(" AND lower(c.nombre) = lower(:categoria)");
                p.put("categoria", f.categoria().trim());
            }
        }
        if (tiene(f.lugar())) {
            sql.append(" AND lower(e.lugar) = lower(:lugar)");
            p.put("lugar", f.lugar().trim());
        }
        if (f.desde() != null) {
            sql.append(" AND e.fecha_hora_inicio >= :desde");
            p.put("desde", Timestamp.from(f.desde()));
        }
        if (f.hasta() != null) {
            sql.append(" AND e.fecha_hora_inicio <= :hasta");
            p.put("hasta", Timestamp.from(f.hasta()));
        }
        if (f.esDePago() != null) {
            sql.append(" AND e.es_de_pago = :esDePago");
            p.put("esDePago", f.esDePago());
        }
        if (tiene(f.q())) {
            sql.append("""
                     AND (e.nombre ILIKE :q OR e.lugar ILIKE :q OR COALESCE(e.direccion, '') ILIKE :q
                          OR COALESCE(c.nombre, '') ILIKE :q)
                    """);
            p.put("q", "%" + f.q().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        sql.append(") t");
        if (conDistancia && f.radioKm() != null) {
            // Con radio solo entran los eventos cuya distancia se puede comprobar
            sql.append(" WHERE t.distancia_km <= :radio");
            p.put("radio", f.radioKm());
        }
        // Sin coordenadas el evento no tiene distancia: va al final, nunca con una distancia inventada (CP-17)
        sql.append(conDistancia ? " ORDER BY t.distancia_km ASC NULLS LAST, t.fecha_hora_inicio ASC, t.id ASC"
                : " ORDER BY t.fecha_hora_inicio ASC, t.id ASC");
        sql.append(" LIMIT :limite");
        p.put("limite", Math.max(1, Math.min(f.limite(), 200)));
        return jdbc.sql(sql.toString()).params(p).query(EventoConsultas::mapear).list();
    }

    public Optional<EventoDto> porId(Long id) {
        return jdbc.sql(seleccion(false) + " WHERE e.id = :id").param("id", id).query(EventoConsultas::mapear)
                .optional();
    }

    public List<EventoDto> deOrganizador(Long organizadorId) {
        return jdbc.sql(seleccion(false) + " WHERE e.organizador_id = :id ORDER BY e.fecha_hora_inicio ASC, e.id ASC")
                .param("id", organizadorId).query(EventoConsultas::mapear).list();
    }

    public List<EventoDto> todos() {
        return jdbc.sql(seleccion(false) + " ORDER BY e.fecha_hora_inicio ASC, e.id ASC")
                .query(EventoConsultas::mapear).list();
    }

    /** Eventos en los que la cuenta está designada como personal de acceso. */
    public List<EventoDto> dePersonalDeAcceso(Long usuarioId) {
        return jdbc.sql(seleccion(false) + """
                 WHERE e.estado IN ('PUBLICADO', 'REALIZADO')
                   AND EXISTS (SELECT 1 FROM evento_personal_acceso pa
                                WHERE pa.evento_id = e.id AND pa.usuario_id = :id)
                 ORDER BY e.fecha_hora_inicio ASC, e.id ASC
                """).param("id", usuarioId).query(EventoConsultas::mapear).list();
    }

    private static boolean tiene(String s) {
        return s != null && !s.isBlank();
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toInstant();
    }

    private static Double decimal(ResultSet rs, String columna) throws SQLException {
        BigDecimal v = rs.getBigDecimal(columna);
        return v == null ? null : v.doubleValue();
    }

    private static EventoDto mapear(ResultSet rs, int n) throws SQLException {
        boolean requiereAsiento = rs.getBoolean("requiere_asiento");
        int inventario = rs.getInt("inventario");
        int disponibles = requiereAsiento ? Math.min(inventario, rs.getInt("asientos_libres")) : inventario;
        double distancia = rs.getDouble("distancia_km");
        Double distanciaKm = rs.wasNull() ? null
                : BigDecimal.valueOf(distancia).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new EventoDto(
                rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"), rs.getString("lugar"),
                rs.getString("direccion"), rs.getString("ciudad"), decimal(rs, "latitud"), decimal(rs, "longitud"),
                rs.getString("categoria"), (Integer) rs.getObject("categoria_id"),
                instante(rs, "fecha_hora_inicio"), instante(rs, "fecha_hora_fin"),
                rs.getBoolean("es_de_pago"), requiereAsiento, rs.getInt("aforo"), Math.max(0, disponibles),
                rs.getInt("vendidos"), rs.getString("estado"), rs.getLong("organizador_id"),
                rs.getString("organizador_nombre"), rs.getBigDecimal("precio_desde").setScale(2, RoundingMode.HALF_UP).toPlainString(),
                distanciaKm, instante(rs, "fecha_creacion"), rs.getBoolean("tiene_boletos"),
                rs.getInt("asientos_totales"), instante(rs, "fecha_cancelacion"),
                (Long) rs.getObject("cancelado_por"), rs.getString("cancelado_por_nombre"),
                rs.getString("motivo_cancelacion"),
                rs.getBigDecimal("comision_cancelacion").setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
