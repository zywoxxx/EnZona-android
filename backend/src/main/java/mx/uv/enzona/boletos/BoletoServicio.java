package mx.uv.enzona.boletos;

import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.uv.enzona.boletos.BoletoDtos.BoletoDto;
import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.common.util.FirmaHmac;
import mx.uv.enzona.eventos.EventoConsultas;
import mx.uv.enzona.eventos.EventoDtos.AsientoDto;
import mx.uv.enzona.eventos.EventoDtos.EventoDto;

/**
 * Emisión y consulta de boletos (RF-12). Cada boleto tiene un código único y su
 * firma HMAC; el QR es `codigo.firma`.
 */
@Service
public class BoletoServicio {

    public interface BoletoRepositorio extends JpaRepository<Boleto, Long> {

        boolean existsByCodigo(String codigo);
    }

    private static final String SELECCION = """
            SELECT b.id, b.orden_id, b.tipo_boleto_id, b.asiento_id, b.codigo, b.qr_firma, b.estado,
                   b.fecha_emision, b.fecha_uso,
                   o.evento_id, o.asistente_id, o.total AS total_orden, o.estado AS estado_orden, o.fecha_creacion,
                   t.nombre AS tipo_nombre, t.precio,
                   a.seccion, a.fila, a.numero, a.estado AS estado_asiento,
                   ROW_NUMBER() OVER (PARTITION BY b.orden_id ORDER BY b.id) AS posicion,
                   COUNT(*) OVER (PARTITION BY b.orden_id) AS total_en_orden
              FROM boleto b
              JOIN orden o ON o.id = b.orden_id
              JOIN tipo_boleto t ON t.id = b.tipo_boleto_id
              LEFT JOIN asiento a ON a.id = b.asiento_id
            """;

    private final BoletoRepositorio boletos;
    private final FirmaHmac firma;
    private final EventoConsultas eventos;
    private final JdbcClient jdbc;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public BoletoServicio(BoletoRepositorio boletos, FirmaHmac firma, EventoConsultas eventos, JdbcClient jdbc,
            Clock reloj) {
        this.boletos = boletos;
        this.firma = firma;
        this.eventos = eventos;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    private String codigoAlAzar() {
        return "EZ-" + String.format("%04d-%04d-%04d", azar.nextInt(10_000), azar.nextInt(10_000),
                azar.nextInt(10_000));
    }

    /** N códigos distintos entre sí y que no existen todavía (una sola consulta para comprobarlo). */
    private List<String> codigosNuevos(int cantidad) {
        for (int intento = 0; intento < 5; intento++) {
            Set<String> candidatos = new LinkedHashSet<>();
            while (candidatos.size() < cantidad) {
                candidatos.add(codigoAlAzar());
            }
            List<String> repetidos = jdbc.sql("SELECT codigo FROM boleto WHERE codigo IN (:codigos)")
                    .param("codigos", candidatos).query(String.class).list();
            if (repetidos.isEmpty()) {
                return new ArrayList<>(candidatos);
            }
        }
        throw new IllegalStateException("No se pudieron generar códigos de boleto únicos");
    }

    /**
     * Emite N boletos de una orden, cada uno con código y firma propios, en una
     * sola sentencia. Debe llamarse dentro de la transacción que confirma el pago.
     *
     * @param asientosIds asientos en el mismo orden que los boletos; vacío si el evento no tiene plano
     * @return ids de los boletos, en el orden de emisión
     */
    public List<Long> emitir(Long ordenId, Long tipoBoletoId, int cantidad, List<Long> asientosIds) {
        List<String> codigos = codigosNuevos(cantidad);
        StringBuilder sql = new StringBuilder(
                "INSERT INTO boleto (orden_id, tipo_boleto_id, asiento_id, codigo, qr_firma, estado, fecha_emision) VALUES ");
        for (int k = 0; k < cantidad; k++) {
            sql.append(k == 0 ? "" : ", ").append("(:orden, :tipo, :asiento").append(k).append(", :codigo").append(k)
                    .append(", :firma").append(k).append(", 'VALIDO', :ahora)");
        }
        sql.append(" RETURNING id");
        JdbcClient.StatementSpec sentencia = jdbc.sql(sql.toString()).param("orden", ordenId)
                .param("tipo", tipoBoletoId).param("ahora", Timestamp.from(reloj.instant()));
        for (int k = 0; k < cantidad; k++) {
            sentencia = sentencia
                    .param("asiento" + k, k < asientosIds.size() ? asientosIds.get(k) : null, Types.BIGINT)
                    .param("codigo" + k, codigos.get(k))
                    .param("firma" + k, firma.firmar(codigos.get(k)));
        }
        List<Long> ids = new ArrayList<>(sentencia.query(Long.class).list());
        ids.sort(Long::compare);
        return ids;
    }

    // ------------------------------------------------------------ consulta

    private List<BoletoDto> consultar(String condicion, String nombre, Object valor) {
        Map<Long, EventoDto> cache = new HashMap<>();
        // El filtro siempre selecciona órdenes completas, así que la posición "k de N" no cambia al filtrar
        return jdbc.sql(SELECCION + " WHERE " + condicion + " ORDER BY b.fecha_emision DESC, b.orden_id DESC, b.id ASC")
                .param(nombre, valor)
                .query((rs, n) -> mapear(rs, cache))
                .list();
    }

    private BoletoDto mapear(ResultSet rs, Map<Long, EventoDto> cache) throws SQLException {
        long eventoId = rs.getLong("evento_id");
        EventoDto evento = cache.computeIfAbsent(eventoId, id -> eventos.porId(id).orElse(null));
        Long asientoId = (Long) rs.getObject("asiento_id");
        AsientoDto asiento = asientoId == null ? null
                : new AsientoDto(asientoId, eventoId, rs.getString("seccion"), rs.getString("fila"),
                        rs.getString("numero"), rs.getString("estado_asiento"));
        Timestamp uso = rs.getTimestamp("fecha_uso");
        String codigo = rs.getString("codigo");
        String qrFirma = rs.getString("qr_firma");
        return new BoletoDto(rs.getLong("id"), rs.getLong("orden_id"), eventoId, rs.getLong("tipo_boleto_id"),
                asientoId, codigo, qrFirma, codigo + "." + qrFirma, rs.getString("estado"),
                rs.getTimestamp("fecha_emision").toInstant(), uso == null ? null : uso.toInstant(),
                rs.getLong("asistente_id"), rs.getString("tipo_nombre"),
                rs.getBigDecimal("precio").setScale(2, RoundingMode.HALF_UP).toPlainString(),
                rs.getInt("posicion"), rs.getInt("total_en_orden"),
                rs.getBigDecimal("total_orden").setScale(2, RoundingMode.HALF_UP).toPlainString(),
                rs.getString("estado_orden"), rs.getTimestamp("fecha_creacion").toInstant(), asiento, evento);
    }

    @Transactional(readOnly = true)
    public List<BoletoDto> deUsuario(Long usuarioId) {
        return consultar("o.asistente_id = :valor", "valor", usuarioId);
    }

    @Transactional(readOnly = true)
    public List<BoletoDto> deOrden(Long ordenId) {
        List<BoletoDto> lista = new ArrayList<>(consultar("b.orden_id = :valor", "valor", ordenId));
        lista.sort((a, b) -> Long.compare(a.id(), b.id()));
        return lista;
    }

    /** Boletos de un evento, para el panel del organizador. */
    @Transactional(readOnly = true)
    public List<BoletoDto> deEvento(Long eventoId) {
        return consultar("o.evento_id = :valor", "valor", eventoId);
    }

    /** El titular del boleto, el organizador del evento o un administrador. */
    @Transactional(readOnly = true)
    public BoletoDto obtener(Long boletoId, UsuarioActual yo) {
        Long ordenId = jdbc.sql("SELECT orden_id FROM boleto WHERE id = :id").param("id", boletoId)
                .query(Long.class).optional()
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El boleto no existe."));
        BoletoDto b = deOrden(ordenId).stream().filter(x -> x.id().equals(boletoId)).findFirst()
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El boleto no existe."));
        boolean esTitular = b.asistenteId().equals(yo.id());
        boolean gestiona = yo.esAdmin() || (b.evento() != null && yo.id().equals(b.evento().organizadorId()));
        if (!esTitular && !gestiona) {
            throw ExcepcionEnZona.noEncontrado("El boleto no existe.");
        }
        return b;
    }
}
