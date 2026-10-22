package mx.uv.enzona;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Registro, verificación por OTP e inicio de sesión (RF-01 a RF-03, CP-01 a CP-06). */
class AccesoIT extends PruebaIntegracion {

    private static String correoNuevo() {
        return "acceso-" + UUID.randomUUID().toString().substring(0, 8) + "@prueba.mx";
    }

    @Test
    @DisplayName("CP-01/CP-05 · registro, verificación con OTP e inicio de sesión con JWT")
    void registroVerificacionYSesion() {
        String correo = correoNuevo();
        String curp = curpNueva();
        Respuesta registro = post("/api/auth/registro", null, mapa("nombre", "Ana Prueba", "correo", correo,
                "telefono", "2721234567".substring(0, 6) + String.format("%04d", (int) (Math.random() * 9999)),
                "curp", curp, "contrasena", CONTRASENA));
        assertThat(registro.estado()).isEqualTo(201);
        assertThat(registro.cuerpo().get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(registro.cuerpo().get("simulacion").asBoolean()).isTrue();
        long id = registro.cuerpo().get("usuarioId").asLong();
        String otp = registro.cuerpo().get("otpSimulado").asText();
        assertThat(otp).matches("\\d{6}");

        // Antes de verificar no hay sesión
        Respuesta sinVerificar = post("/api/auth/login", null, mapa("identificador", correo, "contrasena", CONTRASENA));
        assertThat(sinVerificar.estado()).isEqualTo(403);
        assertThat(sinVerificar.codigo()).isEqualTo("CUENTA_NO_VERIFICADA");
        assertThat(sinVerificar.cuerpo().get("usuarioId").asLong()).isEqualTo(id);

        assertThat(post("/api/auth/otp/verificar", null, mapa("usuarioId", id, "codigo", otp)).estado()).isEqualTo(200);

        Respuesta sesion = post("/api/auth/login", null, mapa("identificador", correo, "contrasena", CONTRASENA));
        assertThat(sesion.estado()).isEqualTo(200);
        String token = sesion.cuerpo().get("token").asText();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(sesion.cuerpo().get("usuario").get("roles").toString()).isEqualTo("[\"ASISTENTE\"]");

        // También se entra con la CURP
        assertThat(post("/api/auth/login", null, mapa("identificador", curp.toLowerCase(), "contrasena", CONTRASENA))
                .estado()).isEqualTo(200);

        Respuesta yo = get("/api/usuarios/me", token);
        assertThat(yo.estado()).isEqualTo(200);
        assertThat(yo.cuerpo().get("correo").asText()).isEqualTo(correo);
        assertThat(yo.cuerpo().get("estado").asText()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("RNF-01/RNF-03 · la contraseña se guarda con BCrypt y la CURP cifrada; la API nunca la devuelve completa")
    void contrasenaYCurpProtegidas() {
        Cuenta c = cuentaNueva("cifrado");
        var fila = jdbc.sql("SELECT contrasena_hash, curp, curp_hash FROM usuario WHERE id = :id").param("id", c.id())
                .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)}).single();
        assertThat(fila[0]).startsWith("$2").doesNotContain(CONTRASENA);
        assertThat(fila[1]).doesNotContain(c.curp()).isNotEqualTo(c.curp());
        assertThat(fila[2]).hasSize(64);

        Respuesta yo = get("/api/usuarios/me", c.token());
        assertThat(yo.cuerpo().toString()).doesNotContain(c.curp());
        assertThat(yo.cuerpo().get("curpEnmascarada").asText()).startsWith(c.curp().substring(0, 4))
                .endsWith(c.curp().substring(16)).contains("·");
    }

    @Test
    @DisplayName("CP-02 · CURP o correo duplicado → 409")
    void duplicados() {
        Cuenta existente = cuentaNueva("duplicado");
        Respuesta mismaCurp = post("/api/auth/registro", null, mapa("nombre", "Otra Persona", "correo", correoNuevo(),
                "curp", existente.curp(), "contrasena", CONTRASENA));
        assertThat(mismaCurp.estado()).isEqualTo(409);
        assertThat(mismaCurp.codigo()).isEqualTo("CURP_DUPLICADA");

        Respuesta mismoCorreo = post("/api/auth/registro", null, mapa("nombre", "Otra Persona", "correo",
                existente.correo().toUpperCase(), "curp", curpNueva(), "contrasena", CONTRASENA));
        assertThat(mismoCorreo.estado()).isEqualTo(409);
        assertThat(mismoCorreo.codigo()).isEqualTo("CORREO_DUPLICADO");
    }

    @Test
    @DisplayName("CP-03 · CURP con formato inválido o contraseña débil → 400 con el detalle")
    void validaciones() {
        Respuesta curp = post("/api/auth/registro", null, mapa("nombre", "Persona", "correo", correoNuevo(), "curp",
                "ABC123", "contrasena", CONTRASENA));
        assertThat(curp.estado()).isEqualTo(400);
        assertThat(curp.codigo()).isEqualTo("VALIDACION");
        assertThat(curp.cuerpo().get("errores").has("curp")).isTrue();
        assertThat(curp.detalle()).contains("CURP");

        Respuesta debil = post("/api/auth/registro", null, mapa("nombre", "Persona", "correo", correoNuevo(), "curp",
                curpNueva(), "contrasena", "sololetras"));
        assertThat(debil.estado()).isEqualTo(400);
        assertThat(debil.codigo()).isEqualTo("CONTRASENA_DEBIL");
    }

    @Test
    @DisplayName("CP-04 · OTP incorrecto → 422, la cuenta sigue pendiente y el intento queda contado")
    void otpIncorrecto() {
        Respuesta registro = post("/api/auth/registro", null, mapa("nombre", "Persona", "correo", correoNuevo(),
                "curp", curpNueva(), "contrasena", CONTRASENA));
        long id = registro.cuerpo().get("usuarioId").asLong();
        String bueno = registro.cuerpo().get("otpSimulado").asText();
        String malo = bueno.equals("000000") ? "111111" : "000000";

        Respuesta r = post("/api/auth/otp/verificar", null, mapa("usuarioId", id, "codigo", malo));
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.codigo()).isEqualTo("OTP_INCORRECTO");
        assertThat(jdbc.sql("SELECT estado::text FROM usuario WHERE id = :id").param("id", id).query(String.class)
                .single()).isEqualTo("PENDIENTE");
        assertThat(jdbc.sql("SELECT intentos FROM otp_verificacion WHERE usuario_id = :id AND NOT usado")
                .param("id", id).query(Integer.class).single()).isEqualTo(1);

        // Reenviar invalida el anterior
        Respuesta reenvio = post("/api/auth/otp/reenviar", null, mapa("usuarioId", id));
        assertThat(reenvio.estado()).isEqualTo(200);
        String nuevo = reenvio.cuerpo().get("otpSimulado").asText();
        if (!nuevo.equals(bueno)) {
            assertThat(post("/api/auth/otp/verificar", null, mapa("usuarioId", id, "codigo", bueno)).estado())
                    .isEqualTo(422);
        }
        assertThat(post("/api/auth/otp/verificar", null, mapa("usuarioId", id, "codigo", nuevo)).estado())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("CP-06 · contraseña incorrecta → 401 sin revelar si la cuenta existe")
    void contrasenaIncorrecta() {
        Cuenta c = cuentaNueva("login");
        Respuesta mala = post("/api/auth/login", null, mapa("identificador", c.correo(), "contrasena", "Otra12345"));
        Respuesta inexistente = post("/api/auth/login", null,
                mapa("identificador", "nadie-" + UUID.randomUUID() + "@prueba.mx", "contrasena", CONTRASENA));
        assertThat(mala.estado()).isEqualTo(401);
        assertThat(inexistente.estado()).isEqualTo(401);
        assertThat(mala.codigo()).isEqualTo("CREDENCIALES_INVALIDAS");
        assertThat(mala.detalle()).isEqualTo(inexistente.detalle());
    }

    @Test
    @DisplayName("Sin token o con token inválido → 401 en formato ProblemDetail; una cuenta bloqueada pierde el acceso")
    void accesoProtegido() {
        Respuesta sinToken = get("/api/usuarios/me", null);
        assertThat(sinToken.estado()).isEqualTo(401);
        assertThat(sinToken.codigo()).isEqualTo("NO_AUTENTICADO");
        assertThat(get("/api/usuarios/me", "no.es.un-token").estado()).isEqualTo(401);

        Cuenta c = cuentaNueva("bloqueo");
        assertThat(get("/api/usuarios/me", c.token()).estado()).isEqualTo(200);
        Cuenta admin = adminNuevo();
        assertThat(put("/api/admin/usuarios/" + c.id() + "/estado", admin.token(), mapa("estado", "BLOQUEADO"))
                .estado()).isEqualTo(200);
        assertThat(get("/api/usuarios/me", c.token()).estado()).isEqualTo(401);
        Respuesta login = post("/api/auth/login", null, mapa("identificador", c.correo(), "contrasena", CONTRASENA));
        assertThat(login.estado()).isEqualTo(403);
        assertThat(login.codigo()).isEqualTo("CUENTA_BLOQUEADA");

        // Un asistente no entra a administración
        Cuenta otro = cuentaNueva("normal");
        assertThat(get("/api/admin/usuarios", otro.token()).estado()).isEqualTo(403);
    }

    @Test
    @DisplayName("Perfil y contraseña: PUT /me y PUT /me/contrasena")
    void perfilYContrasena() {
        Cuenta c = cuentaNueva("perfil");
        Respuesta perfil = put("/api/usuarios/me", c.token(), mapa("nombre", "Nombre Nuevo", "telefono", ""));
        assertThat(perfil.estado()).isEqualTo(200);
        assertThat(perfil.cuerpo().get("nombre").asText()).isEqualTo("Nombre Nuevo");

        assertThat(put("/api/usuarios/me/contrasena", c.token(), mapa("actual", "Incorrecta1", "nueva", "Nueva12345"))
                .estado()).isEqualTo(422);
        assertThat(put("/api/usuarios/me/contrasena", c.token(), mapa("actual", CONTRASENA, "nueva", "corta"))
                .estado()).isEqualTo(400);
        assertThat(put("/api/usuarios/me/contrasena", c.token(), mapa("actual", CONTRASENA, "nueva", "Nueva12345"))
                .estado()).isEqualTo(204);
        assertThat(post("/api/auth/login", null, mapa("identificador", c.correo(), "contrasena", "Nueva12345"))
                .estado()).isEqualTo(200);
    }
}
