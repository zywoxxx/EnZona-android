package com.uv.enzona

import com.uv.enzona.data.URL_EMULADOR
import com.uv.enzona.data.URL_USB
import com.uv.enzona.data.normalizarUrlServidor
import com.uv.enzona.data.primeroQueResponde
import com.uv.enzona.ui.components.ContadorDeToques
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cómo encuentra la app el API sin que la persona configure nada, y el gesto oculto de desarrollo. */
class ServidorTest {

    private val compilada = "http://10.20.252.100:8080/"

    @Test
    fun prefiereLaDireccionCompiladaAunqueLasOtrasTambienRespondan() = runBlocking {
        val elegida = primeroQueResponde(listOf(compilada, URL_USB, URL_EMULADOR)) { true }
        assertEquals(compilada, elegida)
    }

    @Test
    fun siLaCompiladaNoRespondeUsaElCableUsb() = runBlocking {
        val elegida = primeroQueResponde(listOf(compilada, URL_USB, URL_EMULADOR)) { it == URL_USB }
        assertEquals(URL_USB, elegida)
    }

    @Test
    fun sinNingunaRespuestaNoEligeNada() = runBlocking {
        assertNull(primeroQueResponde(listOf(compilada, URL_USB, URL_EMULADOR)) { false })
    }

    @Test
    fun unaSondaQueFallaConExcepcionCuentaComoSinRespuesta() = runBlocking {
        val elegida = primeroQueResponde(listOf(compilada, URL_USB)) { url ->
            if (url == compilada) error("sin ruta") else true
        }
        assertEquals(URL_USB, elegida)
    }

    @Test
    fun sondeaTodasALaVezYNoUnaTrasOtra() = runBlocking {
        val inicio = System.currentTimeMillis()
        primeroQueResponde(List(4) { "http://10.0.0.$it:8080/" }) { delay(200); false }
        val duracion = System.currentTimeMillis() - inicio
        assertTrue("tardó $duracion ms: debió ser ~200, no ~800", duracion < 600)
    }

    @Test
    fun elGestoOcultoSeActivaConSieteToquesSeguidos() {
        val contador = ContadorDeToques(toquesNecesarios = 7, pausaMaximaMs = 1500)
        val activaciones = (1..7).map { contador.tocar(it * 100L) }
        assertEquals(listOf(false, false, false, false, false, false, true), activaciones)
    }

    @Test
    fun unaPausaLargaReiniciaLaCuentaDelGesto() {
        val contador = ContadorDeToques(toquesNecesarios = 3, pausaMaximaMs = 1500)
        assertFalse(contador.tocar(0))
        assertFalse(contador.tocar(100))
        assertFalse("tras 5 s la cuenta vuelve a 1", contador.tocar(5_000))
        assertFalse(contador.tocar(5_100))
        assertTrue(contador.tocar(5_200))
    }

    @Test
    fun unaIpSolaUsaHttpYElPuerto8080() {
        assertEquals("http://10.20.252.100:8080/", normalizarUrlServidor("10.20.252.100"))
    }

    @Test
    fun respetaElPuertoYElEsquemaQueSeEscriben() {
        assertEquals("http://192.168.1.50:9090/", normalizarUrlServidor("192.168.1.50:9090"))
        assertEquals("https://api.enzona.mx/", normalizarUrlServidor("https://api.enzona.mx"))
        assertEquals("https://api.enzona.mx:8443/", normalizarUrlServidor("HTTPS://api.enzona.mx:8443"))
    }

    @Test
    fun conservaLaRutaYTerminaSiempreEnBarra() {
        assertEquals("http://10.0.2.2:8080/api/", normalizarUrlServidor("http://10.0.2.2:8080/api"))
        assertEquals("http://10.0.2.2:8080/", normalizarUrlServidor("  http://10.0.2.2:8080/  "))
    }

    @Test
    fun rechazaLoQueNoEsUnaDireccion() {
        assertNull(normalizarUrlServidor(""))
        assertNull(normalizarUrlServidor("   "))
        assertNull(normalizarUrlServidor("ftp://10.0.0.1"))
        assertNull(normalizarUrlServidor("10.0.0.1 :8080"))
        assertNull(normalizarUrlServidor("http://"))
        assertNull(normalizarUrlServidor("http://host:puerto"))
    }
}
