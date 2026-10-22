package mx.uv.enzona.notificaciones;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.swagger.v3.oas.annotations.media.Schema;
import mx.uv.enzona.common.config.PropiedadesEnZona;
import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.util.FormatoFecha;

/**
 * Notificaciones del asistente (v4): COMPRA_CONFIRMADA al emitir,
 * EVENTO_CANCELADO y FECHA_CAMBIADA en sus casos, y RECORDATORIO generado por
 * una tarea diaria. Aquí se guarda el aviso; el envío por correo o push queda
 * detrás de `ServicioCorreo` (hoy simulado).
 */
@Service
public class NotificacionServicio {

    private static final Logger log = LoggerFactory.getLogger(NotificacionServicio.class);

    @Schema(name = "Notificacion")
    public record NotificacionDto(Long id, String tipo, String texto, Instant fecha, boolean leida, Long eventoId,
            Long boletoId) {
    }

    private final JdbcClient jdbc;
    private final FormatoFecha formato;
    private final PropiedadesEnZona propiedades;
    private final Clock reloj;

    public NotificacionServicio(JdbcClient jdbc, FormatoFecha formato, PropiedadesEnZona propiedades, Clock reloj) {
        this.jdbc = jdbc;
        this.formato = formato;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Registra un aviso. Se llama dentro de la transacción de la operación que lo origina. */
    public void crear(Long usuarioId, Long eventoId, TipoNotificacion tipo, String texto, Long boletoId) {
        jdbc.sql("""
                INSERT INTO notificacion_asistente (usuario_id, evento_id, tipo, texto, fecha, boleto_id)
                VALUES (:usuario, :evento, CAST(:tipo AS tipo_notificacion), :texto, :fecha, :boleto)
                ON CONFLICT DO NOTHING
                """)
                .param("usuario", usuarioId)
                .param("evento", eventoId)
                .param("tipo", tipo.name())
                .param("texto", texto.length() > 500 ? texto.substring(0, 500) : texto)
                .param("fecha", java.sql.Timestamp.from(reloj.instant()))
                .param("boleto", boletoId, java.sql.Types.BIGINT)
                .update();
    }

    @Transactional(readOnly = true)
    public List<NotificacionDto> listar(Long usuarioId, boolean soloNoLeidas) {
        return jdbc.sql("""
                SELECT id, tipo, texto, fecha, leida, evento_id, boleto_id
                  FROM notificacion_asistente
                 WHERE usuario_id = :usuario AND (:soloNoLeidas = FALSE OR leida = FALSE)
                 ORDER BY fecha DESC, id DESC
                 LIMIT 200
                """)
                .param("usuario", usuarioId)
                .param("soloNoLeidas", soloNoLeidas)
                .query((rs, n) -> new NotificacionDto(rs.getLong("id"), rs.getString("tipo"), rs.getString("texto"),
                        rs.getTimestamp("fecha").toInstant(), rs.getBoolean("leida"),
                        (Long) rs.getObject("evento_id"), (Long) rs.getObject("boleto_id")))
                .list();
    }

    @Transactional
    public NotificacionDto marcarLeida(Long usuarioId, Long id) {
        int filas = jdbc.sql("UPDATE notificacion_asistente SET leida = TRUE WHERE id = :id AND usuario_id = :usuario")
                .param("id", id).param("usuario", usuarioId).update();
        if (filas == 0) {
            throw ExcepcionEnZona.noEncontrado("La notificación no existe.");
        }
        return listar(usuarioId, false).stream().filter(n -> n.id().equals(id)).findFirst()
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("La notificación no existe."));
    }

    @Transactional
    public int marcarTodasLeidas(Long usuarioId) {
        return jdbc.sql("UPDATE notificacion_asistente SET leida = TRUE WHERE usuario_id = :usuario AND leida = FALSE")
                .param("usuario", usuarioId).update();
    }

    // ------------------------------------------------------------ recordatorios

    private record Proximo(long usuarioId, long eventoId, String nombre, String lugar, Instant inicio, long boletoId,
            int boletos) {
    }

    /**
     * Genera, una sola vez por usuario y evento, el recordatorio de los eventos
     * publicados con boleto vigente que comienzan dentro de los próximos días.
     * El índice único parcial `uq_notificacion_recordatorio` impide duplicarlo
     * aunque la tarea se ejecute dos veces o en dos instancias.
     */
    @Transactional
    public int generarRecordatorios() {
        Instant ahora = reloj.instant();
        Instant limite = ahora.plus(propiedades.recordatorios().diasAntes(), ChronoUnit.DAYS);
        List<Proximo> proximos = jdbc.sql("""
                SELECT o.asistente_id, e.id AS evento_id, e.nombre, e.lugar, e.fecha_hora_inicio,
                       MIN(b.id) AS boleto_id, COUNT(*) AS boletos
                  FROM boleto b
                  JOIN orden o ON o.id = b.orden_id
                  JOIN evento e ON e.id = o.evento_id
                 WHERE b.estado = 'VALIDO' AND e.estado = 'PUBLICADO'
                   AND e.fecha_hora_inicio > :ahora AND e.fecha_hora_inicio <= :limite
                   AND NOT EXISTS (SELECT 1 FROM notificacion_asistente n
                                    WHERE n.usuario_id = o.asistente_id AND n.evento_id = e.id
                                      AND n.tipo = 'RECORDATORIO')
                 GROUP BY o.asistente_id, e.id, e.nombre, e.lugar, e.fecha_hora_inicio
                """)
                .param("ahora", java.sql.Timestamp.from(ahora))
                .param("limite", java.sql.Timestamp.from(limite))
                .query((rs, n) -> new Proximo(rs.getLong("asistente_id"), rs.getLong("evento_id"),
                        rs.getString("nombre"), rs.getString("lugar"),
                        rs.getTimestamp("fecha_hora_inicio").toInstant(), rs.getLong("boleto_id"),
                        rs.getInt("boletos")))
                .list();
        for (Proximo p : proximos) {
            long dias = Duration.between(ahora, p.inicio()).toDays();
            String cuando = dias <= 0 ? "es hoy" : dias == 1 ? "es mañana" : "es en " + dias + " días";
            crear(p.usuarioId(), p.eventoId(), TipoNotificacion.RECORDATORIO,
                    "«" + p.nombre() + "» " + cuando + " · " + formato.largo(p.inicio()) + " en " + p.lugar() + ". "
                            + (p.boletos() == 1 ? "Lleva tu QR listo."
                                    : "Tienes " + p.boletos() + " boletos; lleva los QR listos."),
                    p.boletoId());
        }
        return proximos.size();
    }

    /** Tarea diaria (por defecto a las 08:00 de America/Mexico_City). */
    @Scheduled(cron = "${enzona.recordatorios.cron}", zone = "${enzona.zona-horaria}")
    public void tareaDiaria() {
        try {
            int n = generarRecordatorios();
            log.info("Recordatorios generados: {}", n);
        } catch (RuntimeException e) {
            log.error("No se pudieron generar los recordatorios", e);
        }
    }
}
