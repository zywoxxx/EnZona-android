package com.uv.enzona

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.uv.enzona.data.CanalVerificacion
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.PagoElegido
import com.uv.enzona.data.Ubicacion
import com.uv.enzona.data.model.ErrorCompra
import com.uv.enzona.data.model.EstadoAsiento
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.EstadoPago
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoCancelacion
import com.uv.enzona.data.model.TipoMetodoPago
import com.uv.enzona.data.remote.AlmacenEnMemoria
import com.uv.enzona.data.remote.RepositorioRemoto
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * `RepositorioRemoto` contra un servidor falso (MockWebServer): rutas y cuerpos
 * del contrato de `EnZona_Api`, mapeo DTO ↔ dominio, interceptor JWT y manejo de
 * errores (CP-REMOTO-01..13). Las respuestas son las que documenta la API en
 * `docs/EJEMPLOS.md` y `docs/CODIGOS_DE_ERROR.md`.
 */
class RepositorioRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenEnMemoria
    private lateinit var repositorio: RepositorioRemoto
    private val gson = Gson()

    @Before
    fun preparar() {
        servidor = MockWebServer()
        servidor.start()
        almacen = AlmacenEnMemoria()
        repositorio = RepositorioRemoto(servidor.url("/").toString(), almacen)
    }

    @After
    fun cerrar() {
        runCatching { servidor.shutdown() }
    }

    private fun respuesta(cuerpo: String, estado: Int = 200) = MockResponse().setResponseCode(estado).setBody(cuerpo)
        .setHeader("Content-Type", if (estado >= 400) "application/problem+json" else "application/json")

    private fun responder(cuerpo: String, estado: Int = 200) = servidor.enqueue(respuesta(cuerpo, estado))

    private fun problema(estado: Int, codigo: String, detalle: String, extra: String = "") =
        """{"type":"https://enzona.mx/errores/x","title":"Error","status":$estado,"detail":"$detalle","codigo":"$codigo","timestamp":"2026-10-04T12:00:00.000-06:00"$extra}"""

    private fun cuerpoDe(peticion: RecordedRequest): JsonObject = gson.fromJson(peticion.body.readUtf8(), JsonObject::class.java)

    private val perfilJson = """{"id":12,"nombre":"Ana Pérez","correo":"ana@correo.com","telefono":null,"correoVerificado":true,
        "telefonoVerificado":false,"estado":"ACTIVO","roles":["ASISTENTE","ORGANIZADOR"],"fechaRegistro":"2026-10-04T12:00:00-06:00"}"""

    private val sesionJson = """{"token":"jwt.de.prueba","tipo":"Bearer","expiraEnSegundos":3600,"expiraEn":"2026-10-04T13:00:00-06:00",
        "usuario":{"id":12,"nombre":"Ana Pérez","correo":"ana@correo.com","roles":["ASISTENTE","ORGANIZADOR"]}}"""

    /** La sesión son dos peticiones: `POST /api/auth/sesion` y el perfil completo con `GET /api/usuarios/me`. */
    private fun iniciarSesion() = runBlocking {
        responder(sesionJson)
        responder(perfilJson)
        repositorio.iniciarSesion("ana@correo.com", "Secreta123", accesoPersonal = false).also {
            servidor.takeRequest()
            servidor.takeRequest()
        }
    }

    private val eventoJson = """{"id":7,"organizadorId":3,"nombre":"Noche de jazz en el Foro","descripcion":"Cuarteto local","lugar":"Foro Cultural Orizaba",
        "direccion":"Sur 5 120, Centro","ciudad":"Orizaba","latitud":18.8506,"longitud":-97.1003,"categoriaId":1,"categoria":"Música",
        "fechaHoraInicio":"2026-11-20T20:00:00-06:00","fechaHoraFin":"2026-11-20T23:00:00-06:00","esDePago":true,"requiereAsiento":true,
        "aforo":50,"disponibles":48,"precioDesde":"180.00","estado":"PUBLICADO","distanciaKm":0.14,"fechaCancelacion":null,"motivoCancelacion":null,
        "tiposBoleto":[{"id":15,"nombre":"Platea","precio":"180.00","cantidadTotal":30,"disponibles":28},
                       {"id":16,"nombre":"Preferente","precio":"250.00","cantidadTotal":20,"disponibles":20}]}"""

    private fun boletoJson(id: Long, codigo: String, asientoId: Long, numero: Int, ordenId: Long = 31, eventoId: Long = 7) =
        """{"id":$id,"ordenId":$ordenId,"eventoId":$eventoId,"tipoBoletoId":15,"asientoId":$asientoId,"codigo":"$codigo","firma":"Yp3c1M8k2Q",
        "contenidoQr":"$codigo.Yp3c1M8k2Q","estado":"VALIDO","fechaEmision":"2026-10-04T12:05:10-06:00","fechaUso":null,
        "eventoNombre":"Noche de jazz en el Foro","fechaHoraInicio":"2026-11-20T20:00:00-06:00","lugar":"Foro Cultural Orizaba",
        "tipoNombre":"Platea","asientoEtiqueta":"Platea · Fila A · Asiento $numero"}"""

    // ------------------------------------------------------------ sesión e interceptor

    @Test
    fun cpRemoto01_sesion_guardaElToken_leeElPerfil_yElInterceptorEnviaElToken() = runBlocking {
        responder(sesionJson)
        responder(perfilJson)
        val usuario = repositorio.iniciarSesion(" ana@correo.com ", "Secreta123", accesoPersonal = true)

        val sesion = servidor.takeRequest()
        assertEquals("/api/auth/sesion", sesion.path)
        assertNull("la sesión no lleva token", sesion.getHeader("Authorization"))
        val enviado = cuerpoDe(sesion)
        assertEquals("ana@correo.com", enviado["identificador"].asString)
        assertEquals("Secreta123", enviado["contrasena"].asString)
        assertFalse("el acceso de personal es un dato local, no viaja", enviado.has("accesoPersonal"))

        val perfil = servidor.takeRequest()
        assertEquals("/api/usuarios/me", perfil.path)
        assertEquals("Bearer jwt.de.prueba", perfil.getHeader("Authorization"))

        assertEquals("jwt.de.prueba", almacen.token)
        assertTrue(repositorio.sesionDePersonal())
        assertEquals(12L, usuario.id)
        assertEquals(setOf(Rol.ASISTENTE, Rol.ORGANIZADOR), usuario.roles)
        assertTrue(usuario.correoVerificado)
        assertEquals("la CURP nunca sale del servidor", "", usuario.curp)
        assertEquals("el hash nunca sale del servidor", "", usuario.contrasenaHash)

        responder("[]")
        repositorio.misBoletos()
        assertEquals("Bearer jwt.de.prueba", servidor.takeRequest().getHeader("Authorization"))

        repositorio.cerrarSesion()
        assertNull(almacen.token)
        assertFalse(repositorio.sesionDePersonal())
        responder("[]")
        repositorio.eventosPublicados("Orizaba", null)
        assertNull("sin sesión no se envía token", servidor.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun cpRemoto02_restaurarSesion_usaElTokenGuardado_yLoDescartaSiExpiro() = runBlocking {
        assertNull("sin token no hay llamada", repositorio.restaurarSesion())
        assertEquals(0, servidor.requestCount)

        almacen.token = "jwt.guardado"
        responder(perfilJson)
        val usuario = repositorio.restaurarSesion()
        assertEquals("Ana Pérez", usuario?.nombre)
        val peticion = servidor.takeRequest()
        assertEquals("/api/usuarios/me", peticion.path)
        assertEquals("Bearer jwt.guardado", peticion.getHeader("Authorization"))

        responder(problema(401, "NO_AUTENTICADO", "Se requiere iniciar sesión"), 401)
        assertNull(repositorio.restaurarSesion())
        assertNull("un token expirado se borra", almacen.token)
    }

    @Test
    fun cpRemoto03_errores401y403_sesionVencida_credenciales_yCuentaPendiente() = runBlocking {
        iniciarSesion()
        responder(problema(401, "NO_AUTENTICADO", "Se requiere iniciar sesión"), 401)
        try {
            repositorio.misBoletos()
            fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals(401, e.estado)
            assertTrue(e.sesionVencida)
        }
        assertNull(almacen.token)
        servidor.takeRequest()

        // Una contraseña incorrecta también es 401, pero no es una sesión vencida
        responder(problema(401, "CREDENCIALES_INVALIDAS", "Credenciales inválidas"), 401)
        try {
            repositorio.iniciarSesion("ana@correo.com", "mala", false)
            fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertFalse(e.sesionVencida)
            assertEquals("Credenciales inválidas", e.message)
        }
        servidor.takeRequest()

        // Cuenta sin verificar: la app ofrece ir al código; para eso guarda el correo con el que se entró
        responder(problema(403, "CUENTA_PENDIENTE", "La cuenta aún no está verificada"), 403)
        try {
            repositorio.iniciarSesion("Ana@Correo.com", "Secreta123", false)
            fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals("CUENTA_NO_VERIFICADA", e.codigo)
            assertEquals(0L, (e.propiedades["usuarioId"] as Number).toLong())
        }
        servidor.takeRequest()
        responder("""{"estado":"ACTIVO","correoVerificado":true,"telefonoVerificado":false,"verificacionesPendientes":[]}""")
        assertTrue(repositorio.verificarOtp(0L, "482913").completa)
        val verificacion = cuerpoDe(servidor.takeRequest())
        assertEquals("ana@correo.com", verificacion["correo"].asString)
        assertEquals("CORREO", verificacion["canal"].asString)

        // Con la CURP no hay correo que verificar: se informa sin ofrecer la pantalla del código
        responder(problema(403, "CUENTA_PENDIENTE", "La cuenta aún no está verificada"), 403)
        try {
            repositorio.iniciarSesion("PERA000229MDFRRSA1", "Secreta123", false)
            fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals("CUENTA_PENDIENTE", e.codigo)
            assertFalse(e.propiedades.containsKey("usuarioId"))
        }
    }

    @Test
    fun cpRemoto04_sinConexion_seInformaConUnErrorPropio() = runBlocking {
        servidor.shutdown()
        try {
            repositorio.eventosPublicados("Orizaba", null)
            fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertTrue(e.sinConexion)
            assertEquals("SIN_CONEXION", e.codigo)
        }
    }

    // ------------------------------------------------------------ registro y verificación

    @Test
    fun cpRemoto05_registro_enviaElAvisoDePrivacidad_yLaVerificacionPideLosDosCanales() = runBlocking {
        responder("""{"usuarioId":12,"correo":"ana@correo.com","estado":"PENDIENTE","verificacionesPendientes":["CORREO","TELEFONO"]}""", 201)
        val pendiente = repositorio.registrar("Ana Pérez", " Ana@Correo.com ", "272 123 4567", "pera000229mdfrrsa1", "Secreta123", aceptaAvisoPrivacidad = true)

        val registro = servidor.takeRequest()
        assertEquals("/api/auth/registro", registro.path)
        val enviado = cuerpoDe(registro)
        assertEquals("ana@correo.com", enviado["correo"].asString)
        assertEquals("solo dígitos, como exige la API", "2721234567", enviado["telefono"].asString)
        assertEquals("PERA000229MDFRRSA1", enviado["curp"].asString)
        assertTrue(enviado["aceptaAvisoPrivacidad"].asBoolean)
        assertFalse(enviado["organizador"].asBoolean)
        assertEquals(12L, pendiente.usuarioId)
        assertNull("la API nunca devuelve el código", pendiente.otpSimulado)
        assertEquals(listOf(CanalVerificacion.CORREO, CanalVerificacion.TELEFONO), pendiente.canalesPendientes)

        // Primer código: el del correo. La cuenta sigue pendiente del teléfono
        responder("""{"estado":"PENDIENTE","correoVerificado":true,"telefonoVerificado":false,"verificacionesPendientes":["TELEFONO"]}""")
        val primero = repositorio.verificarOtp(12, " 482913 ")
        val v1 = cuerpoDe(servidor.takeRequest())
        assertEquals("ana@correo.com", v1["correo"].asString)
        assertEquals("CORREO", v1["canal"].asString)
        assertEquals("482913", v1["codigo"].asString)
        assertFalse(primero.completa)
        assertEquals(CanalVerificacion.TELEFONO, primero.siguienteCanal)

        // Reenvío: va por el canal pendiente y responde 202 sin cuerpo
        servidor.enqueue(MockResponse().setResponseCode(202))
        assertNull(repositorio.reenviarOtp(12))
        val reenvio = servidor.takeRequest()
        assertEquals("/api/auth/verificacion/reenvio", reenvio.path)
        assertEquals("TELEFONO", cuerpoDe(reenvio)["canal"].asString)

        // Código incorrecto: el detalle de la API es el mensaje para la persona
        responder(problema(400, "CODIGO_VERIFICACION_INVALIDO", "El código de verificación no es válido"), 400)
        try {
            repositorio.verificarOtp(12, "000000"); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals("CODIGO_VERIFICACION_INVALIDO", e.codigo)
            assertEquals("El código de verificación no es válido", e.message)
        }
        servidor.takeRequest()

        // Segundo código: el del teléfono. La cuenta queda activa
        responder("""{"estado":"ACTIVO","correoVerificado":true,"telefonoVerificado":true,"verificacionesPendientes":[]}""")
        val segundo = repositorio.verificarOtp(12, "118822")
        assertEquals("TELEFONO", cuerpoDe(servidor.takeRequest())["canal"].asString)
        assertTrue(segundo.completa)

        // Sin teléfono no se envía el campo, y los 400 de validación muestran el campo y su mensaje
        responder(problema(400, "VALIDACION", "Los datos enviados no son válidos", ""","errores":[{"campo":"curp","mensaje":"El CURP no tiene un formato válido"}]"""), 400)
        try {
            repositorio.registrar("Ana", "ana@correo.com", "", "MALA", "Secreta123", true); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals("VALIDACION", e.codigo)
            assertTrue(e.message!!.contains("curp: El CURP no tiene un formato válido."))
        }
        assertFalse(cuerpoDe(servidor.takeRequest()).has("telefono"))
    }

    @Test
    fun cpRemoto05b_capacidades_noOfreceVerificacionPorTelefono() {
        // Resend solo envía correo: la app no pide teléfono para no dejar la cuenta PENDIENTE de un SMS
        assertFalse("sin proveedor de SMS la app no pide teléfono", repositorio.capacidades.verificacionPorTelefono)
    }

    // ------------------------------------------------------------ eventos

    @Test
    fun cpRemoto06_eventos_porCercaniaOCiudad_tiposDentroDelDetalle_yAsientos() = runBlocking {
        val sinCoordenadas = """{"id":9,"organizadorId":3,"nombre":"Feria del libro","lugar":"Plaza","ciudad":"Orizaba","fechaHoraInicio":"2026-10-10T15:00:00-06:00",
            "esDePago":false,"requiereAsiento":false,"aforo":50,"disponibles":50,"estado":"PUBLICADO","precioDesde":"0.00"}"""
        responder("[$eventoJson,$sinCoordenadas]")

        val eventos = repositorio.eventosPublicados("Orizaba", Ubicacion(18.851, -97.1))

        val ruta = servidor.takeRequest().path!!
        assertTrue("con ubicación se envían lat, lng y radio", ruta.contains("lat=18.851") && ruta.contains("lng=-97.1") && ruta.contains("radioKm=50"))
        assertTrue("se pide el máximo por página", ruta.contains("size=100"))
        assertFalse("con ubicación no se filtra por ciudad", ruta.contains("ciudad"))

        val concierto = eventos[0]
        // La API expresa las fechas en la hora del centro de México: 20:00 del 20 de noviembre
        assertEquals(LocalDateTime.of(2026, 11, 20, 20, 0), concierto.fecha)
        assertEquals(LocalDateTime.of(2026, 11, 20, 23, 0), concierto.fechaFin)
        assertEquals(0.14, concierto.distanciaKm!!, 0.0001)
        assertEquals(180.0, concierto.precioDesde, 0.0001)
        assertEquals(48, concierto.disponibles)
        assertEquals(2, concierto.vendidos)
        assertTrue("con lugares tomados hay boletos", concierto.tieneBoletos)
        assertTrue(concierto.tieneCoordenadas)
        assertEquals("Música", concierto.categoria)

        val feria = eventos[1]
        assertNull("sin coordenadas no hay distancia inventada", feria.distanciaKm)
        assertFalse(feria.tieneCoordenadas)
        assertFalse(feria.tieneBoletos)
        assertEquals("", feria.descripcion)

        // Sin consentimiento de ubicación se filtra por ciudad
        responder("[]")
        repositorio.eventosPublicados("Orizaba", null)
        assertTrue(servidor.takeRequest().path!!.startsWith("/api/eventos?ciudad=Orizaba"))

        // Los tipos de boleto viajan dentro del detalle del evento
        responder(eventoJson)
        val tipos = repositorio.tiposDe(7)
        assertEquals("/api/eventos/7", servidor.takeRequest().path)
        assertEquals(listOf("Platea", "Preferente"), tipos.map { it.nombre })
        assertEquals(7L, tipos[0].eventoId)
        assertEquals(28, tipos[0].cantidadDisponible)
        assertEquals(250.0, tipos[1].precio, 0.0001)

        responder("""[{"id":101,"seccion":"Platea","fila":"A","numero":"7","estado":"RESERVADO","etiqueta":"Platea · Fila A · Asiento 7"}]""")
        val asiento = repositorio.asientosDe(7).single()
        assertEquals(7, asiento.numero)
        assertEquals(7L, asiento.eventoId)
        assertEquals(EstadoAsiento.RESERVADO, asiento.estado)
        assertFalse(asiento.libre)
    }

    // ------------------------------------------------------------ compra

    @Test
    fun cpRemoto07_compraDeDos_enviaTokenDePasarela_yDevuelveLosBoletosConSuQr() = runBlocking {
        iniciarSesion()
        responder(
            """{"ordenId":31,"eventoId":7,"estadoOrden":"PAGADA","estadoPago":"APROBADO","total":"360.00","moneda":"MXN",
            "referenciaPago":"sbx_ch_4f0c8e2b6a1d9e7f3c21","expiraEn":null,"mensaje":"Compra confirmada",
            "boletos":[${boletoJson(88, "EZq3Vt0bX9kLm2Pz", 101, 1)},${boletoJson(89, "EZ7Hn2sQw4Lc8Rt1", 102, 2)}]}""",
            201,
        )
        responder(eventoJson)   // el detalle del evento completa la orden para la pantalla de resumen

        val compra = repositorio.comprar(
            SolicitudCompra(eventoId = 7, tipoBoletoId = 15, cantidad = 2, asientosIds = listOf(101, 102), claveOperacion = "6b1f7c1e-3a9d-4c55-9a2f-1f0e2d6c9b10"),
            PagoElegido(TipoMetodoPago.TARJETA),
        )

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/ordenes", peticion.path)
        assertEquals("6b1f7c1e-3a9d-4c55-9a2f-1f0e2d6c9b10", peticion.getHeader("Idempotency-Key"))
        assertEquals("Bearer jwt.de.prueba", peticion.getHeader("Authorization"))
        val enviado = cuerpoDe(peticion)
        assertEquals(7L, enviado["eventoId"].asLong)
        assertEquals(15L, enviado["tipoBoletoId"].asLong)
        assertEquals(2, enviado["cantidad"].asInt)
        assertEquals(listOf(101L, 102L), enviado["asientosIds"].asJsonArray.map { it.asLong })
        assertEquals("TARJETA", enviado["metodo"].asString)
        assertEquals("tok_aprobado", enviado["tokenPago"].asString)
        assertFalse("la API rechaza metodoPagoId", enviado.has("metodoPagoId"))
        assertFalse("el precio no viaja desde el teléfono", enviado.has("total") || enviado.has("precio"))
        assertEquals("/api/eventos/7", servidor.takeRequest().path)

        assertEquals(2, compra.cantidad)
        assertEquals(360.0, compra.total, 0.0001)
        assertEquals(EstadoOrden.PAGADA, compra.orden.estado)
        assertEquals(12L, compra.orden.asistenteId)
        assertEquals(EstadoPago.APROBADO, compra.pago?.estado)
        assertEquals("sbx_ch_4f0c8e2b6a1d9e7f3c21", compra.pago?.referenciaPasarela)
        assertEquals(2, compra.boletos.map { it.codigo }.distinct().size)
        val primero = compra.boletos[0]
        assertEquals("EZq3Vt0bX9kLm2Pz.Yp3c1M8k2Q", primero.contenidoQr)
        assertEquals(EstadoBoleto.VALIDO, primero.estado)
        assertEquals("Platea · Fila A · Asiento 1", primero.etiquetaAsiento)
        assertEquals("Platea", primero.nombreTipo)
        assertEquals(180.0, primero.tipoBoleto?.precio ?: 0.0, 0.0001)
        assertEquals("Noche de jazz en el Foro", primero.evento.nombre)
        assertEquals(12L, primero.asistenteId)
    }

    @Test
    fun cpRemoto08_pagoRechazado_esUn201Recuperable_yLaConfirmacionGratuitaNoLlevaToken() = runBlocking {
        iniciarSesion()
        responder(
            """{"ordenId":32,"eventoId":7,"estadoOrden":"CANCELADA","estadoPago":"RECHAZADO","total":"180.00","moneda":"MXN",
            "referenciaPago":"sbx_ch_91aa","expiraEn":null,"mensaje":"El pago no se aprobó; no se realizó ningún cargo","boletos":[]}""",
            201,
        )
        try {
            repositorio.comprar(SolicitudCompra(7, 15, 1, listOf(103), claveOperacion = "compra-rechazada-01"), PagoElegido(TipoMetodoPago.TARJETA, simularRechazo = true))
            fail("debía fallar")
        } catch (e: ErrorCompra.PagoRechazado) {
            assertEquals("El pago no se aprobó; no se realizó ningún cargo", e.message)
        }
        val rechazo = cuerpoDe(servidor.takeRequest())
        assertEquals("TARJETA", rechazo["metodo"].asString)
        assertEquals("tok_rechazado", rechazo["tokenPago"].asString)

        // Evento gratuito: método GRATIS, sin token, sin pago; una clave corta se sustituye por una válida (8 a 64)
        responder("""{"ordenId":40,"eventoId":9,"estadoOrden":"PAGADA","estadoPago":null,"total":"0.00","moneda":"MXN","mensaje":"Asistencia confirmada","boletos":[]}""", 201)
        responder("""{"id":9,"nombre":"Feria del libro","lugar":"Plaza","fechaHoraInicio":"2026-10-10T15:00:00-06:00","esDePago":false,"requiereAsiento":false,"aforo":50,"disponibles":49,"estado":"PUBLICADO","tiposBoleto":[{"id":30,"nombre":"General","precio":"0.00","cantidadTotal":50,"disponibles":49}]}""")
        val gratis = repositorio.comprar(SolicitudCompra(9, null, 1, claveOperacion = "c2"), pago = null)
        val confirmacion = servidor.takeRequest()
        val clave = confirmacion.getHeader("Idempotency-Key")!!
        assertTrue(clave.length in 8..64)
        assertNotEquals("c2", clave)
        val cuerpo = cuerpoDe(confirmacion)
        assertEquals("GRATIS", cuerpo["metodo"].asString)
        assertFalse(cuerpo.has("tokenPago"))
        assertFalse(cuerpo.has("tipoBoletoId"))
        assertNull(gratis.pago)
        assertEquals(0.0, gratis.total, 0.0001)
        assertEquals(EstadoOrden.PAGADA, gratis.orden.estado)
    }

    @Test
    fun cpRemoto09_losCodigosDeLaApi_seTraducenALosErroresDeCompraDeLasPantallas() = runBlocking {
        iniciarSesion()
        val solicitud = SolicitudCompra(7, 15, 3, claveOperacion = "compra-prueba-0003")
        val tarjeta = PagoElegido(TipoMetodoPago.TARJETA)

        responder(problema(409, "AFORO_AGOTADO", "No hay lugares suficientes para esta compra"), 409)
        responder(eventoJson.replace("\"disponibles\":48", "\"disponibles\":1"))   // la app consulta cuántos quedan
        try {
            repositorio.comprar(solicitud, tarjeta); fail("debía fallar")
        } catch (e: ErrorCompra.SinCupo) {
            assertEquals(1, e.disponibles)
            assertTrue(e.message!!.startsWith("No hay lugares suficientes"))
        }
        servidor.takeRequest(); servidor.takeRequest()

        responder(problema(409, "ASIENTO_NO_DISPONIBLE", "Uno o más asientos ya no están disponibles"), 409)
        try {
            repositorio.comprar(solicitud.conAsientos(listOf(101, 102, 103)), tarjeta); fail("debía fallar")
        } catch (e: ErrorCompra.AsientosOcupados) {
            assertEquals("Uno o más asientos ya no están disponibles", e.message)
        }
        servidor.takeRequest()

        responder(problema(409, "CONFIRMACION_DUPLICADA", "Ya confirmaste tu asistencia a este evento"), 409)
        try {
            repositorio.comprar(SolicitudCompra(9, null, 1, claveOperacion = "rsvp-2c8f1d0a"), null); fail("debía fallar")
        } catch (e: ErrorCompra.PoliticaUsuario) {
            assertEquals("Ya confirmaste tu asistencia a este evento", e.message)
        }
        servidor.takeRequest()

        responder(problema(409, "ESTADO_INVALIDO", "El evento no está disponible para compra o confirmación"), 409)
        try {
            repositorio.comprar(solicitud, tarjeta); fail("debía fallar")
        } catch (e: ErrorCompra.EventoNoDisponible) {
            assertTrue(e.message!!.contains("no está disponible"))
        }
        servidor.takeRequest()

        responder(problema(422, "REGLA_NEGOCIO", "Selecciona un asiento por boleto: faltan 2 de 3"), 422)
        try {
            repositorio.comprar(solicitud, tarjeta); fail("debía fallar")
        } catch (e: ErrorCompra.AsientosInvalidos) {
            assertTrue(e.message!!.contains("asiento"))
        }
        servidor.takeRequest()

        // OXXO y SPEI no existen en la API: el error llega con su código para que la pantalla lo muestre
        responder(problema(422, "METODO_PAGO_NO_SOPORTADO", "El pago en OXXO y por SPEI aún no está disponible"), 422)
        try {
            repositorio.comprar(solicitud, PagoElegido(TipoMetodoPago.OXXO)); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals("METODO_PAGO_NO_SOPORTADO", e.codigo)
        }
        assertEquals("OXXO", cuerpoDe(servidor.takeRequest())["metodo"].asString)

        // Fuera de la compra, 409 llega como ErrorEnZona con su código y su detalle
        responder(problema(409, "ROL_EN_USO", "Tienes eventos publicados vigentes; cancélalos o espera a que terminen"), 409)
        try {
            repositorio.dejarDeSerOrganizador(); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals(409, e.estado)
            assertEquals("ROL_EN_USO", e.codigo)
            assertFalse(e.sesionVencida)
        }
        servidor.takeRequest()
        // Un error sin cuerpo no rompe la app
        servidor.enqueue(MockResponse().setResponseCode(500))
        try {
            repositorio.categorias(); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals(500, e.estado)
            assertTrue(e.message!!.contains("servidor"))
        }
    }

    // ------------------------------------------------------------ cancelación y personal de acceso

    @Test
    fun cpRemoto10_politicaSinComision_designarValidador_yFuncionesQueLaApiNoTiene() = runBlocking {
        iniciarSesion()
        responder("""{"permitida":true,"tipo":"DE_PAGO_CON_VENTAS","importeCobrado":"360.00","comision":"0.00","reembolso":"360.00","boletosAfectados":2,"motivoBloqueo":null}""")
        val politica = repositorio.politicaCancelacion(7, administrativa = false)
        assertEquals("/api/eventos/7/politica-cancelacion", servidor.takeRequest().path)
        assertEquals(TipoCancelacion.DE_PAGO_CON_VENTAS, politica.tipo)
        assertFalse("la API reembolsa el 100 % sin comisión", politica.cobraComision)
        assertEquals("360.00", politica.reembolso.toPlainString())

        // Designar devuelve a la persona; la app vuelve a pedir la lista para refrescar la pantalla
        responder("""{"usuarioId":21,"nombre":"Luis Puerta","correo":"luis@correo.com"}""", 201)
        responder("""[{"usuarioId":21,"nombre":"Luis Puerta","correo":"luis@correo.com"}]""")
        val personal = repositorio.designarValidador(7, " Luis@Correo.com ")
        val alta = servidor.takeRequest()
        assertEquals("/api/eventos/7/validadores", alta.path)
        assertEquals("luis@correo.com", cuerpoDe(alta)["correo"].asString)
        assertEquals("/api/eventos/7/validadores", servidor.takeRequest().path)
        assertEquals(listOf(21L), personal.map { it.usuarioId })

        // Lo que la API no implementa queda declarado y no genera peticiones
        val capacidades = repositorio.capacidades
        assertFalse(capacidades.metodosGuardados)
        assertFalse(capacidades.pagosDiferidos)
        assertFalse(capacidades.notificaciones)
        assertFalse(capacidades.rolesPorAdministrador)
        assertFalse(capacidades.marcarRealizado)
        val antes = servidor.requestCount
        assertTrue(repositorio.metodosDePago().isEmpty())
        assertEquals(0, repositorio.notificacionesNoLeidas())
        assertTrue(repositorio.cargosDeOrganizador().isEmpty())
        try {
            repositorio.agregarTarjeta("4242 4242 4242 4242", "12/29", "Ana"); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals("NO_DISPONIBLE", e.codigo)
        }
        assertEquals("ninguna de esas funciones llama al servidor", antes, servidor.requestCount)
    }

    // ------------------------------------------------------------ mis boletos

    @Test
    fun cpRemoto11_misBoletos_completaElEventoYElTotalDeLaOrden_yToleraUnEventoNoVisible() = runBlocking {
        iniciarSesion()
        val boletos = """[${boletoJson(88, "EZq3Vt0bX9kLm2Pz", 101, 1)},${boletoJson(89, "EZ7Hn2sQw4Lc8Rt1", 102, 2)},
            {"id":90,"ordenId":40,"eventoId":9,"tipoBoletoId":30,"asientoId":null,"codigo":"EZfer1a","firma":"abc","contenidoQr":"EZfer1a.abc","estado":"VALIDO",
             "fechaEmision":"2026-10-05T10:00:00-06:00","eventoNombre":"Feria del libro","fechaHoraInicio":"2026-10-10T15:00:00-06:00","lugar":"Plaza","tipoNombre":"General"}]"""
        // Los detalles de los eventos se piden en paralelo: se responde según la ruta
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/boletos" -> respuesta(boletos)
                "/api/eventos/7" -> respuesta(eventoJson)
                "/api/eventos/9" -> respuesta(problema(404, "NO_ENCONTRADO", "El recurso no existe"), 404)
                else -> MockResponse().setResponseCode(500)
            }
        }

        val mios = repositorio.misBoletos()
        assertEquals(3, mios.size)
        val jazz = mios.filter { it.evento.id == 7L }
        assertEquals("Noche de jazz en el Foro", jazz[0].evento.nombre)
        assertEquals("el total de la orden se reconstruye con los boletos hermanos", 360.0, jazz[0].orden.total, 0.0001)
        assertEquals(31L, jazz[0].orden.id)
        assertEquals(EstadoOrden.PAGADA, jazz[0].orden.estado)
        assertEquals("Platea · Fila A · Asiento 2", jazz[1].etiquetaAsiento)
        assertEquals(12L, jazz[0].asistenteId)

        // El evento 9 no se pudo leer: el boleto se muestra con lo que trae él mismo
        val feria = mios.single { it.evento.id == 9L }
        assertEquals("Feria del libro", feria.evento.nombre)
        assertEquals("Plaza", feria.evento.lugar)
        assertEquals(LocalDateTime.of(2026, 10, 10, 15, 0), feria.evento.fecha)
        assertNull(feria.asiento)
        assertEquals("General", feria.nombreTipo)

        assertEquals("EZq3Vt0bX9kLm2Pz.Yp3c1M8k2Q", repositorio.boleto(88).contenidoQr)
    }

    // ------------------------------------------------------------ portada del evento

    @Test
    fun cpRemoto15_laPortadaViajaComoMultipart_yLaUrlRelativaSeConserva() = runBlocking {
        iniciarSesion()
        responder(eventoJson.replace("\"tiposBoleto\"", "\"imagenUrl\":\"/api/imagenes/abc123.jpg\",\"tiposBoleto\""))
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
        val evento = repositorio.subirImagenEvento(7, com.uv.enzona.data.ImagenParaSubir(bytes, "image/jpeg", "file:///cache/portada.jpg"))

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/eventos/7/imagen", peticion.path)
        assertTrue(peticion.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val cuerpo = peticion.body.readUtf8()
        assertTrue("el campo se llama imagen", cuerpo.contains("name=\"imagen\"; filename=\"portada.jpg\""))
        assertTrue(cuerpo.contains("Content-Type: image/jpeg"))
        assertEquals("la URL llega tal cual; la app la completa al mostrarla", "/api/imagenes/abc123.jpg", evento.imagenUrl)

        responder(eventoJson)
        assertNull(repositorio.quitarImagenEvento(7).imagenUrl)
        val borrado = servidor.takeRequest()
        assertEquals("DELETE", borrado.method)
        assertEquals("/api/eventos/7/imagen", borrado.path)
    }

    // ------------------------------------------------------------ administración

    @Test
    fun cpRemoto14_estadisticasAdmin_combinaUsuariosEventosYReportes_sinPedirReportesDeBorradores() = runBlocking {
        iniciarSesion()
        val borrador = """{"id":8,"organizadorId":3,"nombre":"Borrador","lugar":"Plaza","ciudad":"Orizaba","fechaHoraInicio":"2026-12-01T10:00:00-06:00",
            "esDePago":false,"requiereAsiento":false,"aforo":10,"disponibles":10,"estado":"BORRADOR","precioDesde":"0.00"}"""
        val rutas = mutableListOf<String>()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                rutas += request.path!!
                return when {
                    request.path!!.startsWith("/api/admin/usuarios") -> respuesta("""[$perfilJson,{"id":21,"nombre":"Luis Puerta","correo":"luis@correo.com","estado":"BLOQUEADO","roles":["ASISTENTE","VALIDADOR"]}]""")
                    request.path!!.startsWith("/api/admin/eventos") -> respuesta("[$eventoJson,$borrador]")
                    request.path == "/api/eventos/7/reportes" -> respuesta("""{"eventoId":7,"evento":"Noche de jazz en el Foro","estado":"PUBLICADO","aforo":50,"disponibles":48,
                        "boletosEmitidos":2,"accesosValidados":1,"accesosRechazados":1,"boletosCancelados":0,"ingresos":"360.00","reembolsado":"0.00","ocupacion":4.0,"porTipo":[]}""")
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val e = repositorio.estadisticasAdmin()

        assertTrue(rutas.any { it.startsWith("/api/admin/usuarios?size=100") })
        assertTrue(rutas.any { it.startsWith("/api/admin/eventos?size=100") })
        assertTrue(rutas.contains("/api/eventos/7/reportes"))
        assertFalse("un borrador no tiene reporte", rutas.contains("/api/eventos/8/reportes"))
        assertEquals(2, e.usuariosTotal)
        assertEquals(1, e.usuariosBloqueados)
        assertEquals(1, e.organizadores)
        assertEquals(1, e.validadores)
        assertEquals(2, e.eventosTotal)
        assertEquals(1, e.eventosPublicados)
        assertEquals(1, e.eventosBorrador)
        assertEquals(2, e.boletosEmitidos)
        assertEquals(1, e.accesosValidados)
        assertEquals(360.0, e.ingresosTotales, 0.0001)
        assertEquals(listOf("Noche de jazz en el Foro"), e.ingresosPorEvento.map { it.etiqueta })
        assertEquals(50, e.aforoTotal)
        assertEquals(4.0, e.ocupacionGlobal, 0.0001)
        assertEquals(listOf("nov 26", "dic 26"), e.eventosPorMes.map { it.etiqueta })
    }

    // ------------------------------------------------------------ validador

    @Test
    fun cpRemoto12_validarEnLinea_traduceElResultado_yLoAnotaEnLaBitacoraLocal() = runBlocking {
        iniciarSesion()
        responder("""{"resultado":"PERMITIDO","motivo":null,"mensaje":"Acceso permitido","codigo":"EZq3Vt0bX9kLm2Pz","tipoBoleto":"Platea",
            "asiento":"Platea · Fila A · Asiento 1","fechaUso":"2026-11-20T19:42:10-06:00"}""")
        val permitido = repositorio.validarQr(" EZq3Vt0bX9kLm2Pz.Yp3c1M8k2Q ", 7)
        val peticion = servidor.takeRequest()
        assertEquals("/api/validaciones", peticion.path)
        val enviado = cuerpoDe(peticion)
        assertEquals(7L, enviado["eventoId"].asLong)
        assertEquals("EZq3Vt0bX9kLm2Pz.Yp3c1M8k2Q", enviado["qr"].asString)
        assertTrue(permitido.permitido)
        assertEquals("Acceso permitido", permitido.titulo)
        assertTrue(permitido.detalle.contains("Platea"))
        assertEquals("Platea · Fila A · Asiento 1", permitido.asiento)

        responder("""{"resultado":"RECHAZADO","motivo":"YA_UTILIZADO","mensaje":"El boleto ya fue utilizado","codigo":"EZq3Vt0bX9kLm2Pz",
            "tipoBoleto":"Platea","asiento":"Platea · Fila A · Asiento 1","fechaUso":"2026-11-20T19:42:10-06:00"}""")
        val rechazado = repositorio.validarQr("EZq3Vt0bX9kLm2Pz.Yp3c1M8k2Q", 7)
        servidor.takeRequest()
        assertFalse(rechazado.permitido)
        assertEquals("Boleto ya utilizado", rechazado.titulo)
        assertEquals("El boleto ya fue utilizado", rechazado.detalle)

        // La API no expone la bitácora: la app conserva lo validado desde este dispositivo, lo más reciente primero
        val antes = servidor.requestCount
        val bitacora = repositorio.ultimasValidaciones(7)
        assertEquals(antes, servidor.requestCount)
        assertEquals(2, bitacora.size)
        assertEquals("Boleto ya utilizado", bitacora[0].motivo)
        assertEquals("EZq3Vt0bX9kLm2Pz", bitacora[1].codigoBoleto)
        assertTrue(bitacora.all { it.sincronizado && !it.offline })
    }

    @Test
    fun cpRemoto13_modoSinConexion_validaContraElManifiesto_ySincronizaElLote() = runBlocking {
        iniciarSesion()
        val qrValido = "EZq3Vt0bX9kLm2Pz.Yp3c1M8k2Q"
        val qrUsado = "EZ7Hn2sQw4Lc8Rt1.b9Qe0Zx"
        responder(
            """{"eventoId":7,"evento":"Noche de jazz en el Foro","generadoEn":"2026-11-20T18:00:00-06:00","total":2,
            "boletos":[{"codigo":"EZq3Vt0bX9kLm2Pz","firma":"Yp3c1M8k2Q","estado":"VALIDO","tipoBoleto":"Platea","asiento":"Platea · Fila A · Asiento 1"},
                       {"codigo":"EZ7Hn2sQw4Lc8Rt1","firma":"b9Qe0Zx","estado":"USADO","tipoBoleto":"Platea","asiento":"Platea · Fila A · Asiento 2"}]}""",
        )
        assertEquals(1, repositorio.prepararModoSinConexion(7))
        assertEquals("/api/eventos/7/manifiesto", servidor.takeRequest().path)

        // A partir de aquí no hay red: ninguna validación hace peticiones
        val antes = servidor.requestCount
        val primero = repositorio.validarSinConexion(qrValido, 7)
        assertTrue(primero.permitido)
        assertEquals("EZq3Vt0bX9kLm2Pz", primero.codigo)
        assertEquals("Platea · Fila A · Asiento 1", primero.asiento)
        assertFalse("el mismo boleto no entra dos veces en este dispositivo", repositorio.validarSinConexion(qrValido, 7).permitido)
        assertFalse(repositorio.validarSinConexion(qrUsado, 7).permitido)
        val ajeno = repositorio.validarSinConexion("EZq3Vt0bX9kLm2Pz.firma-alterada", 7)
        assertFalse("una firma distinta no está en el manifiesto", ajeno.permitido)
        assertEquals("Boleto no reconocido", ajeno.titulo)
        assertEquals(antes, servidor.requestCount)
        assertEquals(4, repositorio.pendientesDeSincronizar())
        assertEquals(4, repositorio.ultimasValidaciones(7).count { !it.sincronizado })

        try {
            repositorio.validarSinConexion(qrValido, 99); fail("sin manifiesto no se valida")
        } catch (e: ErrorEnZona) {
            assertEquals("SIN_MANIFIESTO", e.codigo)
        }

        // Si la sincronización falla, la cola se conserva
        servidor.enqueue(MockResponse().setResponseCode(503))
        try {
            repositorio.sincronizarValidaciones(); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals(503, e.estado)
        }
        servidor.takeRequest()
        assertEquals(4, repositorio.pendientesDeSincronizar())

        responder(
            """{"procesados":4,"permitidos":1,"rechazados":3,"conflictos":1,"resultados":[
            {"qr":"$qrValido","fechaHora":"2026-11-20T19:00:00-06:00","resultado":"PERMITIDO","motivo":null,"conflicto":false,"duplicado":false},
            {"qr":"$qrValido","fechaHora":"2026-11-20T19:00:01-06:00","resultado":"RECHAZADO","motivo":"YA_UTILIZADO","conflicto":true,"duplicado":false},
            {"qr":"$qrUsado","fechaHora":"2026-11-20T19:00:02-06:00","resultado":"RECHAZADO","motivo":"YA_UTILIZADO","conflicto":false,"duplicado":true},
            {"qr":"EZq3Vt0bX9kLm2Pz.firma-alterada","fechaHora":"2026-11-20T19:00:03-06:00","resultado":"RECHAZADO","motivo":"FIRMA_INVALIDA","conflicto":false,"duplicado":false}]}""",
        )
        val resumen = repositorio.sincronizarValidaciones()
        val lote = servidor.takeRequest()
        assertEquals("/api/validaciones/sincronizar", lote.path)
        val enviado = cuerpoDe(lote)
        assertEquals(7L, enviado["eventoId"].asLong)
        val registros = enviado["registros"].asJsonArray
        assertEquals(4, registros.size())
        assertEquals(qrValido, registros[0].asJsonObject["qr"].asString)
        assertTrue("cada registro lleva la hora del escaneo", registros.all { it.asJsonObject.has("fechaHora") })
        assertEquals(4, resumen.enviadas)
        assertEquals(1, resumen.permitidas)
        assertEquals(3, resumen.rechazadas)
        assertEquals("un conflicto y un duplicado", 2, resumen.duplicadas)
        assertEquals(0, repositorio.pendientesDeSincronizar())

        // Lo sincronizado queda en la bitácora con el veredicto del servidor
        val bitacora = repositorio.ultimasValidaciones(7)
        assertEquals(4, bitacora.size)
        assertTrue(bitacora.all { it.sincronizado && it.offline })
        // Del más reciente al más antiguo; el mismo QR aparece dos veces con veredictos distintos
        assertEquals(listOf("Boleto no válido", "Boleto ya utilizado", "Boleto ya admitido en otro dispositivo", "Acceso permitido"), bitacora.map { it.motivo })
        assertTrue(bitacora[3].resultado == com.uv.enzona.data.model.ResultadoAcceso.PERMITIDO)
    }
}
