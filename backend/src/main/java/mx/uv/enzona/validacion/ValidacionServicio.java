package mx.uv.enzona.validacion;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import mx.uv.enzona.common.error.ExcepcionEnZona;
import mx.uv.enzona.common.seguridad.UsuarioActual;
import mx.uv.enzona.common.util.FirmaHmac;
import mx.uv.enzona.common.util.FormatoFecha;
import mx.uv.enzona.eventos.EventoConsultas;
import mx.uv.enzona.eventos.EventoDtos.EventoDto;
import mx.uv.enzona.validacion.ValidacionDtos.EntradaManifiestoDto;
import mx.uv.enzona.validacion.ValidacionDtos.EscaneoOfflineDto;
import mx.uv.enzona.validacion.ValidacionDtos.LoteDto;
import mx.uv.enzona.validacion.ValidacionDtos.LoteResultadoDto;
import mx.uv.enzona.validacion.ValidacionDtos.ManifiestoDto;
import mx.uv.enzona.validacion.ValidacionDtos.Motivo;
import mx.uv.enzona.validacion.ValidacionDtos.RegistroDto;
import mx.uv.enzona.validacion.ValidacionDtos.ResultadoDto;

/**
 * Validación de acceso (RF-15, RNF-02, RNF-08). Separa dos comprobaciones:
 *
 *  - AUTENTICIDAD: la firma HMAC se verifica ANTES de tocar la base.
 *  - UN SOLO USO: `UPDATE boleto SET estado = 'USADO' ... WHERE estado = 'VALIDO'`
 *    y se exige exactamente una fila afectada. Si dos terminales escanean el
 *    mismo boleto a la vez, solo una obtiene esa fila.
 */
@Service
public class ValidacionServicio {

    private record BoletoLeido(long id, String estado, Instant fechaUso, long eventoId, String eventoNombre,
            String estadoEvento, String tipo, String asiento) {
    }

    private final FirmaHmac firma;
    private final EventoConsultas eventos;
    private final FormatoFecha formato;
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;
    private final Clock reloj;

    public ValidacionServicio(FirmaHmac firma, EventoConsultas eventos, FormatoFecha formato, TransactionTemplate tx,
            JdbcClient jdbc, Clock reloj) {
        this.firma = firma;
        this.eventos = eventos;
        this.formato = formato;
        this.tx = tx;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------ permisos

    /** Valida en un evento quien fue designado para él, su organizador o un administrador. */
    private EventoDto exigirAcceso(Long eventoId, UsuarioActual yo) {
        EventoDto e = eventos.porId(eventoId).orElseThrow(() -> ExcepcionEnZona.noEncontrado("El evento no existe."));
        boolean designado = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM evento_personal_acceso WHERE evento_id = :evento AND usuario_id = :usuario)
                """).param("evento", eventoId).param("usuario", yo.id()).query(Boolean.class).single();
        if (!designado && !yo.esAdmin() && !yo.id().equals(e.organizadorId())) {
            throw ExcepcionEnZona.prohibido("No estás designado como personal de acceso de este evento.");
        }
        return e;
    }

    public List<EventoDto> eventosAsignados(UsuarioActual yo) {
        if (yo.esAdmin()) {
            return eventos.todos().stream()
                    .filter(e -> "PUBLICADO".equals(e.estado()) || "REALIZADO".equals(e.estado())).toList();
        }
        return eventos.dePersonalDeAcceso(yo.id());
    }

    // ------------------------------------------------------------ validación

    public ResultadoDto validar(UsuarioActual yo, String contenidoQr, Long eventoId) {
        EventoDto evento = exigirAcceso(eventoId, yo);
        return tx.execute(t -> procesar(yo.id(), evento, contenidoQr, false, null, null));
    }

    private Optional<BoletoLeido> buscar(String codigo) {
        return jdbc.sql("""
                SELECT b.id, b.estado, b.fecha_uso, o.evento_id, e.nombre AS evento_nombre, e.estado AS estado_evento,
                       t.nombre AS tipo,
                       CASE WHEN a.id IS NULL THEN NULL
                            ELSE a.seccion || ' · Fila ' || a.fila || ' · Asiento ' || a.numero END AS asiento
                  FROM boleto b
                  JOIN orden o ON o.id = b.orden_id
                  JOIN evento e ON e.id = o.evento_id
                  JOIN tipo_boleto t ON t.id = b.tipo_boleto_id
                  LEFT JOIN asiento a ON a.id = b.asiento_id
                 WHERE b.codigo = :codigo
                """).param("codigo", codigo)
                .query((rs, n) -> {
                    Timestamp uso = rs.getTimestamp("fecha_uso");
                    return new BoletoLeido(rs.getLong("id"), rs.getString("estado"),
                            uso == null ? null : uso.toInstant(), rs.getLong("evento_id"),
                            rs.getString("evento_nombre"), rs.getString("estado_evento"), rs.getString("tipo"),
                            rs.getString("asiento"));
                })
                .optional();
    }

    /**
     * Procesa un escaneo dentro de una transacción y lo deja en la bitácora.
     *
     * @param fechaEscaneo momento del escaneo en el dispositivo (modo sin conexión) o null si es en línea
     */
    private ResultadoDto procesar(Long validadorId, EventoDto evento, String contenidoQr, boolean offline,
            Instant fechaEscaneo, String claveCliente) {
        Instant ahora = reloj.instant();
        Instant momento = fechaEscaneo != null && !fechaEscaneo.isAfter(ahora) ? fechaEscaneo : ahora;
        String contenido = contenidoQr == null ? "" : contenidoQr.trim();
        String[] partes = contenido.split("\\.");

        // 1) Formato y autenticidad, sin consultar la base
        if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) {
            return registrar(validadorId, evento, null, recortar(contenido), Motivo.FIRMA_INVALIDA,
                    "El contenido escaneado no tiene el formato de un boleto EnZona.", null, null, null, offline,
                    momento, claveCliente);
        }
        String codigo = partes[0];
        if (!firma.esAutentica(codigo, partes[1])) {
            return registrar(validadorId, evento, null, recortar(codigo), Motivo.FIRMA_INVALIDA,
                    "La firma digital no coincide. Este boleto no fue emitido por EnZona.", null, null, null, offline,
                    momento, claveCliente);
        }

        // 2) Un solo uso: solo prospera si el boleto sigue VALIDO y es de este evento
        int filas = jdbc.sql("""
                UPDATE boleto SET estado = 'USADO', fecha_uso = :momento
                 WHERE codigo = :codigo AND estado = 'VALIDO'
                   AND orden_id IN (SELECT id FROM orden WHERE evento_id = :evento)
                """).param("momento", Timestamp.from(momento)).param("codigo", codigo)
                .param("evento", evento.id()).update();

        Optional<BoletoLeido> leido = buscar(codigo);
        if (leido.isEmpty()) {
            return registrar(validadorId, evento, null, codigo, Motivo.NO_ENCONTRADO,
                    "El código no corresponde a ningún boleto emitido.", null, null, null, offline, momento,
                    claveCliente);
        }
        BoletoLeido b = leido.get();
        if (filas == 1) {
            return registrar(validadorId, evento, b.id(), codigo, Motivo.PERMITIDO,
                    b.eventoNombre() + " · " + b.tipo(), b.asiento(), b.tipo(), momento, offline, momento,
                    claveCliente);
        }
        if (b.eventoId() != evento.id()) {
            return registrar(validadorId, evento, b.id(), codigo, Motivo.OTRO_EVENTO,
                    "Este boleto es de «" + b.eventoNombre() + "», no de «" + evento.nombre() + "».", b.asiento(),
                    b.tipo(), null, offline, momento, claveCliente);
        }
        return switch (b.estado()) {
            case "USADO" -> registrar(validadorId, evento, b.id(), codigo, Motivo.YA_USADO,
                    "Se registró en el acceso el "
                            + (b.fechaUso() == null ? "—" : formato.largo(b.fechaUso())) + ".",
                    b.asiento(), b.tipo(), b.fechaUso(), offline, momento, claveCliente);
            case "CANCELADO" -> "CANCELADO".equals(b.estadoEvento())
                    ? registrar(validadorId, evento, b.id(), codigo, Motivo.EVENTO_CANCELADO,
                            "El organizador canceló «" + b.eventoNombre() + "». Este boleto ya no da acceso.",
                            b.asiento(), b.tipo(), null, offline, momento, claveCliente)
                    : registrar(validadorId, evento, b.id(), codigo, Motivo.CANCELADO, "El boleto se anuló.",
                            b.asiento(), b.tipo(), null, offline, momento, claveCliente);
            default -> registrar(validadorId, evento, b.id(), codigo, Motivo.EXPIRADO,
                    "La vigencia de este boleto ya terminó.", b.asiento(), b.tipo(), null, offline, momento,
                    claveCliente);
        };
    }

    private static String recortar(String s) {
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private ResultadoDto registrar(Long validadorId, EventoDto evento, Long boletoId, String codigo, Motivo motivo,
            String detalle, String asiento, String tipo, Instant fechaUso, boolean offline, Instant fechaEscaneo,
            String claveCliente) {
        boolean permitido = motivo == Motivo.PERMITIDO;
        jdbc.sql("""
                INSERT INTO validacion_acceso (boleto_id, validador_id, fecha_hora, resultado, offline, sincronizado,
                                               motivo, codigo_leido, evento_id, fecha_escaneo, clave_cliente)
                VALUES (:boleto, :validador, :ahora, CAST(:resultado AS resultado_acceso), :offline, TRUE,
                        :motivo, :codigo, :evento, :escaneo, :clave)
                """)
                .param("boleto", boletoId, Types.BIGINT)
                .param("validador", validadorId)
                .param("ahora", Timestamp.from(reloj.instant()))
                .param("resultado", permitido ? "PERMITIDO" : "RECHAZADO")
                .param("offline", offline)
                .param("motivo", motivo.name())
                .param("codigo", codigo)
                .param("evento", evento.id())
                .param("escaneo", Timestamp.from(fechaEscaneo))
                .param("clave", claveCliente, Types.VARCHAR)
                .update();
        return new ResultadoDto(permitido, motivo.name(), motivo.titulo(), detalle, boletoId == null ? null : codigo,
                asiento, evento.nombre(), tipo, fechaUso, null, claveCliente);
    }

    // ------------------------------------------------------------ modo sin conexión

    @Transactional(readOnly = true)
    public ManifiestoDto manifiesto(Long eventoId, UsuarioActual yo) {
        EventoDto evento = exigirAcceso(eventoId, yo);
        List<EntradaManifiestoDto> entradas = jdbc.sql("""
                SELECT b.codigo, b.qr_firma, b.estado, t.nombre AS tipo,
                       CASE WHEN a.id IS NULL THEN NULL
                            ELSE a.seccion || ' · Fila ' || a.fila || ' · Asiento ' || a.numero END AS asiento
                  FROM boleto b
                  JOIN orden o ON o.id = b.orden_id
                  JOIN tipo_boleto t ON t.id = b.tipo_boleto_id
                  LEFT JOIN asiento a ON a.id = b.asiento_id
                 WHERE o.evento_id = :evento
                 ORDER BY b.id
                """).param("evento", eventoId)
                .query((rs, n) -> {
                    String codigo = rs.getString("codigo");
                    return new EntradaManifiestoDto(FirmaHmac.huella(codigo + "." + rs.getString("qr_firma")),
                            rs.getString("estado"), rs.getString("tipo"), rs.getString("asiento"),
                            codigo.substring(Math.max(0, codigo.length() - 4)));
                })
                .list();
        return new ManifiestoDto(eventoId, evento.nombre(), reloj.instant(), "SHA-256(codigo.firma)", entradas.size(),
                entradas);
    }

    /**
     * Reconcilia las validaciones hechas sin conexión con la misma regla de un
     * solo uso. Se procesan de la más antigua a la más reciente, así que dentro
     * del lote prevalece el primer escaneo; si el boleto ya había sido admitido
     * (en línea o por otro dispositivo), el escaneo se asienta como YA_USADO y se
     * marca `duplicada` para revisión del organizador. Reenviar el mismo lote no
     * duplica registros: cada escaneo lleva su `claveCliente`.
     */
    public LoteResultadoDto sincronizar(UsuarioActual yo, LoteDto lote) {
        EventoDto evento = exigirAcceso(lote.eventoId(), yo);
        List<EscaneoOfflineDto> ordenadas = new ArrayList<>(lote.validaciones());
        ordenadas.sort(Comparator.comparing(EscaneoOfflineDto::fechaEscaneo));

        List<ResultadoDto> resultados = new ArrayList<>();
        for (EscaneoOfflineDto escaneo : ordenadas) {
            ResultadoDto r = tx.execute(t -> yaSincronizado(yo.id(), escaneo.claveCliente()).orElseGet(
                    () -> procesar(yo.id(), evento, escaneo.contenidoQr(), true, escaneo.fechaEscaneo().toInstant(),
                            escaneo.claveCliente())));
            boolean duplicada = Motivo.YA_USADO.name().equals(r.resultado());
            resultados.add(new ResultadoDto(r.permitido(), r.resultado(), r.titulo(), r.detalle(), r.codigo(),
                    r.asiento(), r.eventoNombre(), r.tipoNombre(), r.fechaUso(), duplicada, escaneo.claveCliente()));
        }
        int permitidas = (int) resultados.stream().filter(ResultadoDto::permitido).count();
        int duplicadas = (int) resultados.stream().filter(r -> Boolean.TRUE.equals(r.duplicada())).count();
        return new LoteResultadoDto(resultados.size(), permitidas, resultados.size() - permitidas, duplicadas,
                resultados);
    }

    /** Si el dispositivo ya había enviado este escaneo, se devuelve lo que se registró entonces. */
    private Optional<ResultadoDto> yaSincronizado(Long validadorId, String claveCliente) {
        return jdbc.sql("""
                SELECT v.motivo, v.codigo_leido, v.boleto_id, e.nombre AS evento_nombre
                  FROM validacion_acceso v LEFT JOIN evento e ON e.id = v.evento_id
                 WHERE v.validador_id = :validador AND v.clave_cliente = :clave
                """).param("validador", validadorId).param("clave", claveCliente)
                .query((rs, n) -> {
                    Motivo m = Motivo.valueOf(rs.getString("motivo"));
                    return new ResultadoDto(m == Motivo.PERMITIDO, m.name(), m.titulo(),
                            "Este escaneo ya se había sincronizado.",
                            rs.getObject("boleto_id") == null ? null : rs.getString("codigo_leido"), null,
                            rs.getString("evento_nombre"), null, null, null, claveCliente);
                })
                .optional();
    }

    // ------------------------------------------------------------ bitácora

    @Transactional(readOnly = true)
    public List<RegistroDto> bitacora(UsuarioActual yo, Long eventoId, int limite) {
        StringBuilder sql = new StringBuilder("""
                SELECT v.id, v.codigo_leido, v.evento_id, e.nombre AS evento_nombre, v.fecha_hora, v.fecha_escaneo,
                       v.resultado, v.motivo, v.offline, v.sincronizado
                  FROM validacion_acceso v LEFT JOIN evento e ON e.id = v.evento_id
                 WHERE v.validador_id = :validador
                """);
        if (eventoId != null) {
            sql.append(" AND v.evento_id = :evento");
        }
        sql.append(" ORDER BY v.id DESC LIMIT :limite");
        JdbcClient.StatementSpec consulta = jdbc.sql(sql.toString()).param("validador", yo.id())
                .param("limite", Math.max(1, Math.min(limite, 100)));
        if (eventoId != null) {
            consulta = consulta.param("evento", eventoId);
        }
        return consulta.query((rs, n) -> {
            Timestamp escaneo = rs.getTimestamp("fecha_escaneo");
            String motivo = rs.getString("motivo");
            return new RegistroDto(rs.getLong("id"), rs.getString("codigo_leido"), (Long) rs.getObject("evento_id"),
                    rs.getString("evento_nombre"), rs.getTimestamp("fecha_hora").toInstant(),
                    escaneo == null ? null : escaneo.toInstant(), rs.getString("resultado"), motivo,
                    motivo == null ? null : Motivo.valueOf(motivo).titulo(), rs.getBoolean("offline"),
                    rs.getBoolean("sincronizado"));
        }).list();
    }
}
