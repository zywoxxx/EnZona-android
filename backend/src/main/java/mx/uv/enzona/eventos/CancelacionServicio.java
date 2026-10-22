package mx.uv.enzona.eventos;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.uv.enzona.common.config.PropiedadesEnZona;
import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.common.util.FormatoFecha;
import mx.uv.enzona.eventos.EventoDtos.CancelacionDto;
import mx.uv.enzona.eventos.EventoDtos.CancelacionResultadoDto;
import mx.uv.enzona.eventos.Repositorios.CargoRepositorio;
import mx.uv.enzona.eventos.Repositorios.EventoRepositorio;
import mx.uv.enzona.notificaciones.NotificacionServicio;
import mx.uv.enzona.notificaciones.ServicioCorreo;
import mx.uv.enzona.notificaciones.TipoNotificacion;
import mx.uv.enzona.ordenes.PasarelaPagos;

/**
 * Cancelación de eventos (RF-14, CP-16, CP-CANCEL-01..07). Toda la operación
 * ocurre en UNA transacción: o se aplica completa o no cambia nada.
 */
@Service
public class CancelacionServicio {

    private static final Logger log = LoggerFactory.getLogger(CancelacionServicio.class);

    private final EventoRepositorio eventos;
    private final CargoRepositorio cargos;
    private final NotificacionServicio notificaciones;
    private final ServicioCorreo correo;
    private final PasarelaPagos pasarela;
    private final PropiedadesEnZona propiedades;
    private final JdbcClient jdbc;
    private final Clock reloj;

    public CancelacionServicio(EventoRepositorio eventos, CargoRepositorio cargos, NotificacionServicio notificaciones,
            ServicioCorreo correo, PasarelaPagos pasarela, PropiedadesEnZona propiedades, JdbcClient jdbc, Clock reloj) {
        this.eventos = eventos;
        this.cargos = cargos;
        this.notificaciones = notificaciones;
        this.correo = correo;
        this.pasarela = pasarela;
        this.propiedades = propiedades;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Administrativa cuando cancela un administrador que no es el organizador del evento. */
    private static boolean esAdministrativa(Evento e, UsuarioActual yo) {
        return yo.esAdmin() && !e.getOrganizadorId().equals(yo.id());
    }

    private static void exigirPermiso(Evento e, UsuarioActual yo) {
        if (!yo.esAdmin() && !e.getOrganizadorId().equals(yo.id())) {
            throw ExcepcionEnZona.prohibido("Solo el organizador del evento o un administrador pueden cancelarlo.");
        }
    }

    private PoliticaCancelacion evaluar(Evento e, boolean administrativa, Instant ahora) {
        boolean tienePrecio = jdbc.sql("SELECT EXISTS (SELECT 1 FROM tipo_boleto WHERE evento_id = :e AND precio > 0)")
                .param("e", e.getId()).query(Boolean.class).single();
        BigDecimal cobrado = jdbc
                .sql("SELECT COALESCE(SUM(total), 0) FROM orden WHERE evento_id = :e AND estado = 'PAGADA'")
                .param("e", e.getId()).query(BigDecimal.class).single();
        int vigentes = jdbc.sql("""
                SELECT COUNT(*) FROM boleto b JOIN orden o ON o.id = b.orden_id
                 WHERE o.evento_id = :e AND b.estado IN ('VALIDO', 'USADO')
                """).param("e", e.getId()).query(Integer.class).single();
        return PoliticaCancelacion.evaluar(e.getEstado(), e.getFechaHoraInicio(), e.isEsDePago(), tienePrecio,
                cobrado, vigentes, ahora, administrativa, propiedades.cancelacion().porcentajeComision());
    }

    @Transactional(readOnly = true)
    public PoliticaCancelacion politica(Long eventoId, UsuarioActual yo) {
        Evento e = eventos.findById(eventoId).orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        exigirPermiso(e, yo);
        return evaluar(e, esAdministrativa(e, yo), reloj.instant());
    }

    @Transactional
    public CancelacionResultadoDto cancelar(Long eventoId, CancelacionDto solicitud, UsuarioActual yo) {
        // Bloqueo exclusivo de la fila: las compras en curso terminan antes y las siguientes ven el evento cancelado
        Evento e = eventos.buscarParaModificar(eventoId)
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        exigirPermiso(e, yo);
        boolean administrativa = esAdministrativa(e, yo);
        Instant ahora = reloj.instant();
        PoliticaCancelacion politica = evaluar(e, administrativa, ahora);

        // ---------- validaciones (sin efectos) ----------
        if (!politica.permitida()) {
            throw ExcepcionEnZona.conflicto("CANCELACION_BLOQUEADA", politica.motivoBloqueo());
        }
        String motivo = solicitud.motivo() == null || solicitud.motivo().isBlank() ? null : solicitud.motivo().trim();
        if (administrativa && (motivo == null || motivo.length() < 5)) {
            throw ExcepcionEnZona.regla("MOTIVO_OBLIGATORIO",
                    "La cancelación administrativa requiere un motivo (mínimo 5 caracteres).");
        }
        if (politica.cobraComision() && !solicitud.aceptaComision()) {
            throw ExcepcionEnZona.regla("COMISION_NO_ACEPTADA",
                    "Para cancelar un evento de pago con ventas debes aceptar la comisión de cancelación del "
                            + propiedades.cancelacion().porcentajeComision() + " % ("
                            + FormatoFecha.importe(politica.comision()) + ").")
                    .con("comision", politica.comision().toPlainString());
        }

        // ---------- aplicar (todo o nada) ----------
        Timestamp momento = Timestamp.from(ahora);

        // 1) Asientos de boletos vigentes y de reservas pendientes vuelven a estar disponibles
        jdbc.sql("""
                UPDATE asiento SET estado = 'DISPONIBLE'
                 WHERE evento_id = :e AND estado <> 'DISPONIBLE'
                """).param("e", eventoId).update();

        // 2) Boletos → CANCELADO
        int anulados = jdbc.sql("""
                UPDATE boleto SET estado = 'CANCELADO'
                 WHERE estado IN ('VALIDO', 'USADO')
                   AND orden_id IN (SELECT id FROM orden WHERE evento_id = :e)
                """).param("e", eventoId).update();

        // 3) Asistentes a quienes avisar (antes de cambiar el estado de las órdenes)
        record Afectado(long usuarioId, String correo, BigDecimal pagado) {
        }
        List<Afectado> afectados = jdbc.sql("""
                SELECT o.asistente_id, u.correo, COALESCE(SUM(o.total), 0) AS pagado
                  FROM orden o JOIN usuario u ON u.id = o.asistente_id
                 WHERE o.evento_id = :e AND o.estado = 'PAGADA'
                 GROUP BY o.asistente_id, u.correo
                """).param("e", eventoId)
                .query((rs, n) -> new Afectado(rs.getLong("asistente_id"), rs.getString("correo"),
                        rs.getBigDecimal("pagado")))
                .list();

        // 4) Pagos aprobados → REEMBOLSADO contra su referencia de pasarela (nunca datos de tarjeta)
        record PagoAprobado(long id, String referencia, BigDecimal monto) {
        }
        List<PagoAprobado> aprobados = jdbc.sql("""
                SELECT p.id, p.referencia_pasarela, p.monto
                  FROM pago p JOIN orden o ON o.id = p.orden_id
                 WHERE o.evento_id = :e AND p.estado = 'APROBADO'
                """).param("e", eventoId)
                .query((rs, n) -> new PagoAprobado(rs.getLong("id"), rs.getString("referencia_pasarela"),
                        rs.getBigDecimal("monto")))
                .list();
        for (PagoAprobado p : aprobados) {
            String referenciaReembolso = pasarela.reembolsar(p.referencia(), p.monto());
            jdbc.sql("""
                    UPDATE pago SET estado = 'REEMBOLSADO', referencia_reembolso = :ref, fecha_reembolso = :momento
                     WHERE id = :id AND estado = 'APROBADO'
                    """).param("ref", referenciaReembolso).param("momento", momento).param("id", p.id()).update();
        }
        // Pagos que seguían pendientes (OXXO/SPEI sin confirmar): no hubo cobro, no hay nada que reembolsar
        jdbc.sql("""
                UPDATE pago SET estado = 'RECHAZADO'
                 WHERE estado = 'PENDIENTE' AND orden_id IN (SELECT id FROM orden WHERE evento_id = :e)
                """).param("e", eventoId).update();

        // 5) Órdenes: las cobradas con importe → REEMBOLSADA; las gratuitas y las pendientes → CANCELADA
        jdbc.sql("""
                UPDATE orden SET estado = CASE WHEN estado = 'PAGADA' AND total > 0
                                               THEN 'REEMBOLSADA'::estado_orden ELSE 'CANCELADA'::estado_orden END
                 WHERE evento_id = :e AND estado IN ('PAGADA', 'PENDIENTE')
                """).param("e", eventoId).update();

        // 6) El inventario vuelve a su total (el evento ya no vende, pero los números quedan coherentes)
        jdbc.sql("UPDATE tipo_boleto SET cantidad_disponible = cantidad_total WHERE evento_id = :e")
                .param("e", eventoId).update();

        // 7) Registro de la cancelación en el evento
        e.setEstado(EstadoEvento.CANCELADO);
        e.setFechaCancelacion(ahora);
        e.setCanceladoPor(yo.id());
        e.setMotivoCancelacion(motivo);
        e.setComisionCancelacion(politica.comision());
        eventos.saveAndFlush(e);

        // 8) Comisión como cargo pendiente del organizador
        if (politica.cobraComision()) {
            CargoOrganizador cargo = new CargoOrganizador();
            cargo.setOrganizadorId(e.getOrganizadorId());
            cargo.setEventoId(eventoId);
            String concepto = "Comisión de cancelación (" + propiedades.cancelacion().porcentajeComision() + " %) · "
                    + e.getNombre();
            cargo.setConcepto(concepto.length() > 200 ? concepto.substring(0, 200) : concepto);
            cargo.setImporte(politica.comision());
            cargo.setPagado(false);
            cargo.setFecha(ahora);
            cargos.save(cargo);
        }

        // 9) Aviso a cada asistente
        for (Afectado a : afectados) {
            boolean conReembolso = a.pagado().signum() > 0;
            String texto = conReembolso
                    ? "El evento «" + e.getNombre() + "» fue cancelado. Recibirás el reembolso completo de tu compra ("
                            + FormatoFecha.importe(a.pagado()) + ")."
                    : "El evento «" + e.getNombre() + "» fue cancelado. Tu confirmación quedó anulada.";
            notificaciones.crear(a.usuarioId(), eventoId, TipoNotificacion.EVENTO_CANCELADO, texto, null);
            correo.enviar(a.correo(), "Evento cancelado: " + e.getNombre(), texto);
        }

        log.info("Evento {} cancelado por {} ({}). Boletos anulados: {}, reembolso: {}, comisión: {}",
                eventoId, yo.id(), politica.tipo(), anulados, politica.reembolso(), politica.comision());
        return new CancelacionResultadoDto(eventoId, politica.reembolso().toPlainString(),
                politica.comision().toPlainString(), anulados, afectados.size(), yo.id(), ahora);
    }
}
