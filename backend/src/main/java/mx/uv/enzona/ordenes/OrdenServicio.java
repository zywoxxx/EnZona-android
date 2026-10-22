package mx.uv.enzona.ordenes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import mx.uv.enzona.boletos.BoletoDtos.BoletoDto;
import mx.uv.enzona.boletos.BoletoServicio;
import mx.uv.enzona.common.config.PropiedadesEnZona;
import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.common.util.FormatoFecha;
import mx.uv.enzona.eventos.Asiento;
import mx.uv.enzona.eventos.EstadoAsiento;
import mx.uv.enzona.eventos.EstadoEvento;
import mx.uv.enzona.eventos.Evento;
import mx.uv.enzona.eventos.Repositorios.AsientoRepositorio;
import mx.uv.enzona.eventos.Repositorios.EventoRepositorio;
import mx.uv.enzona.eventos.Repositorios.TipoBoletoRepositorio;
import mx.uv.enzona.eventos.TipoBoleto;
import mx.uv.enzona.notificaciones.NotificacionServicio;
import mx.uv.enzona.notificaciones.ServicioCorreo;
import mx.uv.enzona.notificaciones.TipoNotificacion;
import mx.uv.enzona.ordenes.OrdenDtos.CrearOrdenDto;
import mx.uv.enzona.ordenes.OrdenDtos.OrdenCreadaDto;

/**
 * Compra de N boletos o confirmación gratuita (RF-10, RF-11, RF-16, RNF-05, RNF-09).
 *
 * La compra se hace en tres pasos, para no mantener bloqueos de base de datos
 * durante la llamada a la pasarela (Anexo E, tabla E.8):
 *
 *  1. RESERVA (una transacción): valida todo, descuenta N del inventario con
 *     UPDATE condicional, toma los N asientos y crea la orden PENDIENTE con su
 *     detalle. Si falta un solo lugar o un solo asiento, no se reserva nada.
 *  2. COBRO: llamada a la pasarela, fuera de cualquier transacción.
 *  3. CIERRE (una transacción): pago aprobado → orden PAGADA, asientos OCUPADO y
 *     N boletos firmados; pago rechazado → se devuelve lo reservado; pago
 *     pendiente (OXXO/SPEI) → la reserva se conserva hasta que se confirme o venza.
 *
 * El precio y el total se derivan de `tipo_boleto`; nunca se aceptan del cliente.
 */
@Service
public class OrdenServicio {

    public interface OrdenRepositorio extends JpaRepository<Orden, Long> {

        Optional<Orden> findByAsistenteIdAndClaveOperacion(Long asistenteId, String claveOperacion);
    }

    public interface OrdenDetalleRepositorio extends JpaRepository<OrdenDetalle, Long> {

        Optional<OrdenDetalle> findFirstByOrdenId(Long ordenId);
    }

    public interface PagoRepositorio extends JpaRepository<Pago, Long> {

        Optional<Pago> findFirstByOrdenIdOrderByIdDesc(Long ordenId);
    }

    /** Límite técnico por orden; el inventario real puede ser menor. */
    public static final int MAXIMO_POR_ORDEN = 50;
    private static final int HORAS_VIGENCIA_PENDIENTE = 24;

    private static final Logger log = LoggerFactory.getLogger(OrdenServicio.class);

    /** Resultado de una creación: la orden y si se acaba de crear (201) o ya existía con esa clave (200). */
    public record Creacion(OrdenCreadaDto orden, boolean nueva) {
    }

    /** Lo que se reservó: permite cerrar la compra sin volver a leerlo de la base. */
    private record Pedido(Long tipoBoletoId, int cantidad, List<Long> asientosIds, long eventoId, String nombreEvento,
            Instant inicioEvento, BigDecimal total) {
    }

    private record Reserva(Long ordenId, BigDecimal total, TipoMetodoPago medio, String token, Long metodoPagoId,
            String nombreEvento, Pedido pedido) {
    }

    /** Señal interna: los asientos pedidos ya no estaban todos disponibles. */
    private static final class AsientosNoDisponibles extends RuntimeException {
        private final List<Long> ids;

        AsientosNoDisponibles(List<Long> ids) {
            super("asientos no disponibles", null, false, false);
            this.ids = ids;
        }
    }

    private final OrdenRepositorio ordenes;
    private final OrdenDetalleRepositorio detalles;
    private final PagoRepositorio pagos;
    private final MetodoPagoServicio metodos;
    private final EventoRepositorio eventos;
    private final TipoBoletoRepositorio tipos;
    private final AsientoRepositorio asientos;
    private final BoletoServicio boletos;
    private final PasarelaPagos pasarela;
    private final NotificacionServicio notificaciones;
    private final ServicioCorreo correo;
    private final FormatoFecha formato;
    private final PropiedadesEnZona propiedades;
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;
    private final Clock reloj;

    public OrdenServicio(OrdenRepositorio ordenes, OrdenDetalleRepositorio detalles, PagoRepositorio pagos,
            MetodoPagoServicio metodos, EventoRepositorio eventos, TipoBoletoRepositorio tipos,
            AsientoRepositorio asientos, BoletoServicio boletos, PasarelaPagos pasarela,
            NotificacionServicio notificaciones, ServicioCorreo correo, FormatoFecha formato,
            PropiedadesEnZona propiedades, TransactionTemplate tx, JdbcClient jdbc, Clock reloj) {
        this.ordenes = ordenes;
        this.detalles = detalles;
        this.pagos = pagos;
        this.metodos = metodos;
        this.eventos = eventos;
        this.tipos = tipos;
        this.asientos = asientos;
        this.boletos = boletos;
        this.pasarela = pasarela;
        this.notificaciones = notificaciones;
        this.correo = correo;
        this.formato = formato;
        this.propiedades = propiedades;
        this.tx = tx;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ==================================================================
    //  Creación
    // ==================================================================

    public Creacion crear(UsuarioActual yo, String claveOperacion, CrearOrdenDto solicitud) {
        String clave = claveOperacion == null ? "" : claveOperacion.trim();
        if (clave.length() < 8 || clave.length() > 64) {
            throw ExcepcionEnZona.solicitudInvalida("CLAVE_DE_OPERACION",
                    "La cabecera Idempotency-Key debe tener entre 8 y 64 caracteres.");
        }
        // Idempotencia: la misma clave devuelve la misma orden, sin cobrar ni emitir de nuevo
        Optional<Long> previa = ordenConClave(yo.id(), clave);
        if (previa.isPresent()) {
            return new Creacion(respuesta(previa.get()), false);
        }

        Reserva reserva;
        try {
            reserva = tx.execute(estado -> reservar(yo, clave, solicitud));
        } catch (AsientosNoDisponibles e) {
            throw asientosOcupados(e.ids);
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones simultáneas con la misma clave: gana una y la otra recibe la misma orden
            Optional<Long> ganadora = ordenConClave(yo.id(), clave);
            if (ganadora.isPresent()) {
                return new Creacion(respuesta(ganadora.get()), false);
            }
            throw e;
        }

        PasarelaPagos.Resultado cobro = null;
        if (reserva.total().signum() > 0) {
            try {
                cobro = pasarela.cobrar(new PasarelaPagos.Cobro(reserva.medio(), reserva.token(), reserva.total(),
                        "MXN", "orden-" + reserva.ordenId()));
            } catch (RuntimeException e) {
                log.error("La pasarela no respondió para la orden {}", reserva.ordenId(), e);
                cobro = new PasarelaPagos.Resultado(PasarelaPagos.Estado.RECHAZADO, null,
                        "La pasarela de pago no respondió. No se realizó ningún cargo.");
            }
        }
        cerrar(reserva, cobro, yo);
        return new Creacion(respuesta(reserva.ordenId()), true);
    }

    private Optional<Long> ordenConClave(Long asistenteId, String clave) {
        return jdbc.sql("SELECT id FROM orden WHERE asistente_id = :asistente AND clave_operacion = :clave")
                .param("asistente", asistenteId).param("clave", clave).query(Long.class).optional();
    }

    /** Paso 1. Dentro de una transacción: valida, reserva inventario y asientos y crea la orden PENDIENTE. */
    private Reserva reservar(UsuarioActual yo, String clave, CrearOrdenDto s) {
        // FOR SHARE: una cancelación del evento espera a que esta reserva termine, y al revés
        Evento evento = eventos.buscarParaCompra(s.eventoId())
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        Instant ahora = reloj.instant();
        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            throw ExcepcionEnZona.conflicto("EVENTO_CANCELADO", "El evento fue cancelado.");
        }
        if (evento.getEstado() != EstadoEvento.PUBLICADO) {
            throw ExcepcionEnZona.conflicto("EVENTO_NO_DISPONIBLE", "El evento no admite compras en este momento.");
        }
        Instant cierre = evento.getFechaHoraFin() != null ? evento.getFechaHoraFin() : evento.getFechaHoraInicio();
        if (!cierre.isAfter(ahora)) {
            throw ExcepcionEnZona.conflicto("EVENTO_TERMINADO", "El evento ya comenzó o terminó; no admite más boletos.");
        }
        int n = s.cantidad();
        if (n > MAXIMO_POR_ORDEN) {
            throw ExcepcionEnZona.regla("CANTIDAD_MAXIMA",
                    "Una orden admite hasta " + MAXIMO_POR_ORDEN + " boletos.").con("maximo", MAXIMO_POR_ORDEN);
        }

        // ---------- tipo de boleto y precio (siempre del servidor) ----------
        List<TipoBoleto> delEvento = tipos.findByEventoIdOrderByIdAsc(evento.getId());
        TipoBoleto tipo;
        if (s.tipoBoletoId() != null) {
            tipo = delEvento.stream().filter(t -> t.getId().equals(s.tipoBoletoId())).findFirst()
                    .orElseThrow(() -> ExcepcionEnZona.regla("TIPO_AJENO",
                            "El tipo de boleto no pertenece a este evento."));
        } else if (evento.isEsDePago()) {
            throw ExcepcionEnZona.solicitudInvalida("VALIDACION", "Elige un tipo de boleto.");
        } else {
            tipo = delEvento.stream().findFirst().orElseThrow(() -> ExcepcionEnZona.conflicto(
                    "EVENTO_NO_DISPONIBLE", "El evento no tiene boletos configurados."));
        }
        BigDecimal total = FormatoFecha.mxn(tipo.getPrecio().multiply(BigDecimal.valueOf(n)));

        // ---------- política de eventos gratuitos: una confirmación por persona ----------
        if (!evento.isEsDePago()) {
            if (n != 1) {
                throw ExcepcionEnZona.regla("UNA_CONFIRMACION_POR_PERSONA",
                        "En eventos gratuitos se confirma un lugar por persona.");
            }
            // Candado por (evento, persona): dos confirmaciones simultáneas de la misma cuenta se atienden en fila
            jdbc.sql("SELECT pg_advisory_xact_lock(:evento, :usuario)")
                    .param("evento", evento.getId().intValue()).param("usuario", yo.id().intValue())
                    .query((rs, i) -> 0).single();
            // Se mira la ORDEN y no solo el boleto: el boleto se emite en el cierre, después de esta
            // transacción, y entre una y otra ya debe contar como confirmación en curso
            boolean yaTiene = jdbc.sql("""
                    SELECT EXISTS (SELECT 1 FROM orden o
                                    WHERE o.evento_id = :evento AND o.asistente_id = :usuario
                                      AND o.estado IN ('PENDIENTE', 'PAGADA'))
                    """).param("evento", evento.getId()).param("usuario", yo.id()).query(Boolean.class).single();
            if (yaTiene) {
                throw ExcepcionEnZona.regla("YA_CONFIRMADO", "Ya confirmaste tu asistencia a este evento.");
            }
        }

        // ---------- medio de pago ----------
        TipoMetodoPago medio = null;
        String token = null;
        Long metodoPagoId = null;
        if (total.signum() > 0) {
            medio = medio(s.metodo());
            if (medio == TipoMetodoPago.TARJETA) {
                if (s.tokenPasarela() != null && !s.tokenPasarela().isBlank()) {
                    token = s.tokenPasarela().trim();
                    metodoPagoId = s.metodoPagoId() == null ? null : metodos.exigirPropio(yo.id(), s.metodoPagoId()).getId();
                } else if (s.metodoPagoId() != null) {
                    MetodoPago guardado = metodos.exigirPropio(yo.id(), s.metodoPagoId());
                    token = guardado.getTokenPasarela();
                    metodoPagoId = guardado.getId();
                } else {
                    throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                            "Para pagar con tarjeta elige una tarjeta guardada o envía el token de la pasarela.");
                }
            }
        }

        // ---------- asientos ----------
        List<Long> ids = s.asientosIds() == null ? List.of() : s.asientosIds();
        if (evento.isRequiereAsiento()) {
            if (new LinkedHashSet<>(ids).size() != ids.size()) {
                throw ExcepcionEnZona.solicitudInvalida("ASIENTOS_REPETIDOS", "Hay asientos repetidos en la selección.");
            }
            if (ids.size() != n) {
                int faltan = n - ids.size();
                throw ExcepcionEnZona.solicitudInvalida("ASIENTOS_INCOMPLETOS",
                        faltan > 0 ? "Faltan " + faltan + " asientos por elegir (" + ids.size() + " de " + n + ")."
                                : "Elegiste " + ids.size() + " asientos para " + n + " boletos. Quita " + (-faltan) + ".");
            }
            List<Asiento> elegidos = asientos.findAllById(ids);
            if (elegidos.size() != ids.size()) {
                throw ExcepcionEnZona.regla("ASIENTO_INEXISTENTE", "Alguno de los asientos elegidos no existe.");
            }
            elegidos.stream().filter(a -> !a.getEventoId().equals(evento.getId())).findFirst().ifPresent(a -> {
                throw ExcepcionEnZona.regla("ASIENTO_AJENO",
                        "El asiento " + a.getFila() + a.getNumero() + " no pertenece a este evento.");
            });
        } else if (!ids.isEmpty()) {
            throw ExcepcionEnZona.solicitudInvalida("SIN_PLANO", "Este evento no tiene asientos numerados.");
        }

        // ---------- inventario por lote: todo o nada ----------
        if (tipos.descontar(tipo.getId(), n) != 1) {
            int quedan = jdbc.sql("SELECT cantidad_disponible FROM tipo_boleto WHERE id = :id")
                    .param("id", tipo.getId()).query(Integer.class).single();
            throw ExcepcionEnZona.conflicto("SIN_CUPO",
                    quedan <= 0 ? "Aforo agotado: ya no quedan lugares de tipo " + tipo.getNombre() + "."
                            : "Solo quedan " + quedan + " lugares de tipo " + tipo.getNombre() + " y pediste " + n + ".")
                    .con("disponibles", Math.max(quedan, 0)).con("solicitados", n);
        }
        if (!ids.isEmpty() && asientos.tomar(evento.getId(), ids, EstadoAsiento.RESERVADO.name()) != ids.size()) {
            // La excepción revierte también el descuento de inventario de esta transacción
            throw new AsientosNoDisponibles(ids);
        }

        // ---------- orden PENDIENTE y su detalle ----------
        Orden orden = new Orden();
        orden.setAsistenteId(yo.id());
        orden.setEventoId(evento.getId());
        orden.setTotal(total);
        orden.setEstado(EstadoOrden.PENDIENTE);
        orden.setFechaCreacion(ahora);
        orden.setClaveOperacion(clave);
        orden = ordenes.saveAndFlush(orden);

        OrdenDetalle detalle = new OrdenDetalle();
        detalle.setOrdenId(orden.getId());
        detalle.setTipoBoletoId(tipo.getId());
        detalle.setCantidad(n);
        detalle.setPrecioUnitario(FormatoFecha.mxn(tipo.getPrecio()));
        detalle.setAsientosIds(ids.toArray(Long[]::new));
        detalles.saveAndFlush(detalle);

        return new Reserva(orden.getId(), total, medio, token, metodoPagoId, evento.getNombre(),
                new Pedido(tipo.getId(), n, List.copyOf(ids), evento.getId(), evento.getNombre(),
                        evento.getFechaHoraInicio(), total));
    }

    private static TipoMetodoPago medio(String metodo) {
        String m = metodo == null ? "" : metodo.trim().toUpperCase(Locale.ROOT);
        return switch (m) {
            case "TARJETA" -> TipoMetodoPago.TARJETA;
            case "OXXO" -> TipoMetodoPago.OXXO;
            case "SPEI" -> TipoMetodoPago.SPEI;
            default -> throw ExcepcionEnZona.solicitudInvalida("VALIDACION",
                    "Este evento es de pago: indica el método TARJETA, OXXO o SPEI.");
        };
    }

    private ExcepcionEnZona asientosOcupados(List<Long> ids) {
        List<String> etiquetas = jdbc.sql("""
                SELECT fila || numero FROM asiento WHERE id IN (:ids) AND estado <> 'DISPONIBLE' ORDER BY id
                """).param("ids", ids).query(String.class).list();
        String detalle = etiquetas.isEmpty() ? "Alguno de los asientos elegidos acaba de ocuparse. Elige otros."
                : etiquetas.size() == 1 ? "El asiento " + etiquetas.get(0) + " acaba de ocuparse. Elige otro."
                        : "Estos asientos acaban de ocuparse: " + String.join(", ", etiquetas) + ". Elige otros.";
        return ExcepcionEnZona.conflicto("ASIENTOS_OCUPADOS", detalle).con("asientosOcupados", etiquetas)
                .con("libres", ids.size() - etiquetas.size());
    }

    // ==================================================================
    //  Cierre
    // ==================================================================

    /** Paso 3. Aplica el resultado del cobro. `cobro` es null cuando la orden no tiene importe. */
    private void cerrar(Reserva reserva, PasarelaPagos.Resultado cobro, UsuarioActual yo) {
        PasarelaPagos.Estado estado = cobro == null ? PasarelaPagos.Estado.APROBADO : cobro.estado();
        switch (estado) {
            case APROBADO -> {
                boolean emitida = Boolean.TRUE.equals(tx.execute(t -> {
                    Long pagoId = cobro == null ? null
                            : registrarPago(reserva, EstadoPago.APROBADO, cobro.referencia()).getId();
                    if (!confirmar(reserva.ordenId(), yo.id(), reserva.pedido())) {
                        // El evento se canceló mientras se cobraba: se devuelve el dinero
                        if (pagoId != null) {
                            reembolsar(pagoId, cobro.referencia(), reserva.total());
                        }
                        return false;
                    }
                    return true;
                }));
                if (!emitida) {
                    throw ExcepcionEnZona.conflicto("EVENTO_CANCELADO",
                            "El evento se canceló mientras se procesaba tu compra. "
                                    + (cobro == null ? "No se emitieron boletos." : "El cobro se reembolsó por completo."))
                            .con("ordenId", reserva.ordenId());
                }
                correo.enviar(yo.correo(), "Tus boletos de " + reserva.nombreEvento(),
                        "Tu compra quedó confirmada. Los boletos con su código QR están en tu cuenta de EnZona.");
            }
            case PENDIENTE -> tx.executeWithoutResult(
                    t -> registrarPago(reserva, EstadoPago.PENDIENTE, cobro.referencia()));
            case RECHAZADO -> {
                tx.executeWithoutResult(t -> {
                    registrarPago(reserva, EstadoPago.RECHAZADO, cobro.referencia());
                    liberar(reserva.ordenId(), true);
                });
                throw ExcepcionEnZona.regla("PAGO_RECHAZADO",
                        "La pasarela rechazó el pago. No se realizó ningún cargo y tus lugares siguen disponibles.")
                        .con("ordenId", reserva.ordenId());
            }
        }
    }

    private Pago registrarPago(Reserva reserva, EstadoPago estado, String referencia) {
        Pago p = new Pago();
        p.setOrdenId(reserva.ordenId());
        p.setMonto(reserva.total());
        p.setMoneda("MXN");
        p.setEstado(estado);
        p.setReferenciaPasarela(referencia);
        p.setFecha(reloj.instant());
        p.setMetodoPagoId(reserva.metodoPagoId());
        p.setMedio(reserva.medio());
        return pagos.saveAndFlush(p);
    }

    private void reembolsar(Long pagoId, String referencia, BigDecimal monto) {
        String referenciaReembolso = pasarela.reembolsar(referencia, monto);
        jdbc.sql("""
                UPDATE pago SET estado = 'REEMBOLSADO', referencia_reembolso = :ref, fecha_reembolso = :momento
                 WHERE id = :id
                """).param("ref", referenciaReembolso).param("momento", java.sql.Timestamp.from(reloj.instant()))
                .param("id", pagoId).update();
    }

    /**
     * Orden PENDIENTE → PAGADA, asientos RESERVADO → OCUPADO y emisión de los N
     * boletos. Devuelve false si la orden ya no estaba pendiente (p. ej. el
     * evento se canceló y la cancelación ya liberó la reserva).
     */
    private boolean confirmar(Long ordenId, Long asistenteId, Pedido p) {
        int filas = jdbc.sql("UPDATE orden SET estado = 'PAGADA' WHERE id = :id AND estado = 'PENDIENTE'")
                .param("id", ordenId).update();
        if (filas != 1) {
            return false;
        }
        List<Long> ids = p.asientosIds();
        if (!ids.isEmpty()
                && asientos.cambiar(ids, EstadoAsiento.RESERVADO.name(), EstadoAsiento.OCUPADO.name()) != ids.size()) {
            throw new IllegalStateException("Los asientos reservados de la orden " + ordenId + " cambiaron de estado");
        }
        List<Long> emitidos = boletos.emitir(ordenId, p.tipoBoletoId(), p.cantidad(), ids);

        int n = p.cantidad();
        String inicio = p.total().signum() > 0
                ? "Compraste " + (n == 1 ? "1 boleto" : n + " boletos") + " para «" + p.nombreEvento() + "»"
                : "Confirmaste tu lugar en «" + p.nombreEvento() + "»";
        notificaciones.crear(asistenteId, p.eventoId(), TipoNotificacion.COMPRA_CONFIRMADA,
                inicio + " · " + formato.corto(p.inicioEvento()) + ". " + (n == 1 ? "Tu QR está" : "Tus QR están")
                        + " en Mis boletos.",
                emitidos.get(0));
        return true;
    }

    /** Lo pedido en una orden ya guardada (para confirmar un pago que quedó pendiente). */
    private Pedido pedidoDe(Long ordenId) {
        OrdenDetalle d = detalles.findFirstByOrdenId(ordenId)
                .orElseThrow(() -> new IllegalStateException("La orden " + ordenId + " no tiene detalle"));
        return jdbc.sql("""
                SELECT e.id, e.nombre, e.fecha_hora_inicio, o.total
                  FROM orden o JOIN evento e ON e.id = o.evento_id WHERE o.id = :id
                """).param("id", ordenId)
                .query((rs, n) -> new Pedido(d.getTipoBoletoId(), d.getCantidad(), Arrays.asList(d.getAsientosIds()),
                        rs.getLong("id"), rs.getString("nombre"), rs.getTimestamp("fecha_hora_inicio").toInstant(),
                        rs.getBigDecimal("total")))
                .single();
    }

    /**
     * Devuelve al inventario lo reservado por una orden PENDIENTE y la cancela.
     * `liberarClave` deja libre la clave de operación para que un reintento con
     * otro medio de pago cree una orden nueva.
     */
    private boolean liberar(Long ordenId, boolean liberarClave) {
        int filas = jdbc.sql("""
                UPDATE orden SET estado = 'CANCELADA',
                                 clave_operacion = CASE WHEN :liberar THEN NULL ELSE clave_operacion END
                 WHERE id = :id AND estado = 'PENDIENTE'
                """).param("liberar", liberarClave).param("id", ordenId).update();
        if (filas != 1) {
            return false;
        }
        detalles.findFirstByOrdenId(ordenId).ifPresent(d -> {
            tipos.reponer(d.getTipoBoletoId(), d.getCantidad());
            List<Long> ids = Arrays.asList(d.getAsientosIds());
            if (!ids.isEmpty()) {
                asientos.cambiar(ids, EstadoAsiento.RESERVADO.name(), EstadoAsiento.DISPONIBLE.name());
            }
        });
        return true;
    }

    // ==================================================================
    //  Pagos pendientes (OXXO / SPEI)
    // ==================================================================

    /**
     * SIMULACIÓN del aviso de la pasarela: confirma un pago pendiente y emite los
     * boletos. Disponible en el perfil dev o para un administrador.
     */
    public OrdenCreadaDto confirmarPago(Long pagoId, UsuarioActual yo) {
        Pago pago = pagos.findById(pagoId).orElseThrow(() -> ExcepcionEnZona.noEncontrado("El pago no existe."));
        Orden orden = ordenes.findById(pago.getOrdenId())
                .orElseThrow(() -> ExcepcionEnZona.noEncontrado("La orden no existe."));
        boolean permitido = yo.esAdmin()
                || (propiedades.pagos().confirmacionSimulada() && orden.getAsistenteId().equals(yo.id()));
        if (!permitido) {
            throw ExcepcionEnZona.prohibido("La confirmación de un pago la envía la pasarela; no está disponible para tu cuenta.");
        }
        if (pago.getEstado() != EstadoPago.PENDIENTE) {
            throw ExcepcionEnZona.conflicto("PAGO_NO_PENDIENTE",
                    "Este pago ya no está pendiente (estado " + pago.getEstado() + ").");
        }
        boolean emitida = Boolean.TRUE.equals(tx.execute(t -> {
            int filas = jdbc.sql("UPDATE pago SET estado = 'APROBADO' WHERE id = :id AND estado = 'PENDIENTE'")
                    .param("id", pagoId).update();
            if (filas != 1) {
                throw ExcepcionEnZona.conflicto("PAGO_NO_PENDIENTE", "Este pago ya no está pendiente.");
            }
            if (!confirmar(orden.getId(), orden.getAsistenteId(), pedidoDe(orden.getId()))) {
                reembolsar(pagoId, pago.getReferenciaPasarela(), pago.getMonto());
                return false;
            }
            return true;
        }));
        if (!emitida) {
            throw ExcepcionEnZona.conflicto("ORDEN_NO_PENDIENTE",
                    "La orden ya no estaba pendiente (venció o el evento se canceló). El pago se reembolsó.");
        }
        log.info("SIMULACIÓN · pago {} de la orden {} confirmado por la cuenta {}", pagoId, orden.getId(), yo.id());
        return respuesta(orden.getId());
    }

    /** Las reservas con pago pendiente vencen a las 24 horas y devuelven sus lugares. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    public void vencerPendientes() {
        Instant limite = reloj.instant().minus(HORAS_VIGENCIA_PENDIENTE, ChronoUnit.HOURS);
        List<Long> vencidas = jdbc.sql("SELECT id FROM orden WHERE estado = 'PENDIENTE' AND fecha_creacion < :limite")
                .param("limite", java.sql.Timestamp.from(limite)).query(Long.class).list();
        for (Long id : vencidas) {
            try {
                tx.executeWithoutResult(t -> {
                    if (liberar(id, false)) {
                        jdbc.sql("UPDATE pago SET estado = 'RECHAZADO' WHERE orden_id = :id AND estado = 'PENDIENTE'")
                                .param("id", id).update();
                        log.info("Orden {} vencida: se liberaron sus lugares", id);
                    }
                });
            } catch (RuntimeException e) {
                log.error("No se pudo vencer la orden {}", id, e);
            }
        }
    }

    // ==================================================================
    //  Consulta
    // ==================================================================

    /** La orden con su último pago (una consulta) y sus boletos (otra). */
    public OrdenCreadaDto respuesta(Long ordenId) {
        List<BoletoDto> emitidos = boletos.deOrden(ordenId);
        return jdbc.sql("""
                SELECT o.id, o.evento_id, o.asistente_id, o.estado, o.total, o.fecha_creacion,
                       p.id AS pago_id, p.estado AS estado_pago, p.referencia_pasarela, p.medio
                  FROM orden o
                  LEFT JOIN LATERAL (SELECT * FROM pago x WHERE x.orden_id = o.id ORDER BY x.id DESC LIMIT 1) p ON TRUE
                 WHERE o.id = :id
                """).param("id", ordenId)
                .query((rs, n) -> {
                    String estadoPago = rs.getString("estado_pago");
                    return new OrdenCreadaDto(rs.getLong("id"), rs.getLong("evento_id"), rs.getLong("asistente_id"),
                            rs.getString("estado"), estadoPago,
                            rs.getBigDecimal("total").setScale(2, RoundingMode.HALF_UP).toPlainString(), "MXN",
                            "PENDIENTE".equals(estadoPago) ? rs.getString("referencia_pasarela") : null,
                            (Long) rs.getObject("pago_id"), rs.getString("medio"),
                            rs.getTimestamp("fecha_creacion").toInstant(),
                            pasarela.esSimulada() && estadoPago != null, emitidos);
                })
                .optional().orElseThrow(() -> ExcepcionEnZona.noEncontrado("La orden no existe."));
    }

    public OrdenCreadaDto obtener(Long ordenId, UsuarioActual yo) {
        OrdenCreadaDto o = respuesta(ordenId);
        if (!o.asistenteId().equals(yo.id()) && !yo.esAdmin()) {
            throw ExcepcionEnZona.noEncontrado("La orden no existe.");
        }
        return o;
    }

    public List<OrdenCreadaDto> deUsuario(Long usuarioId) {
        return jdbc.sql("SELECT id FROM orden WHERE asistente_id = :id ORDER BY fecha_creacion DESC, id DESC LIMIT 100")
                .param("id", usuarioId).query(Long.class).list().stream().map(this::respuesta).toList();
    }
}
