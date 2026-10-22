package mx.uv.enzona;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cancelación de eventos (RF-14, CP-16, CP-CANCEL-01..07). */
class CancelacionIT extends PruebaIntegracion {

    private Respuesta politica(Cuenta c, long evento) {
        return get("/api/eventos/" + evento + "/politica-cancelacion", c.token());
    }

    private Respuesta cancelar(Cuenta c, long evento, String motivo, boolean aceptaComision) {
        return post("/api/eventos/" + evento + "/cancelacion", c.token(),
                mapa("motivo", motivo, "aceptaComision", aceptaComision));
    }

    private String estadoEvento(long evento) {
        return jdbc.sql("SELECT estado::text FROM evento WHERE id = :id").param("id", evento).query(String.class).single();
    }

    @Test
    @DisplayName("CP-CANCEL-01 · evento gratuito: se cancela sin comisión, anula las confirmaciones y avisa")
    void gratuito() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Feria", false, null, 20, null);
        Cuenta ana = cuentaNueva("ana");
        Cuenta luis = cuentaNueva("luis");
        assertThat(comprar(ana, evento, null, 1, null, claveNueva(), "GRATIS", null).estado()).isEqualTo(201);
        assertThat(comprar(luis, evento, null, 1, null, claveNueva(), "GRATIS", null).estado()).isEqualTo(201);

        Respuesta p = politica(org, evento);
        assertThat(p.cuerpo().get("permitida").asBoolean()).isTrue();
        assertThat(p.cuerpo().get("tipo").asText()).isEqualTo("GRATUITO");
        assertThat(p.cuerpo().get("comision").asText()).isEqualTo("0.00");
        assertThat(p.cuerpo().get("boletosAfectados").asInt()).isEqualTo(2);

        Respuesta r = cancelar(org, evento, null, false);

        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(200);
        assertThat(r.cuerpo().get("boletosAnulados").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("asistentesNotificados").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("comision").asText()).isEqualTo("0.00");
        assertThat(estadoEvento(evento)).isEqualTo("CANCELADO");
        assertThat(boletosDeEvento(evento, "CANCELADO")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orden WHERE evento_id = :e AND estado = 'CANCELADA'")
                .param("e", evento).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM cargo_organizador WHERE evento_id = :e").param("e", evento)
                .query(Integer.class).single()).isZero();

        Respuesta avisos = get("/api/usuarios/me/notificaciones", ana.token());
        assertThat(avisos.cuerpo().get(0).get("tipo").asText()).isEqualTo("EVENTO_CANCELADO");
        // El evento cancelado sale del catálogo y ya no admite compras
        assertThat(get("/api/eventos?q=Feria", null).cuerpo().toString()).doesNotContain("\"id\":" + evento + ",");
        assertThat(comprar(cuentaNueva("tarde"), evento, null, 1, null, claveNueva(), "GRATIS", null).codigo())
                .isEqualTo("EVENTO_CANCELADO");
    }

    @Test
    @DisplayName("CP-CANCEL-02/03 · de pago con ventas: comisión del 10 %, reembolso del 100 %, asientos liberados, todo en una transacción")
    void dePagoConVentas() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Concierto", true, "120.00", 0, seccion("Luneta", 3, 5));
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("ana");
        Cuenta luis = cuentaNueva("luis");
        assertThat(comprar(ana, evento, tipo, 2, asientosLibres(evento, ana.token(), 2)).estado()).isEqualTo(201);
        assertThat(comprar(luis, evento, tipo, 1, asientosLibres(evento, luis.token(), 1)).estado()).isEqualTo(201);

        Respuesta p = politica(org, evento);
        assertThat(p.cuerpo().get("tipo").asText()).isEqualTo("DE_PAGO_CON_COMISION");
        assertThat(p.cuerpo().get("importeCobrado").asText()).isEqualTo("360.00");
        assertThat(p.cuerpo().get("comision").asText()).isEqualTo("36.00");
        assertThat(p.cuerpo().get("reembolso").asText()).isEqualTo("360.00");

        // Sin aceptar la comisión no se cancela y nada cambia
        Respuesta sinAceptar = cancelar(org, evento, null, false);
        assertThat(sinAceptar.estado()).isEqualTo(422);
        assertThat(sinAceptar.codigo()).isEqualTo("COMISION_NO_ACEPTADA");
        assertThat(estadoEvento(evento)).isEqualTo("PUBLICADO");
        assertThat(boletosDeEvento(evento, "VALIDO")).isEqualTo(3);

        Respuesta r = cancelar(org, evento, "Cambio de sede", true);

        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(200);
        assertThat(r.cuerpo().get("importeReembolsado").asText()).isEqualTo("360.00");
        assertThat(r.cuerpo().get("comision").asText()).isEqualTo("36.00");
        assertThat(r.cuerpo().get("boletosAnulados").asInt()).isEqualTo(3);
        assertThat(r.cuerpo().get("asistentesNotificados").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("canceladoPor").asLong()).isEqualTo(org.id());

        assertThat(boletosDeEvento(evento, "CANCELADO")).isEqualTo(3);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE evento_id = :e AND estado <> 'DISPONIBLE'")
                .param("e", evento).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orden WHERE evento_id = :e AND estado = 'REEMBOLSADA'")
                .param("e", evento).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM pago p JOIN orden o ON o.id = p.orden_id
                 WHERE o.evento_id = :e AND p.estado = 'REEMBOLSADO' AND p.referencia_reembolso LIKE 'SIM-REEMB-%'
                   AND p.fecha_reembolso IS NOT NULL
                """).param("e", evento).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT comision_cancelacion FROM evento WHERE id = :e").param("e", evento)
                .query(BigDecimal.class).single()).isEqualByComparingTo("36.00");
        assertThat(jdbc.sql("SELECT importe FROM cargo_organizador WHERE evento_id = :e AND NOT pagado")
                .param("e", evento).query(BigDecimal.class).single()).isEqualByComparingTo("36.00");

        Respuesta cargos = get("/api/usuarios/me/cargos", org.token());
        assertThat(cargos.cuerpo()).hasSize(1);
        assertThat(cargos.cuerpo().get(0).get("importe").asText()).isEqualTo("36.00");
        assertThat(get("/api/usuarios/me/notificaciones", ana.token()).cuerpo().get(0).get("texto").asText())
                .contains("reembolso completo").contains("$240.00 MXN");

        // CP-CANCEL-05: ya cancelado → 409
        Respuesta otraVez = cancelar(org, evento, null, true);
        assertThat(otraVez.estado()).isEqualTo(409);
        assertThat(politica(org, evento).cuerpo().get("permitida").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CP-CANCEL-04 · de pago sin ventas: procede como gratuito, comisión 0")
    void dePagoSinVentas() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Sin ventas", true, "200.00", 10, null);
        // Un pago rechazado no cuenta como venta
        assertThat(comprar(cuentaNueva("rechazo"), evento, tipoDe(evento), 1, null, claveNueva(), "TARJETA",
                "tok-rechazo").estado()).isEqualTo(422);

        Respuesta p = politica(org, evento);
        assertThat(p.cuerpo().get("tipo").asText()).isEqualTo("DE_PAGO_SIN_VENTAS");
        assertThat(p.cuerpo().get("comision").asText()).isEqualTo("0.00");

        Respuesta r = cancelar(org, evento, null, false);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("comision").asText()).isEqualTo("0.00");
        assertThat(r.cuerpo().get("boletosAnulados").asInt()).isZero();
    }

    @Test
    @DisplayName("CP-CANCEL-05 · un evento que ya comenzó no se cancela por ningún rol: solo se marca como finalizado")
    void eventoIniciado() {
        Cuenta org = organizadorNuevo();
        Cuenta admin = adminNuevo();
        long evento = eventoPublicado(org, "Iniciado", true, "30.00", 10, null);
        assertThat(comprar(cuentaNueva("ana"), evento, tipoDe(evento), 1, null).estado()).isEqualTo(201);
        jdbc.sql("UPDATE evento SET fecha_hora_inicio = now() - interval '1 hour' WHERE id = :e").param("e", evento)
                .update();

        assertThat(politica(org, evento).cuerpo().get("permitida").asBoolean()).isFalse();
        assertThat(politica(org, evento).cuerpo().get("motivoBloqueo").asText()).contains("ya comenzó");
        Respuesta r = cancelar(org, evento, null, true);
        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("CANCELACION_BLOQUEADA");
        assertThat(cancelar(admin, evento, "Moderación del contenido", false).estado()).isEqualTo(409);
        assertThat(estadoEvento(evento)).isEqualTo("PUBLICADO");
        assertThat(boletosDeEvento(evento, "VALIDO")).isEqualTo(1);

        Respuesta realizado = post("/api/eventos/" + evento + "/realizado", org.token(), null);
        assertThat(realizado.estado()).isEqualTo(200);
        assertThat(realizado.cuerpo().get("estado").asText()).isEqualTo("REALIZADO");
        assertThat(cancelar(org, evento, null, true).estado()).isEqualTo(409);
    }

    @Test
    @DisplayName("CP-CANCEL-06 · borrador: se cancela sin comisión")
    void borrador() {
        Cuenta org = organizadorNuevo();
        Respuesta creado = post("/api/eventos", org.token(), mapa("nombre", "Borrador", "lugar", "Sala",
                "fechaHoraInicio", enDias(7), "esDePago", true, "precio", 50, "aforo", 10));
        long evento = creado.cuerpo().get("id").asLong();
        assertThat(creado.cuerpo().get("estado").asText()).isEqualTo("BORRADOR");

        assertThat(politica(org, evento).cuerpo().get("tipo").asText()).isEqualTo("BORRADOR");
        assertThat(cancelar(org, evento, null, false).estado()).isEqualTo(200);
        assertThat(estadoEvento(evento)).isEqualTo("CANCELADO");
    }

    @Test
    @DisplayName("CP-CANCEL-07 · cancelación administrativa: motivo obligatorio, reembolso del 100 % y sin comisión")
    void administrativa() {
        Cuenta org = organizadorNuevo();
        Cuenta admin = adminNuevo();
        long evento = eventoPublicado(org, "Moderado", true, "150.00", 10, null);
        assertThat(comprar(cuentaNueva("ana"), evento, tipoDe(evento), 2, null).estado()).isEqualTo(201);

        Respuesta p = politica(admin, evento);
        assertThat(p.cuerpo().get("tipo").asText()).isEqualTo("ADMINISTRATIVA");
        assertThat(p.cuerpo().get("comision").asText()).isEqualTo("0.00");
        assertThat(p.cuerpo().get("reembolso").asText()).isEqualTo("300.00");

        Respuesta sinMotivo = cancelar(admin, evento, "  ", false);
        assertThat(sinMotivo.estado()).isEqualTo(422);
        assertThat(sinMotivo.codigo()).isEqualTo("MOTIVO_OBLIGATORIO");
        assertThat(estadoEvento(evento)).isEqualTo("PUBLICADO");

        Respuesta r = cancelar(admin, evento, "Contenido reportado por la comunidad", false);
        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(200);
        assertThat(r.cuerpo().get("comision").asText()).isEqualTo("0.00");
        assertThat(r.cuerpo().get("importeReembolsado").asText()).isEqualTo("300.00");
        assertThat(r.cuerpo().get("canceladoPor").asLong()).isEqualTo(admin.id());
        assertThat(jdbc.sql("SELECT motivo_cancelacion FROM evento WHERE id = :e").param("e", evento)
                .query(String.class).single()).isEqualTo("Contenido reportado por la comunidad");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM cargo_organizador WHERE evento_id = :e").param("e", evento)
                .query(Integer.class).single()).isZero();
        assertThat(get("/api/admin/eventos", admin.token()).cuerpo().toString())
                .contains("Contenido reportado por la comunidad");
    }

    @Test
    @DisplayName("Solo el organizador del evento o un administrador pueden cancelarlo (403)")
    void permisos() {
        Cuenta org = organizadorNuevo();
        Cuenta otroOrganizador = organizadorNuevo();
        long evento = eventoPublicado(org, "Ajeno", false, null, 10, null);

        assertThat(cancelar(otroOrganizador, evento, null, false).estado()).isEqualTo(403);
        assertThat(politica(otroOrganizador, evento).estado()).isEqualTo(403);
        assertThat(cancelar(cuentaNueva("asistente"), evento, null, false).estado()).isEqualTo(403);
        assertThat(estadoEvento(evento)).isEqualTo("PUBLICADO");
    }

    @Test
    @DisplayName("Una reserva con pago pendiente (OXXO) se libera al cancelar el evento")
    void pendienteAlCancelar() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Con pendiente", true, "70.00", 0, seccion("Luneta", 2, 3));
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("ana");
        List<Long> asientos = asientosLibres(evento, ana.token(), 2);
        Respuesta pendiente = comprar(ana, evento, tipo, 2, asientos, claveNueva(), "SPEI", null);
        assertThat(pendiente.cuerpo().get("estadoPago").asText()).isEqualTo("PENDIENTE");

        // Sin cobro confirmado no hay ventas: comisión 0
        assertThat(politica(org, evento).cuerpo().get("tipo").asText()).isEqualTo("DE_PAGO_SIN_VENTAS");
        assertThat(cancelar(org, evento, null, false).estado()).isEqualTo(200);

        long orden = pendiente.cuerpo().get("ordenId").asLong();
        assertThat(jdbc.sql("SELECT estado::text FROM orden WHERE id = :id").param("id", orden).query(String.class)
                .single()).isEqualTo("CANCELADA");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE evento_id = :e AND estado <> 'DISPONIBLE'")
                .param("e", evento).query(Integer.class).single()).isZero();
        // La confirmación tardía de la pasarela ya no emite boletos
        assertThat(post("/api/pagos/" + pendiente.cuerpo().get("pagoId").asLong() + "/confirmar", ana.token(), null)
                .estado()).isEqualTo(409);
        assertThat(boletosDeEvento(evento, "VALIDO")).isZero();
    }
}
