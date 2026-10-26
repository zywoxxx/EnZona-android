package com.uv.enzona

import com.uv.enzona.ui.components.TiemposConexion
import com.uv.enzona.ui.components.fasesConexion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Línea de tiempo de la animación de conexión: qué parte del logo está dibujada en cada instante. */
class AnimacionConexionTest {

    @Test
    fun alEmpezarElCicloNoHayNadaDibujado() {
        val f = fasesConexion(0)
        assertEquals(0f, f.contorno, 0.001f)
        assertEquals(0f, f.disco, 0.001f)
        assertEquals(0f, f.boleto, 0.001f)
        assertEquals(0f, f.texto, 0.001f)
        assertEquals(1f, f.opacidad, 0.001f)
    }

    @Test
    fun elContornoSeTrazaCompletoAntesDeQueAparezcaElBoleto() {
        var anterior = 0f
        for (ms in 0L..TiemposConexion.CONTORNO_FIN step 50) {
            val actual = fasesConexion(ms).contorno
            assertTrue("el contorno nunca retrocede", actual >= anterior)
            anterior = actual
        }
        val alTerminar = fasesConexion(TiemposConexion.CONTORNO_FIN)
        assertEquals(1f, alTerminar.contorno, 0.001f)
        assertEquals(0f, alTerminar.boleto, 0.001f)
    }

    @Test
    fun elBoletoRebotaPorEncimaDeSuTamanoYTerminaExacto() {
        val maximo = (TiemposConexion.BOLETO_INICIO..TiemposConexion.BOLETO_FIN step 10)
            .maxOf { fasesConexion(it).boleto }
        assertTrue("debe pasar de largo (rebote), fue $maximo", maximo > 1.05f && maximo < 1.2f)
        assertEquals(1f, fasesConexion(TiemposConexion.BOLETO_FIN).boleto, 0.001f)
    }

    @Test
    fun elTextoEntraAlFinalYTodoSeDesvaneceAntesDelSiguienteCiclo() {
        assertEquals(1f, fasesConexion(TiemposConexion.TEXTO_FIN).texto, 0.001f)
        assertEquals(1f, fasesConexion(TiemposConexion.SALIDA_INICIO).opacidad, 0.001f)
        val mitad = (TiemposConexion.SALIDA_INICIO + TiemposConexion.CICLO) / 2
        assertEquals(0.5f, fasesConexion(mitad).opacidad, 0.01f)
        assertTrue(fasesConexion(TiemposConexion.CICLO - 1).opacidad < 0.01f)
    }

    @Test
    fun elCicloSeRepite() {
        val primero = fasesConexion(700)
        val segundo = fasesConexion(700 + TiemposConexion.CICLO * 3)
        assertEquals(primero.contorno, segundo.contorno, 0.0001f)
        assertEquals(primero.boleto, segundo.boleto, 0.0001f)
    }
}
