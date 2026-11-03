package com.uv.enzona

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.remote.AlmacenEnMemoria
import com.uv.enzona.data.remote.RepositorioRemoto
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Contrato de «¿Olvidaste tu contraseña?» con EnZona_Api: rutas, cuerpos y errores. */
class RecuperacionRemotaTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: RepositorioRemoto
    private val gson = Gson()

    @Before
    fun preparar() {
        servidor = MockWebServer()
        servidor.start()
        repositorio = RepositorioRemoto(servidor.url("/").toString(), AlmacenEnMemoria())
    }

    @After
    fun cerrar() {
        runCatching { servidor.shutdown() }
    }

    private fun cuerpo(): JsonObject = gson.fromJson(servidor.takeRequest().also { ultimaRuta = it.path }.body.readUtf8(), JsonObject::class.java)
    private var ultimaRuta: String? = null

    @Test
    fun `pedir el codigo manda el correo normalizado y nunca devuelve el codigo`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(202))

        assertNull(repositorio.solicitarRecuperacion(" Ana@Correo.com "))

        val enviado = cuerpo()
        assertEquals("/api/auth/contrasena/recuperacion", ultimaRuta)
        assertEquals("ana@correo.com", enviado["correo"].asString)
        assertFalse(enviado.has("codigo"))
    }

    @Test
    fun `restablecer manda correo, codigo y contrasena nueva con los nombres de la API`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(204))

        repositorio.restablecerContrasena("ana@correo.com", " 482913 ", "Nueva1234")

        val enviado = cuerpo()
        assertEquals("/api/auth/contrasena/restablecimiento", ultimaRuta)
        assertEquals("482913", enviado["codigo"].asString)
        assertEquals("Nueva1234", enviado["nuevaContrasena"].asString)
    }

    @Test
    fun `un codigo expirado llega como error con el codigo y el detalle de la API`() = runBlocking {
        servidor.enqueue(
            MockResponse().setResponseCode(400).setHeader("Content-Type", "application/problem+json").setBody(
                """{"type":"https://enzona.mx/errores/x","title":"Error","status":400,"detail":"El código de verificación expiró; solicita uno nuevo","codigo":"CODIGO_VERIFICACION_EXPIRADO","timestamp":"2026-11-03T12:00:00.000-06:00"}"""
            )
        )
        try {
            repositorio.restablecerContrasena("ana@correo.com", "482913", "Nueva1234"); fail("debía fallar")
        } catch (e: ErrorEnZona) {
            assertEquals("CODIGO_VERIFICACION_EXPIRADO", e.codigo)
            assertEquals("El código de verificación expiró; solicita uno nuevo", e.message)
        }
    }
}
