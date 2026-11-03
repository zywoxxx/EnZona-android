package com.uv.enzona

import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.RepositorioMock
import com.uv.enzona.ui.screens.auth.RecuperacionContrasena
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * «¿Olvidaste tu contraseña?»: reglas de la pantalla y flujo completo con los
 * datos de prueba (código al correo, contraseña nueva, inicio de sesión).
 */
class RecuperacionContrasenaTest {

    private val repositorio = RepositorioMock

    @Test
    fun `el correo debe estar bien formado antes de pedir el codigo`() {
        assertNull(RecuperacionContrasena.errorDeCorreo("asistente@uv.mx"))
        assertEquals(RecuperacionContrasena.MENSAJE_CORREO, RecuperacionContrasena.errorDeCorreo("asistente"))
        assertEquals(RecuperacionContrasena.MENSAJE_CORREO, RecuperacionContrasena.errorDeCorreo(""))
    }

    @Test
    fun `la contrasena nueva sigue la politica y debe repetirse igual`() {
        assertNull(RecuperacionContrasena.errorDeContrasena("Nueva1234", "Nueva1234"))
        assertEquals(RecuperacionContrasena.MENSAJE_CONTRASENA, RecuperacionContrasena.errorDeContrasena("corta1", "corta1"))
        assertEquals(RecuperacionContrasena.MENSAJE_CONTRASENA, RecuperacionContrasena.errorDeContrasena("sinnumeros", "sinnumeros"))
        assertEquals(RecuperacionContrasena.MENSAJE_CONFIRMACION, RecuperacionContrasena.errorDeContrasena("Nueva1234", "Nueva1235"))
    }

    @Test
    fun `el codigo son exactamente seis digitos`() {
        assertTrue(RecuperacionContrasena.codigoCompleto("482913"))
        assertFalse(RecuperacionContrasena.codigoCompleto("48291"))
        assertFalse(RecuperacionContrasena.codigoCompleto("48291a"))
    }

    @Test
    fun `el fallo muestra el detalle de la API o un texto generico`() {
        assertEquals("El código de verificación expiró", RecuperacionContrasena.mensajeDeFallo(IllegalStateException("El código de verificación expiró")))
        assertTrue(RecuperacionContrasena.mensajeDeFallo(IOException()).contains("No se pudo cambiar"))
    }

    @Test
    fun `con datos de prueba el codigo cambia la contrasena y la anterior deja de servir`() = runBlocking {
        val codigo = repositorio.solicitarRecuperacion("Asistente@uv.mx ")
        assertNotNull("con datos de prueba el código se devuelve para mostrarlo", codigo)

        repositorio.restablecerContrasena("asistente@uv.mx", codigo!!, "Nueva1234")

        assertEquals("asistente@uv.mx", repositorio.iniciarSesion("asistente@uv.mx", "Nueva1234", false).correo)
        assertTrue(MockRepository.autenticar("asistente@uv.mx", "enzona123").isFailure)
        // El código se consume: no sirve dos veces
        try {
            repositorio.restablecerContrasena("asistente@uv.mx", codigo, "Otra12345"); fail("el código ya se usó")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("código"))
        }
        // Se deja como estaba para las demás pruebas
        repositorio.restablecerContrasena("asistente@uv.mx", repositorio.solicitarRecuperacion("asistente@uv.mx")!!, "enzona123")
    }

    @Test
    fun `un correo sin cuenta no recibe codigo y un codigo equivocado no cambia nada`() = runBlocking {
        assertNull(repositorio.solicitarRecuperacion("nadie@uv.mx"))
        repositorio.solicitarRecuperacion("asistente@uv.mx")
        try {
            repositorio.restablecerContrasena("asistente@uv.mx", "000000", "Nueva1234"); fail("código equivocado")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("código"))
        }
        assertTrue(MockRepository.autenticar("asistente@uv.mx", "enzona123").isSuccess)
    }
}
