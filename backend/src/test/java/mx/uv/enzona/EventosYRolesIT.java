package mx.uv.enzona;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** Catálogo, ciclo de vida del evento, rol de organizador, métodos de pago, notificaciones y administración. */
class EventosYRolesIT extends PruebaIntegracion {

    private long eventoEn(Cuenta org, String nombre, String ciudad, Double lat, Double lng, int dias) {
        Map<String, Object> cuerpo = mapa("nombre", nombre, "lugar", "Recinto " + nombre, "ciudad", ciudad, "latitud",
                18.851, "longitud", -97.1, "categoria", "Cultural", "fechaHoraInicio", enDias(dias), "esDePago", false,
                "aforo", 50, "publicar", true);
        Respuesta r = post("/api/eventos", org.token(), cuerpo);
        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(201);
        long id = r.cuerpo().get("id").asLong();
        // Las coordenadas finales se fijan en la base: permite el caso «publicado sin coordenadas» de CP-17
        jdbc.sql("UPDATE evento SET latitud = :lat, longitud = :lng WHERE id = :id")
                .param("lat", lat, java.sql.Types.NUMERIC).param("lng", lng, java.sql.Types.NUMERIC).param("id", id)
                .update();
        return id;
    }

    // ==================================================================
    //  Catálogo
    // ==================================================================

    @Test
    @DisplayName("CP-17 · con lat/lng el catálogo se ordena por distancia real; sin coordenadas, al final y sin distancia")
    void ordenPorDistancia() {
        Cuenta org = organizadorNuevo();
        String ciudad = "Ciudad-" + UUID.randomUUID().toString().substring(0, 8);
        // Punto de referencia: centro de Orizaba (18.8510, -97.1000)
        long lejos = eventoEn(org, "Lejos", ciudad, 18.8940, -96.9350, 3);        // Córdoba, ~18 km
        long cerca = eventoEn(org, "Cerca", ciudad, 18.8512, -97.1010, 9);        // a ~100 m
        long sinCoordenadas = eventoEn(org, "Sin coordenadas", ciudad, null, null, 1);
        long medio = eventoEn(org, "Medio", ciudad, 18.8398, -97.0931, 6);        // ~1.4 km

        Respuesta r = get("/api/eventos?ciudad=" + ciudad + "&lat=18.851&lng=-97.1", null);

        assertThat(r.estado()).isEqualTo(200);
        List<Long> orden = new ArrayList<>();
        r.cuerpo().forEach(e -> orden.add(e.get("id").asLong()));
        assertThat(orden).containsExactly(cerca, medio, lejos, sinCoordenadas);
        JsonNode primero = r.cuerpo().get(0);
        assertThat(primero.get("distanciaKm").asDouble()).isBetween(0.05, 0.2);
        assertThat(r.cuerpo().get(1).get("distanciaKm").asDouble()).isBetween(1.2, 1.7);
        assertThat(r.cuerpo().get(2).get("distanciaKm").asDouble()).isBetween(17.0, 19.0);
        JsonNode ultimo = r.cuerpo().get(3);
        assertThat(ultimo.has("distanciaKm")).as("nunca una distancia inventada").isFalse();
        assertThat(ultimo.has("latitud")).isFalse();

        // Con radio solo entran los eventos cuya distancia se puede comprobar
        List<Long> enRadio = new ArrayList<>();
        get("/api/eventos?ciudad=" + ciudad + "&lat=18.851&lng=-97.1&radioKm=5", null).cuerpo()
                .forEach(e -> enRadio.add(e.get("id").asLong()));
        assertThat(enRadio).containsExactly(cerca, medio);

        // Sin consentimiento de ubicación: se filtra por ciudad y se ordena por fecha
        List<Long> porFecha = new ArrayList<>();
        Respuesta sinUbicacion = get("/api/eventos?ciudad=" + ciudad, null);
        sinUbicacion.cuerpo().forEach(e -> porFecha.add(e.get("id").asLong()));
        assertThat(porFecha).containsExactly(sinCoordenadas, lejos, medio, cerca);
        assertThat(sinUbicacion.cuerpo().get(0).has("distanciaKm")).isFalse();

        assertThat(get("/api/eventos?lat=18.851", null).estado()).isEqualTo(400);
    }

    @Test
    @DisplayName("CP-07 · filtros por texto, categoría, precio y fecha")
    void filtros() {
        Cuenta org = organizadorNuevo();
        String marca = "Filtro" + UUID.randomUUID().toString().substring(0, 6);
        long gratis = eventoPublicado(org, marca + " gratuito", false, null, 10, null);
        long pago = eventoPublicado(org, marca + " de pago", true, "45.00", 10, null);

        List<Long> todos = new ArrayList<>();
        get("/api/eventos?q=" + marca, null).cuerpo().forEach(e -> todos.add(e.get("id").asLong()));
        assertThat(todos).containsExactlyInAnyOrder(gratis, pago);

        Respuesta soloGratis = get("/api/eventos?q=" + marca + "&precio=GRATIS", null);
        assertThat(soloGratis.cuerpo()).hasSize(1);
        assertThat(soloGratis.cuerpo().get(0).get("id").asLong()).isEqualTo(gratis);
        assertThat(soloGratis.cuerpo().get(0).get("precioDesde").asText()).isEqualTo("0.00");
        assertThat(get("/api/eventos?q=" + marca + "&precio=DE_PAGO", null).cuerpo().get(0).get("precioDesde").asText())
                .isEqualTo("45.00");

        assertThat(get("/api/eventos?q=" + marca + "&categoria=Música", null).cuerpo()).hasSize(2);
        assertThat(get("/api/eventos?q=" + marca + "&categoria=Deportivo", null).cuerpo()).isEmpty();
        assertThat(get("/api/eventos?q=" + marca + "&hasta=" + java.time.LocalDate.now().plusDays(2), null).cuerpo())
                .isEmpty();
        assertThat(get("/api/eventos?q=" + marca + "&desde=" + java.time.LocalDate.now().plusDays(2) + "&hasta="
                + java.time.LocalDate.now().plusDays(30), null).cuerpo()).hasSize(2);
        assertThat(get("/api/eventos?q=" + marca + "&desde=ayer", null).estado()).isEqualTo(400);

        assertThat(get("/api/categorias", null).cuerpo().toString()).contains("Música");
    }

    // ==================================================================
    //  Ciclo de vida
    // ==================================================================

    @Test
    @DisplayName("CP-08 · publicar exige ubicación y fecha futura; un borrador no se ve en el catálogo ni lo ve otra cuenta")
    void publicacion() {
        Cuenta org = organizadorNuevo();
        Respuesta borrador = post("/api/eventos", org.token(), mapa("nombre", "Sin mapa", "lugar", "Sala",
                "fechaHoraInicio", enDias(8), "esDePago", false, "aforo", 40));
        assertThat(borrador.estado()).isEqualTo(201);
        long id = borrador.cuerpo().get("id").asLong();
        assertThat(borrador.cuerpo().get("estado").asText()).isEqualTo("BORRADOR");
        // Un evento gratuito nace con su tipo «Entrada general» con todo el aforo
        Respuesta tipos = get("/api/eventos/" + id + "/tipos", null);
        assertThat(tipos.cuerpo()).hasSize(1);
        assertThat(tipos.cuerpo().get(0).get("cantidadTotal").asInt()).isEqualTo(40);
        assertThat(tipos.cuerpo().get(0).get("precio").asText()).isEqualTo("0.00");

        assertThat(get("/api/eventos/" + id, null).estado()).isEqualTo(404);
        assertThat(get("/api/eventos/" + id, cuentaNueva("otra").token()).estado()).isEqualTo(404);
        assertThat(get("/api/eventos/" + id, org.token()).estado()).isEqualTo(200);

        Respuesta sinUbicacion = post("/api/eventos/" + id + "/publicar", org.token(), null);
        assertThat(sinUbicacion.estado()).isEqualTo(422);
        assertThat(sinUbicacion.codigo()).isEqualTo("UBICACION_OBLIGATORIA");

        Map<String, Object> conMapa = mapa("nombre", "Con mapa", "lugar", "Sala", "latitud", 18.85, "longitud", -97.1,
                "fechaHoraInicio", enDias(8), "aforo", 40);
        assertThat(put("/api/eventos/" + id, org.token(), conMapa).estado()).isEqualTo(200);
        Respuesta publicado = post("/api/eventos/" + id + "/publicar", org.token(), null);
        assertThat(publicado.estado()).isEqualTo(200);
        assertThat(publicado.cuerpo().get("estado").asText()).isEqualTo("PUBLICADO");
        assertThat(get("/api/eventos/" + id, null).estado()).isEqualTo(200);

        // Latitud sin longitud, fecha de fin anterior al inicio y publicación directa con fecha pasada
        assertThat(post("/api/eventos", org.token(), mapa("nombre", "Media coordenada", "lugar", "Sala", "latitud",
                18.85, "fechaHoraInicio", enDias(3), "aforo", 5)).estado()).isEqualTo(400);
        assertThat(post("/api/eventos", org.token(), mapa("nombre", "Fin antes", "lugar", "Sala", "fechaHoraInicio",
                enDias(3), "fechaHoraFin", enDias(2), "aforo", 5)).estado()).isEqualTo(400);
        assertThat(post("/api/eventos", org.token(), mapa("nombre", "Pasado", "lugar", "Sala", "latitud", 18.85,
                "longitud", -97.1, "fechaHoraInicio", enDias(-1), "aforo", 5, "publicar", true)).estado())
                .isEqualTo(422);
        // Un asistente no crea eventos
        assertThat(post("/api/eventos", cuentaNueva("asistente").token(), conMapa).estado()).isEqualTo(403);
    }

    @Test
    @DisplayName("CP-15 · el aforo no baja de lo ya entregado; cambiar la fecha avisa a quien tiene boleto")
    void edicionConPoliticas() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Editable", true, "20.00", 10, null);
        long tipo = tipoDe(evento);
        Cuenta ana = cuentaNueva("ana");
        assertThat(comprar(ana, evento, tipo, 4, null).estado()).isEqualTo(201);

        Map<String, Object> cambio = mapa("nombre", "Editable", "lugar", "Sala nueva", "latitud", 18.85, "longitud",
                -97.1, "fechaHoraInicio", enDias(10), "aforo", 3);
        Respuesta menos = put("/api/eventos/" + evento, org.token(), cambio);
        assertThat(menos.estado()).isEqualTo(422);
        assertThat(menos.codigo()).isEqualTo("AFORO_MENOR_A_LO_ENTREGADO");
        assertThat(menos.detalle()).contains("ya hay 4 boletos entregados");

        cambio.put("aforo", 6);
        cambio.put("fechaHoraInicio", enDias(15));
        Respuesta r = put("/api/eventos/" + evento, org.token(), cambio);
        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(200);
        assertThat(r.cuerpo().get("aforo").asInt()).isEqualTo(6);
        assertThat(r.cuerpo().get("disponibles").asInt()).isEqualTo(2);
        assertThat(r.cuerpo().get("vendidos").asInt()).isEqualTo(4);
        assertThat(disponiblesDeTipo(tipo)).isEqualTo(2);

        Respuesta avisos = get("/api/usuarios/me/notificaciones", ana.token());
        assertThat(avisos.cuerpo().get(0).get("tipo").asText()).isEqualTo("FECHA_CAMBIADA");
        assertThat(avisos.cuerpo().get(0).get("texto").asText()).contains("cambió de fecha");

        // Con boletos emitidos no se toca el plano ni el precio
        assertThat(put("/api/eventos/" + evento + "/asientos", org.token(),
                mapa("requiereAsiento", true, "secciones", seccion("Luneta", 2, 3))).estado()).isEqualTo(409);
        cambio.put("precio", 99);
        assertThat(put("/api/eventos/" + evento, org.token(), cambio).codigo()).isEqualTo("PRECIO_CON_VENTAS");
        // Otro organizador no edita un evento ajeno
        assertThat(put("/api/eventos/" + evento, organizadorNuevo().token(), cambio).estado()).isEqualTo(403);
    }

    @Test
    @DisplayName("RF-09 · activar y desactivar el plano de asientos ajusta el aforo; el reporte resume ventas y accesos")
    void planoYReporte() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Plano", true, "25.00", 12, null);

        Respuesta conPlano = put("/api/eventos/" + evento + "/asientos", org.token(),
                mapa("requiereAsiento", true, "secciones", seccion("Balcón", 4, 5)));
        assertThat(conPlano.estado()).as(conPlano.cuerpo().toString()).isEqualTo(200);
        assertThat(conPlano.cuerpo().get("requiereAsiento").asBoolean()).isTrue();
        assertThat(conPlano.cuerpo().get("aforo").asInt()).isEqualTo(20);
        assertThat(conPlano.cuerpo().get("asientosTotales").asInt()).isEqualTo(20);
        Respuesta asientos = get("/api/eventos/" + evento + "/asientos", org.token());
        assertThat(asientos.cuerpo()).hasSize(20);
        assertThat(asientos.cuerpo().get(0).get("fila").asText()).isEqualTo("A");
        assertThat(asientos.cuerpo().get(19).get("fila").asText()).isEqualTo("D");
        assertThat(get("/api/eventos/" + evento + "/asientos", null).estado()).isEqualTo(401);

        Cuenta ana = cuentaNueva("ana");
        Respuesta compra = comprar(ana, evento, tipoDe(evento), 2, asientosLibres(evento, ana.token(), 2));
        assertThat(compra.estado()).isEqualTo(201);
        post("/api/validaciones", org.token(),
                mapa("contenidoQr", compra.cuerpo().get("boletos").get(0).get("contenidoQr").asText(), "eventoId", evento));

        Respuesta reporte = get("/api/eventos/" + evento + "/reportes", org.token());
        assertThat(reporte.estado()).isEqualTo(200);
        assertThat(reporte.cuerpo().get("boletosEmitidos").asInt()).isEqualTo(2);
        assertThat(reporte.cuerpo().get("boletosUsados").asInt()).isEqualTo(1);
        assertThat(reporte.cuerpo().get("ingresos").asText()).isEqualTo("50.00");
        assertThat(reporte.cuerpo().get("accesosPermitidos").asInt()).isEqualTo(1);

        // El organizador ve los boletos de su evento, pero no el contenido del QR de sus asistentes
        Respuesta boletos = get("/api/eventos/" + evento + "/boletos", org.token());
        assertThat(boletos.cuerpo()).hasSize(2);
        assertThat(boletos.cuerpo().toString()).doesNotContain("contenidoQr").doesNotContain("\"firma\"");
    }

    // ==================================================================
    //  Rol de organizador (CP-ROL-01..04)
    // ==================================================================

    @Test
    @DisplayName("CP-ROL-01/02/03 · convertirse en organizador y dejar de serlo; los permisos cambian de inmediato")
    void rolOrganizador() {
        Cuenta c = cuentaNueva("rol");
        Map<String, Object> evento = mapa("nombre", "Mi evento", "lugar", "Sala", "fechaHoraInicio", enDias(4),
                "aforo", 5);
        // CP-ROL-01: sin confirmar nada, la cuenta sigue siendo solo asistente
        assertThat(post("/api/eventos", c.token(), evento).estado()).isEqualTo(403);
        assertThat(delete("/api/usuarios/me/roles/organizador", c.token()).estado()).isEqualTo(403);

        // CP-ROL-02: al confirmar se añade la fila en usuario_rol y el MISMO token ya puede crear eventos
        Respuesta alta = post("/api/usuarios/me/roles/organizador", c.token(), null);
        assertThat(alta.estado()).isEqualTo(200);
        assertThat(alta.cuerpo().get("roles").toString()).isEqualTo("[\"ASISTENTE\",\"ORGANIZADOR\"]");
        assertThat(post("/api/eventos", c.token(), evento).estado()).isEqualTo(201);
        assertThat(get("/api/eventos/mios", c.token()).cuerpo()).hasSize(1);

        // CP-ROL-03: deja el rol (su único evento es un borrador sin boletos) y conserva el de asistente
        assertThat(get("/api/usuarios/me/roles/organizador/puede-dejar", c.token()).cuerpo().get("puede").asBoolean())
                .isTrue();
        Respuesta baja = delete("/api/usuarios/me/roles/organizador", c.token());
        assertThat(baja.estado()).isEqualTo(200);
        assertThat(baja.cuerpo().get("roles").toString()).isEqualTo("[\"ASISTENTE\"]");
        assertThat(post("/api/eventos", c.token(), evento).estado()).isEqualTo(403);
        assertThat(get("/api/eventos/mios", c.token()).estado()).isEqualTo(403);
    }

    @Test
    @DisplayName("CP-ROL-04 · no se puede dejar el rol con eventos publicados con boletos vigentes, ni siendo el único rol, ni siendo admin")
    void bloqueosParaDejarElRol() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Con boletos", false, null, 10, null);
        assertThat(comprar(cuentaNueva("ana"), evento, null, 1, null, claveNueva(), "GRATIS", null).estado())
                .isEqualTo(201);

        Respuesta r = delete("/api/usuarios/me/roles/organizador", org.token());
        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("NO_PUEDE_DEJAR_ROL");
        assertThat(r.detalle()).contains("boletos vigentes").contains("Con boletos");
        assertThat(get("/api/usuarios/me", org.token()).cuerpo().get("roles").toString()).contains("ORGANIZADOR");

        // Al cancelar el evento ya puede dejarlo
        assertThat(post("/api/eventos/" + evento + "/cancelacion", org.token(), mapa("aceptaComision", false)).estado())
                .isEqualTo(200);
        assertThat(delete("/api/usuarios/me/roles/organizador", org.token()).estado()).isEqualTo(200);

        // Único rol
        Cuenta solo = organizadorNuevo();
        jdbc.sql("DELETE FROM usuario_rol WHERE usuario_id = :u AND rol_id = (SELECT id FROM rol WHERE nombre = 'ASISTENTE')")
                .param("u", solo.id()).update();
        Respuesta unico = delete("/api/usuarios/me/roles/organizador", solo.token());
        assertThat(unico.estado()).isEqualTo(409);
        assertThat(unico.detalle()).contains("único rol");

        // Administración
        Cuenta admin = adminNuevo();
        post("/api/usuarios/me/roles/organizador", admin.token(), null);
        Respuesta deAdmin = delete("/api/usuarios/me/roles/organizador", admin.token());
        assertThat(deAdmin.estado()).isEqualTo(409);
        assertThat(deAdmin.detalle()).contains("Administración");
    }

    @Test
    @DisplayName("El organizador designa y retira personal de acceso por correo (usuario_rol)")
    void personalDeAcceso() {
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Con personal", false, null, 10, null);
        Cuenta persona = cuentaNueva("porton");

        assertThat(get("/api/validaciones/eventos", persona.token()).estado()).isEqualTo(403);
        Respuesta alta = post("/api/eventos/" + evento + "/personal-acceso", org.token(), mapa("correo", persona.correo()));
        assertThat(alta.estado()).isEqualTo(200);
        assertThat(alta.cuerpo()).hasSize(1);
        assertThat(get("/api/usuarios/me", persona.token()).cuerpo().get("roles").toString()).contains("VALIDADOR");
        Respuesta asignados = get("/api/validaciones/eventos", persona.token());
        assertThat(asignados.cuerpo()).hasSize(1);
        assertThat(asignados.cuerpo().get(0).get("id").asLong()).isEqualTo(evento);

        assertThat(post("/api/eventos/" + evento + "/personal-acceso", org.token(), mapa("correo", persona.correo()))
                .estado()).isEqualTo(409);
        assertThat(post("/api/eventos/" + evento + "/personal-acceso", org.token(), mapa("correo", "nadie@prueba.mx"))
                .estado()).isEqualTo(404);
        assertThat(post("/api/eventos/" + evento + "/personal-acceso", organizadorNuevo().token(),
                mapa("correo", persona.correo())).estado()).isEqualTo(403);

        Respuesta baja = delete("/api/eventos/" + evento + "/personal-acceso/" + persona.id(), org.token());
        assertThat(baja.estado()).isEqualTo(200);
        assertThat(baja.cuerpo()).isEmpty();
        assertThat(get("/api/usuarios/me", persona.token()).cuerpo().get("roles").toString()).doesNotContain("VALIDADOR");
    }

    // ==================================================================
    //  Métodos de pago
    // ==================================================================

    @Test
    @DisplayName("Métodos de pago · solo token, marca, últimos 4 y vencimiento; cualquier PAN o CVV se rechaza con 400")
    void metodosDePago() {
        Cuenta c = cuentaNueva("tarjetas");
        String ruta = "/api/usuarios/me/metodos-pago";

        for (Map<String, Object> malo : List.of(
                mapa("marca", "Visa", "ultimos4", "4242", "vencimiento", "12/29", "tokenPasarela", "tok_abc123", "numero", "4242424242424242"),
                mapa("marca", "Visa", "ultimos4", "4242", "vencimiento", "12/29", "tokenPasarela", "tok_abc123", "cvv", "123"),
                mapa("marca", "Visa", "ultimos4", "4242", "vencimiento", "12/29", "tokenPasarela", "4242 4242 4242 4242"),
                mapa("marca", "4242-4242-4242-4242", "ultimos4", "4242", "vencimiento", "12/29", "tokenPasarela", "tok_abc123"),
                mapa("marca", "Visa", "ultimos4", 4242424242424242L, "vencimiento", "12/29", "tokenPasarela", "tok_abc123"))) {
            Respuesta r = post(ruta, c.token(), malo);
            assertThat(r.estado()).as(malo.toString()).isEqualTo(400);
            assertThat(r.codigo()).isEqualTo("DATOS_DE_TARJETA_NO_PERMITIDOS");
        }
        assertThat(post(ruta, c.token(), mapa("marca", "Visa", "ultimos4", "4242", "vencimiento", "12/29",
                "tokenPasarela", "tok_abc123", "alias", "la buena")).codigo()).isEqualTo("CAMPO_NO_PERMITIDO");
        assertThat(post(ruta, c.token(), mapa("marca", "Visa", "ultimos4", "42", "vencimiento", "12/29",
                "tokenPasarela", "tok_abc123")).estado()).isEqualTo(400);
        assertThat(get(ruta, c.token()).cuerpo()).isEmpty();

        Respuesta visa = post(ruta, c.token(), mapa("marca", "Visa", "ultimos4", "4242", "vencimiento", "12/29",
                "tokenPasarela", "tok_visa_ok"));
        assertThat(visa.estado()).isEqualTo(201);
        assertThat(visa.cuerpo().get("predeterminado").asBoolean()).isTrue();
        assertThat(visa.cuerpo().toString()).doesNotContain("tok_visa_ok");
        Respuesta master = post(ruta, c.token(), mapa("marca", "Mastercard", "ultimos4", "5100", "vencimiento",
                "01/30", "tokenPasarela", "tok_master-rechazo"));
        assertThat(master.cuerpo().get("predeterminado").asBoolean()).isFalse();
        long idVisa = visa.cuerpo().get("id").asLong();
        long idMaster = master.cuerpo().get("id").asLong();

        Respuesta cambio = post(ruta + "/" + idMaster + "/predeterminado", c.token(), null);
        assertThat(cambio.cuerpo().get(0).get("predeterminado").asBoolean()).isFalse();
        assertThat(cambio.cuerpo().get(1).get("predeterminado").asBoolean()).isTrue();

        // La compra con una tarjeta guardada usa su token; la que termina en -rechazo se rechaza
        Cuenta org = organizadorNuevo();
        long evento = eventoPublicado(org, "Con tarjeta", true, "10.00", 5, null);
        Map<String, Object> orden = mapa("eventoId", evento, "tipoBoletoId", tipoDe(evento), "cantidad", 1,
                "asientosIds", List.of(), "metodoPagoId", idMaster, "metodo", "TARJETA");
        assertThat(llamar("POST", "/api/ordenes", c.token(), orden, Map.of("Idempotency-Key", claveNueva())).codigo())
                .isEqualTo("PAGO_RECHAZADO");
        orden.put("metodoPagoId", idVisa);
        Respuesta aprobada = llamar("POST", "/api/ordenes", c.token(), orden, Map.of("Idempotency-Key", claveNueva()));
        assertThat(aprobada.estado()).isEqualTo(201);
        assertThat(jdbc.sql("SELECT metodo_pago_id FROM pago WHERE id = :id")
                .param("id", aprobada.cuerpo().get("pagoId").asLong()).query(Long.class).single()).isEqualTo(idVisa);
        // La tarjeta de otra cuenta no sirve
        orden.put("metodoPagoId", idVisa);
        assertThat(llamar("POST", "/api/ordenes", cuentaNueva("ajena").token(), orden,
                Map.of("Idempotency-Key", claveNueva())).estado()).isEqualTo(404);

        Respuesta trasBorrar = delete(ruta + "/" + idMaster, c.token());
        assertThat(trasBorrar.cuerpo()).hasSize(1);
        assertThat(trasBorrar.cuerpo().get(0).get("predeterminado").asBoolean()).isTrue();
        assertThat(delete(ruta + "/" + idVisa, cuentaNueva("ajena2").token()).estado()).isEqualTo(404);
    }

    // ==================================================================
    //  Notificaciones y administración
    // ==================================================================

    @Test
    @DisplayName("CP-NOTIF · compra confirmada, recordatorio único por evento y marcado como leída")
    void notificaciones() {
        Cuenta org = organizadorNuevo();
        Cuenta admin = adminNuevo();
        long evento = eventoPublicado(org, "Próximo", true, "15.00", 10, null);
        Cuenta ana = cuentaNueva("ana");
        assertThat(comprar(ana, evento, tipoDe(evento), 2, null).estado()).isEqualTo(201);

        Respuesta avisos = get("/api/usuarios/me/notificaciones", ana.token());
        assertThat(avisos.cuerpo()).hasSize(1);
        JsonNode compra = avisos.cuerpo().get(0);
        assertThat(compra.get("tipo").asText()).isEqualTo("COMPRA_CONFIRMADA");
        assertThat(compra.get("texto").asText()).contains("Compraste 2 boletos").contains("Tus QR están");
        assertThat(compra.get("leida").asBoolean()).isFalse();
        assertThat(compra.get("boletoId").asLong()).isPositive();

        // El evento es en 10 días: aún no toca recordatorio. Al acercarse a 3 días, sí; y solo una vez.
        assertThat(post("/api/admin/recordatorios/generar", ana.token(), null).estado()).isEqualTo(403);
        post("/api/admin/recordatorios/generar", admin.token(), null);
        assertThat(get("/api/usuarios/me/notificaciones", ana.token()).cuerpo()).hasSize(1);
        jdbc.sql("UPDATE evento SET fecha_hora_inicio = now() + interval '3 days 2 hours' WHERE id = :e")
                .param("e", evento).update();
        post("/api/admin/recordatorios/generar", admin.token(), null);
        post("/api/admin/recordatorios/generar", admin.token(), null);
        Respuesta conRecordatorio = get("/api/usuarios/me/notificaciones", ana.token());
        assertThat(conRecordatorio.cuerpo()).hasSize(2);
        JsonNode recordatorio = conRecordatorio.cuerpo().get(0);
        assertThat(recordatorio.get("tipo").asText()).isEqualTo("RECORDATORIO");
        assertThat(recordatorio.get("texto").asText()).contains("es en 3 días").contains("Tienes 2 boletos");

        long id = recordatorio.get("id").asLong();
        assertThat(post("/api/usuarios/me/notificaciones/" + id + "/leida", ana.token(), null).cuerpo().get("leida")
                .asBoolean()).isTrue();
        assertThat(post("/api/usuarios/me/notificaciones/" + id + "/leida", org.token(), null).estado()).isEqualTo(404);
        assertThat(get("/api/usuarios/me/notificaciones?soloNoLeidas=true", ana.token()).cuerpo()).hasSize(1);
        Respuesta todas = post("/api/usuarios/me/notificaciones/leidas", ana.token(), null);
        assertThat(todas.cuerpo().asInt()).isEqualTo(1);
        assertThat(get("/api/usuarios/me/notificaciones?soloNoLeidas=true", ana.token()).cuerpo()).isEmpty();
    }

    @Test
    @DisplayName("RF-18 · administración: usuarios, roles y categorías")
    void administracion() {
        Cuenta admin = adminNuevo();
        Cuenta persona = cuentaNueva("persona");

        Respuesta usuarios = get("/api/admin/usuarios", admin.token());
        assertThat(usuarios.estado()).isEqualTo(200);
        assertThat(usuarios.cuerpo().toString()).contains(persona.correo()).doesNotContain(persona.curp());

        Respuesta roles = put("/api/admin/usuarios/" + persona.id() + "/roles", admin.token(),
                mapa("roles", List.of("ASISTENTE", "ORGANIZADOR")));
        assertThat(roles.estado()).isEqualTo(200);
        assertThat(roles.cuerpo().get("roles").toString()).isEqualTo("[\"ASISTENTE\",\"ORGANIZADOR\"]");
        assertThat(put("/api/admin/usuarios/" + persona.id() + "/roles", admin.token(), mapa("roles", List.of()))
                .estado()).isEqualTo(400);
        assertThat(put("/api/admin/usuarios/" + admin.id() + "/roles", admin.token(),
                mapa("roles", List.of("ASISTENTE"))).estado()).isEqualTo(422);
        assertThat(put("/api/admin/usuarios/" + admin.id() + "/estado", admin.token(), mapa("estado", "BLOQUEADO"))
                .estado()).isEqualTo(422);

        String nombre = "Categoría " + UUID.randomUUID().toString().substring(0, 6);
        assertThat(post("/api/admin/categorias", admin.token(), mapa("nombre", nombre)).estado()).isEqualTo(201);
        assertThat(post("/api/admin/categorias", admin.token(), mapa("nombre", nombre.toUpperCase())).estado())
                .isEqualTo(409);
        assertThat(get("/api/admin/categorias", admin.token()).cuerpo().toString()).contains(nombre);
        assertThat(post("/api/admin/categorias", persona.token(), mapa("nombre", "Otra")).estado()).isEqualTo(403);
    }

    @Test
    @DisplayName("Documentación y salud: OpenAPI publicado y /actuator/health")
    void documentacion() {
        assertThat(get("/actuator/health", null).cuerpo().get("status").asText()).isEqualTo("UP");
        Respuesta openapi = get("/v3/api-docs", null);
        assertThat(openapi.estado()).isEqualTo(200);
        assertThat(openapi.cuerpo().get("paths").has("/api/ordenes")).isTrue();
        assertThat(openapi.cuerpo().get("paths").has("/api/validaciones/lote")).isTrue();
        assertThat(openapi.cuerpo().toString()).contains("SIMULA");
    }
}
