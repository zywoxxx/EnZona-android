package mx.uv.enzona;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** Validación de acceso (RF-15, RNF-02, RNF-08): firma, un solo uso, manifiesto y sincronización sin conexión. */
class ValidacionIT extends PruebaIntegracion {

    private record Escenario(Cuenta organizador, Cuenta validador, Cuenta asistente, long evento, List<String> qrs) {
    }

    private Escenario escenario(int boletos) {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Acceso", true, "50.00", 30, null);
        Cuenta validador = validadorDe(evento, org);
        Cuenta ana = cuentaNueva("asistente");
        Respuesta compra = comprar(ana, evento, tipoDe(evento), boletos, null);
        assertThat(compra.estado()).isEqualTo(201);
        List<String> qrs = new ArrayList<>();
        compra.cuerpo().get("boletos").forEach(b -> qrs.add(b.get("contenidoQr").asText()));
        return new Escenario(org, validador, ana, evento, qrs);
    }

    private Respuesta validar(Cuenta validador, long evento, String qr) {
        return post("/api/validaciones", validador.token(), mapa("contenidoQr", qr, "eventoId", evento));
    }

    private static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("CP-12/CP-13 · el primer escaneo permite el acceso y el segundo lo rechaza como ya utilizado")
    void unSoloUso() {
        Escenario e = escenario(2);

        Respuesta primero = validar(e.validador(), e.evento(), e.qrs().get(0));
        assertThat(primero.estado()).isEqualTo(200);
        assertThat(primero.cuerpo().get("permitido").asBoolean()).isTrue();
        assertThat(primero.cuerpo().get("resultado").asText()).isEqualTo("PERMITIDO");
        assertThat(primero.cuerpo().get("titulo").asText()).isEqualTo("Acceso permitido");

        Respuesta segundo = validar(e.validador(), e.evento(), e.qrs().get(0));
        assertThat(segundo.estado()).isEqualTo(200);
        assertThat(segundo.cuerpo().get("permitido").asBoolean()).isFalse();
        assertThat(segundo.cuerpo().get("resultado").asText()).isEqualTo("YA_USADO");

        // CP-MULTI-10: el otro boleto de la misma orden se valida por separado
        assertThat(validar(e.validador(), e.evento(), e.qrs().get(1)).cuerpo().get("permitido").asBoolean()).isTrue();

        assertThat(boletosDeEvento(e.evento(), "USADO")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM validacion_acceso WHERE evento_id = :e AND resultado = 'PERMITIDO'")
                .param("e", e.evento()).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM validacion_acceso WHERE evento_id = :e AND motivo = 'YA_USADO'")
                .param("e", e.evento()).query(Integer.class).single()).isEqualTo(1);

        Respuesta bitacora = get("/api/validaciones?eventoId=" + e.evento(), e.validador().token());
        assertThat(bitacora.cuerpo()).hasSize(3);
        assertThat(bitacora.cuerpo().get(0).get("motivo").asText()).isEqualTo("PERMITIDO");
    }

    @Test
    @DisplayName("RNF-02 · firma alterada, formato inválido, código inexistente y boleto de otro evento se rechazan")
    void rechazos() {
        Escenario e = escenario(1);
        String qr = e.qrs().get(0);
        String codigo = qr.split("\\.")[0];
        String firma = qr.split("\\.")[1];

        Respuesta falsa = validar(e.validador(), e.evento(), codigo + "." + "0".repeat(firma.length()));
        assertThat(falsa.cuerpo().get("resultado").asText()).isEqualTo("FIRMA_INVALIDA");
        assertThat(falsa.cuerpo().get("titulo").asText()).isEqualTo("Boleto falsificado");

        assertThat(validar(e.validador(), e.evento(), "esto no es un boleto").cuerpo().get("resultado").asText())
                .isEqualTo("FIRMA_INVALIDA");
        // Un código distinto con la firma de otro boleto tampoco pasa
        assertThat(validar(e.validador(), e.evento(), "EZ-0000-0000-0000." + firma).cuerpo().get("resultado").asText())
                .isEqualTo("FIRMA_INVALIDA");

        // Ningún rechazo consumió el boleto
        assertThat(boletosDeEvento(e.evento(), "VALIDO")).isEqualTo(1);

        // Boleto auténtico presentado en otro evento del mismo organizador
        long otro = eventoPublicado(e.organizador(), "Otro acceso", true, "10.00", 5, null);
        Respuesta ajeno = post("/api/validaciones", e.organizador().token(), mapa("contenidoQr", qr, "eventoId", otro));
        assertThat(ajeno.cuerpo().get("resultado").asText()).isEqualTo("OTRO_EVENTO");
        assertThat(boletosDeEvento(e.evento(), "VALIDO")).isEqualTo(1);

        // Quien no está designado para el evento no puede validar
        Cuenta extrano = validadorDe(otro, e.organizador());
        assertThat(validar(extrano, e.evento(), qr).estado()).isEqualTo(403);
        assertThat(validar(e.asistente(), e.evento(), qr).estado()).isEqualTo(403);
    }

    @Test
    @DisplayName("CP-13 con concurrencia real · seis terminales escanean el mismo QR a la vez y solo una lo admite")
    void mismoQrALaVez() throws Exception {
        Escenario e = escenario(1);
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Respuesta>> futuros = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futuros.add(hilos.submit(() -> {
                salida.await();
                return validar(e.validador(), e.evento(), e.qrs().get(0));
            }));
        }
        salida.countDown();
        int permitidos = 0;
        int usados = 0;
        for (Future<Respuesta> f : futuros) {
            Respuesta r = f.get(60, TimeUnit.SECONDS);
            assertThat(r.estado()).isEqualTo(200);
            if (r.cuerpo().get("permitido").asBoolean()) {
                permitidos++;
            } else if ("YA_USADO".equals(r.cuerpo().get("resultado").asText())) {
                usados++;
            }
        }
        hilos.shutdownNow();
        assertThat(permitidos).isEqualTo(1);
        assertThat(usados).isEqualTo(5);
    }

    @Test
    @DisplayName("CP-14 · manifiesto sin conexión y sincronización por lote con detección de duplicados")
    void sinConexion() throws Exception {
        Escenario e = escenario(3);

        // 1) El validador descarga el manifiesto: huellas SHA-256, sin códigos ni firmas
        Respuesta manifiesto = get("/api/eventos/" + e.evento() + "/manifiesto", e.validador().token());
        assertThat(manifiesto.estado()).isEqualTo(200);
        assertThat(manifiesto.cuerpo().get("total").asInt()).isEqualTo(3);
        Set<String> huellas = new HashSet<>();
        manifiesto.cuerpo().get("boletos").forEach(b -> huellas.add(b.get("huella").asText()));
        for (String qr : e.qrs()) {
            assertThat(huellas).contains(sha256(qr));
            assertThat(manifiesto.cuerpo().toString()).doesNotContain(qr).doesNotContain(qr.split("\\.")[1]);
        }
        // Un QR inventado no pertenece al manifiesto
        assertThat(huellas).doesNotContain(sha256("EZ-1111-2222-3333.abcdefabcdefabcdefabcdefabcdefab"));
        assertThat(get("/api/eventos/" + e.evento() + "/manifiesto", e.asistente().token()).estado()).isEqualTo(403);

        // 2) Mientras tanto, otra terminal CON conexión admite el boleto [2]
        assertThat(validar(e.organizador(), e.evento(), e.qrs().get(2)).cuerpo().get("permitido").asBoolean()).isTrue();

        // 3) El dispositivo sin conexión escaneó [0], [1], otra vez [0] y [2]; ahora sincroniza
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30);
        List<Map<String, Object>> escaneos = List.of(
                mapa("contenidoQr", e.qrs().get(0), "fechaEscaneo", base.plusMinutes(5).toString(), "claveCliente", "escaneo-B-" + UUID.randomUUID()),
                mapa("contenidoQr", e.qrs().get(0), "fechaEscaneo", base.plusMinutes(1).toString(), "claveCliente", "escaneo-A-" + UUID.randomUUID()),
                mapa("contenidoQr", e.qrs().get(1), "fechaEscaneo", base.plusMinutes(2).toString(), "claveCliente", "escaneo-C-" + UUID.randomUUID()),
                mapa("contenidoQr", e.qrs().get(2), "fechaEscaneo", base.plusMinutes(3).toString(), "claveCliente", "escaneo-D-" + UUID.randomUUID()));
        Map<String, Object> lote = mapa("eventoId", e.evento(), "validaciones", escaneos);

        Respuesta r = post("/api/validaciones/lote", e.validador().token(), lote);

        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(200);
        assertThat(r.cuerpo().get("recibidas").asInt()).isEqualTo(4);
        assertThat(r.cuerpo().get("permitidas").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("duplicadas").asInt()).isEqualTo(2);
        for (JsonNode x : r.cuerpo().get("resultados")) {
            String clave = x.get("claveCliente").asText();
            if (clave.startsWith("escaneo-A-") || clave.startsWith("escaneo-C-")) {
                // Prevalece el escaneo más antiguo de cada boleto
                assertThat(x.get("resultado").asText()).isEqualTo("PERMITIDO");
                assertThat(x.get("duplicada").asBoolean()).isFalse();
            } else {
                assertThat(x.get("resultado").asText()).isEqualTo("YA_USADO");
                assertThat(x.get("duplicada").asBoolean()).isTrue();
            }
        }
        assertThat(boletosDeEvento(e.evento(), "USADO")).isEqualTo(3);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM validacion_acceso WHERE evento_id = :e AND offline")
                .param("e", e.evento()).query(Integer.class).single()).isEqualTo(4);

        // 4) Reenviar el mismo lote (se perdió la respuesta) no duplica la bitácora ni cambia el resultado
        Respuesta reenvio = post("/api/validaciones/lote", e.validador().token(), lote);
        assertThat(reenvio.cuerpo().get("permitidas").asInt()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM validacion_acceso WHERE evento_id = :e AND offline")
                .param("e", e.evento()).query(Integer.class).single()).isEqualTo(4);
    }
}
