package mx.uv.enzona;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base de las pruebas de integración: la API completa contra un PostgreSQL 18
 * efímero (Testcontainers). Flyway construye el esquema desde V1, así que las
 * migraciones se prueban en cada ejecución.
 *
 * Nunca se usa la base de Aiven: la conexión se fija aquí con la del contenedor
 * y `basePropia` lo comprueba antes de cada prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class PruebaIntegracion {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("enzona_test").withUsername("enzona").withPassword("enzona");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        // Secretos de prueba: no son los de ningún entorno real
        r.add("enzona.jwt.secreto", () -> "secreto-jwt-solo-para-pruebas-0123456789-abcdefghijklmnopqrstuvwxyz-0123456789");
        r.add("enzona.hmac.secreto", () -> "secreto-hmac-solo-para-pruebas-0123456789-abcdefghijklmnopqrstuvwxyz-01234567");
        r.add("enzona.curp.clave", () -> "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        r.add("enzona.otp.devolver-en-respuesta", () -> "true");
        r.add("enzona.pagos.confirmacion-simulada", () -> "true");
    }

    protected static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @LocalServerPort
    protected int puerto;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    private DataSource fuente;

    @BeforeEach
    void basePropia() throws Exception {
        try (var c = fuente.getConnection()) {
            assertThat(c.getMetaData().getURL()).as("las pruebas solo corren contra el contenedor")
                    .isEqualTo(POSTGRES.getJdbcUrl()).doesNotContain("aivencloud");
        }
    }

    // ==================================================================
    //  Cliente HTTP
    // ==================================================================

    /** Respuesta de la API: estado HTTP y cuerpo JSON (o nodo vacío). */
    public record Respuesta(int estado, JsonNode cuerpo) {

        public String codigo() {
            return cuerpo.path("codigo").asText();
        }

        public String detalle() {
            return cuerpo.path("detail").asText();
        }
    }

    protected Respuesta llamar(String metodo, String ruta, String token, Object cuerpo, Map<String, String> cabeceras) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                    .timeout(Duration.ofSeconds(60)).header("Accept", "application/json");
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            if (cabeceras != null) {
                cabeceras.forEach(b::header);
            }
            if (cuerpo != null) {
                b.header("Content-Type", "application/json");
                b.method(metodo, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo), StandardCharsets.UTF_8));
            } else {
                b.method(metodo, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String texto = r.body();
            return new Respuesta(r.statusCode(),
                    texto == null || texto.isBlank() ? JSON.createObjectNode() : JSON.readTree(texto));
        } catch (Exception e) {
            throw new IllegalStateException(metodo + " " + ruta, e);
        }
    }

    protected Respuesta get(String ruta, String token) {
        return llamar("GET", ruta, token, null, null);
    }

    protected Respuesta post(String ruta, String token, Object cuerpo) {
        return llamar("POST", ruta, token, cuerpo, null);
    }

    protected Respuesta put(String ruta, String token, Object cuerpo) {
        return llamar("PUT", ruta, token, cuerpo, null);
    }

    protected Respuesta delete(String ruta, String token) {
        return llamar("DELETE", ruta, token, null, null);
    }

    protected static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], pares[i + 1]);
        }
        return m;
    }

    // ==================================================================
    //  Datos de prueba
    // ==================================================================

    /** Cuenta registrada, verificada y con sesión iniciada. */
    public record Cuenta(long id, String correo, String curp, String contrasena, String token) {
    }

    protected static final String CONTRASENA = "Prueba1234";

    /** CURP con formato válido y única por prueba. */
    protected static String curpNueva() {
        int n = SECUENCIA.getAndIncrement();
        char a = (char) ('A' + (n % 26));
        char b = (char) ('A' + ((n / 26) % 26));
        char c = (char) ('A' + ((n / 676) % 26));
        return "PRU" + a + "900101HVZ" + b + c + "X" + "0" + (n % 10);
    }

    protected Cuenta cuentaNueva(String prefijo) {
        String correo = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@prueba.mx";
        String curp = curpNueva();
        Respuesta registro = post("/api/auth/registro", null, mapa("nombre", "Persona " + prefijo, "correo", correo,
                "telefono", "", "curp", curp, "contrasena", CONTRASENA));
        assertThat(registro.estado()).as(registro.cuerpo().toString()).isEqualTo(201);
        long id = registro.cuerpo().get("usuarioId").asLong();
        Respuesta otp = post("/api/auth/otp/verificar", null,
                mapa("usuarioId", id, "codigo", registro.cuerpo().get("otpSimulado").asText()));
        assertThat(otp.estado()).isEqualTo(200);
        return new Cuenta(id, correo, curp, CONTRASENA, sesion(correo, CONTRASENA));
    }

    protected String sesion(String identificador, String contrasena) {
        Respuesta r = post("/api/auth/login", null, mapa("identificador", identificador, "contrasena", contrasena));
        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(200);
        return r.cuerpo().get("token").asText();
    }

    protected Cuenta organizadorNuevo() {
        Cuenta c = cuentaNueva("organizador");
        assertThat(post("/api/usuarios/me/roles/organizador", c.token(), null).estado()).isEqualTo(200);
        return c;
    }

    /** El rol ADMIN no se obtiene por la API pública: se concede directamente en la base. */
    protected Cuenta adminNuevo() {
        Cuenta c = cuentaNueva("admin");
        jdbc.sql("INSERT INTO usuario_rol (usuario_id, rol_id) SELECT :u, id FROM rol WHERE nombre = 'ADMIN'")
                .param("u", c.id()).update();
        return c;
    }

    protected static String enDias(int dias) {
        return OffsetDateTime.now(ZoneOffset.ofHours(-6)).plusDays(dias).withNano(0).toString();
    }

    /** Evento publicado en Orizaba. `secciones` vacío = sin plano de asientos. */
    protected long eventoPublicado(Cuenta organizador, String nombre, boolean esDePago, String precio, int aforo,
            List<Map<String, Object>> secciones) {
        Map<String, Object> cuerpo = mapa("nombre", nombre + " " + UUID.randomUUID().toString().substring(0, 6),
                "descripcion", "Evento de prueba", "lugar", "Sala de pruebas", "direccion", "Centro", "ciudad", "Orizaba",
                "latitud", 18.851, "longitud", -97.1, "categoria", "Música", "fechaHoraInicio", enDias(10),
                "esDePago", esDePago, "aforo", aforo, "publicar", true);
        if (esDePago) {
            cuerpo.put("precio", new BigDecimal(precio));
        }
        if (secciones != null && !secciones.isEmpty()) {
            cuerpo.put("requiereAsiento", true);
            cuerpo.put("secciones", secciones);
        }
        Respuesta r = post("/api/eventos", organizador.token(), cuerpo);
        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(201);
        assertThat(r.cuerpo().get("estado").asText()).isEqualTo("PUBLICADO");
        return r.cuerpo().get("id").asLong();
    }

    protected static List<Map<String, Object>> seccion(String nombre, int filas, int porFila) {
        return List.of(mapa("nombre", nombre, "filas", filas, "asientosPorFila", porFila));
    }

    protected long tipoDe(long eventoId) {
        return get("/api/eventos/" + eventoId + "/tipos", null).cuerpo().get(0).get("id").asLong();
    }

    protected List<Long> asientosLibres(long eventoId, String token, int cuantos) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode a : get("/api/eventos/" + eventoId + "/asientos", token).cuerpo()) {
            if ("DISPONIBLE".equals(a.get("estado").asText()) && ids.size() < cuantos) {
                ids.add(a.get("id").asLong());
            }
        }
        assertThat(ids).hasSize(cuantos);
        return ids;
    }

    protected static String claveNueva() {
        return UUID.randomUUID().toString();
    }

    /** Compra con tarjeta aprobada (token de un solo uso de la pasarela simulada). */
    protected Respuesta comprar(Cuenta c, long eventoId, Long tipoId, int cantidad, List<Long> asientos, String clave,
            String metodo, String token) {
        return llamar("POST", "/api/ordenes", c.token(),
                mapa("eventoId", eventoId, "tipoBoletoId", tipoId, "cantidad", cantidad,
                        "asientosIds", asientos == null ? List.of() : asientos, "metodoPagoId", null, "metodo", metodo,
                        "tokenPasarela", token),
                Map.of("Idempotency-Key", clave));
    }

    protected Respuesta comprar(Cuenta c, long eventoId, long tipoId, int cantidad, List<Long> asientos) {
        return comprar(c, eventoId, tipoId, cantidad, asientos, claveNueva(), "TARJETA", "tok_prueba_ok");
    }

    protected int disponiblesDeTipo(long tipoId) {
        return jdbc.sql("SELECT cantidad_disponible FROM tipo_boleto WHERE id = :id").param("id", tipoId)
                .query(Integer.class).single();
    }

    protected int boletosDeEvento(long eventoId, String estado) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM boleto b JOIN orden o ON o.id = b.orden_id
                 WHERE o.evento_id = :e AND b.estado = CAST(:estado AS estado_boleto)
                """).param("e", eventoId).param("estado", estado).query(Integer.class).single();
    }

    /** Designa a una cuenta como personal de acceso del evento y devuelve una sesión suya ya con el rol. */
    protected Cuenta validadorDe(long eventoId, Cuenta organizador) {
        Cuenta v = cuentaNueva("validador");
        Respuesta r = post("/api/eventos/" + eventoId + "/personal-acceso", organizador.token(),
                mapa("correo", v.correo()));
        assertThat(r.estado()).as(r.cuerpo().toString()).isEqualTo(200);
        return v;
    }
}
