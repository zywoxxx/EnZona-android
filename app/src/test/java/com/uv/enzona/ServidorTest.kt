package com.uv.enzona

import com.uv.enzona.data.normalizarUrlServidor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lo que la persona escribe en «Servidor» se convierte en una dirección válida para la API. */
class ServidorTest {

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
