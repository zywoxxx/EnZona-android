package mx.uv.enzona;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Compra de N boletos (RF-10, RF-11, RF-16, RNF-05, RNF-09): inventario por
 * lote, asientos, idempotencia, pasarela simulada y concurrencia real.
 */
class ComprasIT extends PruebaIntegracion {

    /** Lanza las tareas a la vez (todas esperan la misma señal) y devuelve sus respuestas. */
    private List<Respuesta> aLaVez(List<Callable<Respuesta>> tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Respuesta>> futuros = new ArrayList<>();
            for (Callable<Respuesta> t : tareas) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return t.call();
                }));
            }
            salida.countDown();
            List<Respuesta> respuestas = new ArrayList<>();
            for (Future<Respuesta> f : futuros) {
                respuestas.add(f.get(60, TimeUnit.SECONDS));
            }
            return respuestas;
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("CP-MULTI-01 · compra de 2 boletos sin asiento: una orden, dos QR distintos, total e inventario correctos")
    void dosBoletosSinAsiento() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Torneo", true, "35.00", 40, null);
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("compra");

        Respuesta r = comprar(ana, evento, tipo, 2, null);

        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(201);
        assertThat(r.cuerpo().get("estadoOrden").asText()).isEqualTo("PAGADA");
        assertThat(r.cuerpo().get("estadoPago").asText()).isEqualTo("APROBADO");
        assertThat(r.cuerpo().get("total").asText()).isEqualTo("70.00");
        assertThat(r.cuerpo().get("pagoSimulado").asBoolean()).isTrue();
        JsonNode boletos = r.cuerpo().get("boletos");
        assertThat(boletos).hasSize(2);
        assertThat(boletos.get(0).get("codigo").asText()).isNotEqualTo(boletos.get(1).get("codigo").asText());
        for (JsonNode b : boletos) {
            assertThat(b.get("estado").asText()).isEqualTo("VALIDO");
            assertThat(b.get("firma").asText()).hasSize(32);
            assertThat(b.get("contenidoQr").asText()).isEqualTo(b.get("codigo").asText() + "." + b.get("firma").asText());
            assertThat(b.get("totalEnOrden").asInt()).isEqualTo(2);
        }
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(38);
        assertThat(get("/api/eventos/" + evento, null).cuerpo().get("disponibles").asInt()).isEqualTo(38);

        // Los boletos aparecen en «Mis boletos» con el evento resuelto
        Respuesta mios = get("/api/boletos", ana.token());
        assertThat(mios.cuerpo()).hasSize(2);
        assertThat(mios.cuerpo().get(0).get("evento").get("id").asLong()).isEqualTo(evento);
        // y la orden se puede consultar; otra cuenta no la ve
        assertThat(get("/api/ordenes/" + r.cuerpo().get("ordenId").asLong(), ana.token()).estado()).isEqualTo(200);
        assertThat(get("/api/ordenes/" + r.cuerpo().get("ordenId").asLong(), cuentaNueva("ajena").token()).estado())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("CP-MULTI-02 · compra de 3 boletos con asiento: tres butacas distintas quedan ocupadas")
    void tresBoletosConAsiento() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Concierto", true, "120.00", 0, seccion("Luneta", 3, 6));
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("asientos");
        List<Long> asientos = asientosLibres(evento, ana.token(), 3);

        Respuesta r = comprar(ana, evento, tipo, 3, asientos);

        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(201);
        assertThat(r.cuerpo().get("total").asText()).isEqualTo("360.00");
        Set<Long> emitidos = new HashSet<>();
        r.cuerpo().get("boletos").forEach(b -> emitidos.add(b.get("asientoId").asLong()));
        assertThat(emitidos).containsExactlyInAnyOrderElementsOf(asientos);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE id IN (:ids) AND estado = 'OCUPADO'")
                .param("ids", asientos).query(Integer.class).single()).isEqualTo(3);
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(15);
        assertThat(r.cuerpo().get("boletos").get(0).get("asiento").get("seccion").asText()).isEqualTo("Luneta");
    }

    @Test
    @DisplayName("CP-MULTI-03 · asientos incompletos, repetidos o de otro evento: no se emite nada")
    void asientosInvalidos() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Obra", true, "60.00", 0, seccion("Luneta", 2, 5));
        long otro = eventoPublicado(org, "Otra obra", true, "60.00", 0, seccion("Luneta", 2, 5));
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("invalidos");
        List<Long> dos = asientosLibres(evento, ana.token(), 2);
        List<Long> ajeno = asientosLibres(otro, ana.token(), 1);

        Respuesta incompletos = comprar(ana, evento, tipo, 3, dos);
        assertThat(incompletos.estado()).isEqualTo(400);
        assertThat(incompletos.codigo()).isEqualTo("ASIENTOS_INCOMPLETOS");

        Respuesta repetidos = comprar(ana, evento, tipo, 2, List.of(dos.get(0), dos.get(0)));
        assertThat(repetidos.estado()).isEqualTo(400);
        assertThat(repetidos.codigo()).isEqualTo("ASIENTOS_REPETIDOS");

        Respuesta deOtro = comprar(ana, evento, tipo, 2, List.of(dos.get(0), ajeno.get(0)));
        assertThat(deOtro.estado()).isEqualTo(422);
        assertThat(deOtro.codigo()).isEqualTo("ASIENTO_AJENO");

        assertThat(disponiblesDeTipo(tipo)).isEqualTo(10);
        assertThat(boletosDeEvento(evento, "VALIDO")).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE evento_id = :e AND estado <> 'DISPONIBLE'")
                .param("e", evento).query(Integer.class).single()).isZero();
    }

    @Test
    @DisplayName("CP-MULTI-05 · pedir 3 cuando quedan 2: 409 con cuántos quedan, sin boletos ni cobro")
    void cantidadMayorQueLaDisponibilidad() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Pocos lugares", true, "50.00", 2, null);
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("cupo");

        Respuesta r = comprar(ana, evento, tipo, 3, null);

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("SIN_CUPO");
        assertThat(r.cuerpo().get("disponibles").asInt()).isEqualTo(2);
        assertThat(r.detalle()).contains("Solo quedan 2");
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orden WHERE evento_id = :e").param("e", evento).query(Integer.class)
                .single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM pago p JOIN orden o ON o.id = p.orden_id WHERE o.evento_id = :e")
                .param("e", evento).query(Integer.class).single()).isZero();

        // Los 2 que quedan sí se pueden comprar
        assertThat(comprar(ana, evento, tipo, 2, null).estado()).isEqualTo(201);
        assertThat(comprar(ana, evento, tipo, 1, null).codigo()).isEqualTo("SIN_CUPO");
    }

    @Test
    @DisplayName("CP-MULTI-07 · doble envío con la misma Idempotency-Key: misma orden, sin cobrar ni emitir dos veces")
    void idempotencia() throws Exception {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Idempotente", true, "80.00", 20, null);
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("idem");
        String clave = claveNueva();

        Respuesta primera = comprar(ana, evento, tipo, 3, null, clave, "TARJETA", "tok_ok");
        Respuesta segunda = comprar(ana, evento, tipo, 3, null, clave, "TARJETA", "tok_ok");

        assertThat(primera.estado()).isEqualTo(201);
        assertThat(segunda.estado()).isEqualTo(200);
        assertThat(segunda.cuerpo().get("ordenId").asLong()).isEqualTo(primera.cuerpo().get("ordenId").asLong());
        assertThat(segunda.cuerpo().get("boletos")).hasSize(3);
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(17);
        assertThat(boletosDeEvento(evento, "VALIDO")).isEqualTo(3);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM pago p JOIN orden o ON o.id = p.orden_id WHERE o.evento_id = :e")
                .param("e", evento).query(Integer.class).single()).isEqualTo(1);

        // Doble toque real: dos peticiones simultáneas con la misma clave
        String otraClave = claveNueva();
        List<Respuesta> simultaneas = aLaVez(List.of(
                () -> comprar(ana, evento, tipo, 2, null, otraClave, "TARJETA", "tok_ok"),
                () -> comprar(ana, evento, tipo, 2, null, otraClave, "TARJETA", "tok_ok")));
        assertThat(simultaneas).allSatisfy(r -> assertThat(r.estado()).isIn(200, 201));
        assertThat(simultaneas.get(0).cuerpo().get("ordenId").asLong())
                .isEqualTo(simultaneas.get(1).cuerpo().get("ordenId").asLong());
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(15);
        assertThat(boletosDeEvento(evento, "VALIDO")).isEqualTo(5);

        // La clave es obligatoria
        Respuesta sinClave = post("/api/ordenes", ana.token(), mapa("eventoId", evento, "tipoBoletoId", tipo,
                "cantidad", 1, "asientosIds", List.of(), "metodo", "TARJETA", "tokenPasarela", "tok_ok"));
        assertThat(sinClave.estado()).isEqualTo(400);
    }

    @Test
    @DisplayName("CP-10/CP-MULTI-06 · pago rechazado: 422, sin boletos, inventario y asientos devueltos; el reintento funciona")
    void pagoRechazado() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Rechazo", true, "100.00", 0, seccion("Luneta", 2, 4));
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("rechazo");
        List<Long> asientos = asientosLibres(evento, ana.token(), 2);
        String clave = claveNueva();

        Respuesta r = comprar(ana, evento, tipo, 2, asientos, clave, "TARJETA", "tok_demo-rechazo");

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.codigo()).isEqualTo("PAGO_RECHAZADO");
        assertThat(boletosDeEvento(evento, "VALIDO")).isZero();
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(8);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE evento_id = :e AND estado <> 'DISPONIBLE'")
                .param("e", evento).query(Integer.class).single()).isZero();
        long orden = r.cuerpo().get("ordenId").asLong();
        assertThat(jdbc.sql("SELECT estado::text FROM orden WHERE id = :id").param("id", orden).query(String.class)
                .single()).isEqualTo("CANCELADA");
        assertThat(jdbc.sql("SELECT estado::text FROM pago WHERE orden_id = :id").param("id", orden)
                .query(String.class).single()).isEqualTo("RECHAZADO");

        // Reintento con la misma selección y la misma clave, ahora con un medio que sí pasa
        Respuesta reintento = comprar(ana, evento, tipo, 2, asientos, clave, "TARJETA", "tok_ok");
        assertThat(reintento.estado()).as(reintento.cuerpo().toString()).isEqualTo(201);
        assertThat(reintento.cuerpo().get("boletos")).hasSize(2);
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(6);
    }

    @Test
    @DisplayName("OXXO/SPEI · el pago queda PENDIENTE, reserva los lugares y emite los boletos al confirmarse")
    void pagoPendiente() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Pendiente", true, "90.00", 0, seccion("Luneta", 2, 4));
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("oxxo");
        List<Long> asientos = asientosLibres(evento, ana.token(), 2);

        Respuesta r = comprar(ana, evento, tipo, 2, asientos, claveNueva(), "OXXO", null);

        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(201);
        assertThat(r.cuerpo().get("estadoOrden").asText()).isEqualTo("PENDIENTE");
        assertThat(r.cuerpo().get("estadoPago").asText()).isEqualTo("PENDIENTE");
        assertThat(r.cuerpo().get("referenciaPago").asText()).startsWith("SIM-OXXO-");
        assertThat(r.cuerpo().get("boletos")).isEmpty();
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(6);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE id IN (:ids) AND estado = 'RESERVADO'")
                .param("ids", asientos).query(Integer.class).single()).isEqualTo(2);
        // Otra persona no puede tomar esos asientos mientras están reservados
        Respuesta otra = comprar(cuentaNueva("otra"), evento, tipo, 2, asientos);
        assertThat(otra.estado()).isEqualTo(409);
        assertThat(otra.codigo()).isEqualTo("ASIENTOS_OCUPADOS");
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(6);

        long pago = r.cuerpo().get("pagoId").asLong();
        // Solo el titular (en dev) o un administrador pueden simular la confirmación
        assertThat(post("/api/pagos/" + pago + "/confirmar", cuentaNueva("tercero").token(), null).estado())
                .isEqualTo(403);
        Respuesta confirmado = post("/api/pagos/" + pago + "/confirmar", ana.token(), null);
        assertThat(confirmado.estado()).as(confirmado.cuerpo().toString()).isEqualTo(200);
        assertThat(confirmado.cuerpo().get("estadoOrden").asText()).isEqualTo("PAGADA");
        assertThat(confirmado.cuerpo().get("boletos")).hasSize(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE id IN (:ids) AND estado = 'OCUPADO'")
                .param("ids", asientos).query(Integer.class).single()).isEqualTo(2);
        assertThat(post("/api/pagos/" + pago + "/confirmar", ana.token(), null).estado()).isEqualTo(409);
    }

    @Test
    @DisplayName("CP-MULTI-09 · evento gratuito: una confirmación por persona, sin cobro")
    void eventoGratuito() throws Exception {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Feria", false, null, 30, null);
        Cuenta ana = cuentaNueva("gratis");

        Respuesta r = comprar(ana, evento, null, 1, null, claveNueva(), "GRATIS", null);
        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(201);
        assertThat(r.cuerpo().get("total").asText()).isEqualTo("0.00");
        assertThat(r.cuerpo().get("estadoOrden").asText()).isEqualTo("PAGADA");
        assertThat(r.cuerpo().path("estadoPago").isMissingNode()).as("sin cobro no hay pago").isTrue();
        assertThat(r.cuerpo().get("boletos")).hasSize(1);

        Respuesta segunda = comprar(ana, evento, null, 1, null, claveNueva(), "GRATIS", null);
        assertThat(segunda.estado()).isEqualTo(422);
        assertThat(segunda.codigo()).isEqualTo("YA_CONFIRMADO");
        Respuesta varios = comprar(cuentaNueva("varios"), evento, null, 2, null, claveNueva(), "GRATIS", null);
        assertThat(varios.estado()).isEqualTo(422);
        assertThat(varios.codigo()).isEqualTo("UNA_CONFIRMACION_POR_PERSONA");

        // La misma persona, dos confirmaciones simultáneas con claves distintas: solo una prospera
        Cuenta luis = cuentaNueva("simultaneo");
        List<Respuesta> dos = aLaVez(List.of(
                () -> comprar(luis, evento, null, 1, null, claveNueva(), "GRATIS", null),
                () -> comprar(luis, evento, null, 1, null, claveNueva(), "GRATIS", null)));
        assertThat(dos.stream().filter(x -> x.estado() == 201)).hasSize(1);
        assertThat(dos.stream().filter(x -> x.estado() == 422)).hasSize(1);
        assertThat(boletosDeEvento(evento, "VALIDO")).isEqualTo(2);
    }

    @Test
    @DisplayName("CP-MULTI-11 · el importe se calcula en el servidor con el precio de tipo_boleto")
    void importeDelServidor() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Importe", true, "33.35", 20, null);
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("importe");

        // Un cliente manipulado que envía su propio «total» o «precio» no cambia nada
        Respuesta r = llamar("POST", "/api/ordenes", ana.token(),
                mapa("eventoId", evento, "tipoBoletoId", tipo, "cantidad", 3, "asientosIds", List.of(), "metodo",
                        "TARJETA", "tokenPasarela", "tok_ok", "total", "1.00", "precioUnitario", "0.01"),
                java.util.Map.of("Idempotency-Key", claveNueva()));
        assertThat(r.estado()).isEqualTo(201);
        assertThat(r.cuerpo().get("total").asText()).isEqualTo("100.05");
        assertThat(r.cuerpo().get("moneda").asText()).isEqualTo("MXN");

        // Un tipo de boleto de otro evento se rechaza
        long otroTipo = tipoDe(eventoPublicado(org, "Otro", true, "10.00", 5, null));
        Respuesta ajeno = comprar(ana, evento, otroTipo, 1, null);
        assertThat(ajeno.estado()).isEqualTo(422);
        assertThat(ajeno.codigo()).isEqualTo("TIPO_AJENO");
    }

    @Test
    @DisplayName("CP-11 · concurrencia real: 8 personas compiten por los últimos 3 lugares y nunca hay sobreventa")
    void ultimosLugares() throws Exception {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Último lugar", true, "40.00", 3, null);
        long tipo = tipoDe(evento);
        List<Callable<Respuesta>> tareas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Cuenta c = cuentaNueva("carrera" + i);
            tareas.add(() -> comprar(c, evento, tipo, 1, null));
        }

        List<Respuesta> respuestas = aLaVez(tareas);

        assertThat(respuestas.stream().filter(r -> r.estado() == 201)).hasSize(3);
        assertThat(respuestas.stream().filter(r -> r.estado() == 409 && "SIN_CUPO".equals(r.codigo()))).hasSize(5);
        assertThat(disponiblesDeTipo(tipo)).isZero();
        assertThat(boletosDeEvento(evento, "VALIDO")).isEqualTo(3);
        assertThat(get("/api/eventos/" + evento, null).cuerpo().get("disponibles").asInt()).isZero();
    }

    @Test
    @DisplayName("CP-MULTI-08 · concurrencia real: dos compras compiten por el mismo asiento y solo una lo obtiene")
    void mismoAsiento() throws Exception {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Mismo asiento", true, "75.00", 0, seccion("Luneta", 2, 5));
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("ana");
        Cuenta luis = cuentaNueva("luis");
        List<Long> libres = asientosLibres(evento, ana.token(), 3);
        // Ambos quieren el asiento [0]; cada uno suma otro distinto para probar el «todo o nada»
        List<Long> deAna = List.of(libres.get(0), libres.get(1));
        List<Long> deLuis = List.of(libres.get(0), libres.get(2));

        List<Respuesta> respuestas = aLaVez(List.of(
                () -> comprar(ana, evento, tipo, 2, deAna),
                () -> comprar(luis, evento, tipo, 2, deLuis)));

        List<Respuesta> ganadoras = respuestas.stream().filter(r -> r.estado() == 201).toList();
        List<Respuesta> perdedoras = respuestas.stream().filter(r -> r.estado() == 409).toList();
        assertThat(ganadoras).hasSize(1);
        assertThat(perdedoras).hasSize(1);
        assertThat(perdedoras.get(0).codigo()).isEqualTo("ASIENTOS_OCUPADOS");
        assertThat(boletosDeEvento(evento, "VALIDO")).isEqualTo(2);
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(8);
        // Quien perdió no se quedó con su otro asiento: exactamente 2 ocupados
        assertThat(jdbc.sql("SELECT COUNT(*) FROM asiento WHERE evento_id = :e AND estado <> 'DISPONIBLE'")
                .param("e", evento).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM boleto b JOIN orden o ON o.id = b.orden_id
                 WHERE o.evento_id = :e AND b.asiento_id = :a AND b.estado = 'VALIDO'
                """).param("e", evento).param("a", libres.get(0)).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un borrador o un evento cancelado no admiten compras")
    void eventoNoDisponible() {
        Cuenta org = organizadorNuevo();
        Respuesta borrador = post("/api/eventos", org.token(), mapa("nombre", "Borrador", "lugar", "Sala",
                "fechaHoraInicio", enDias(5), "esDePago", true, "precio", 10, "aforo", 5));
        assertThat(borrador.estado()).isEqualTo(201);
        long id = borrador.cuerpo().get("id").asLong();
        Respuesta r = comprar(cuentaNueva("borrador"), id, tipoDe(id), 1, null);
        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("EVENTO_NO_DISPONIBLE");
    }
}
