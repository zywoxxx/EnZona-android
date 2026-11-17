package com.uv.enzona

import com.uv.enzona.ui.components.Movimiento
import com.uv.enzona.ui.theme.AnchoPantalla
import com.uv.enzona.ui.theme.anchoPantalla
import com.uv.enzona.ui.theme.columnasAdaptables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de pantalla adaptable (cortes de ancho, columnas) y del escalonado de las animaciones. */
class PantallaAdaptableTest {

    @Test
    fun `los cortes de ancho son los de Material 3`() {
        assertEquals(AnchoPantalla.COMPACTA, anchoPantalla(360))      // teléfono
        assertEquals(AnchoPantalla.COMPACTA, anchoPantalla(599))
        assertEquals(AnchoPantalla.MEDIANA, anchoPantalla(600))       // teléfono grande o tableta chica
        assertEquals(AnchoPantalla.MEDIANA, anchoPantalla(839))
        assertEquals(AnchoPantalla.EXPANDIDA, anchoPantalla(840))     // tableta
        assertEquals(AnchoPantalla.EXPANDIDA, anchoPantalla(1280))
    }

    @Test
    fun `solo las pantallas anchas usan barra lateral y varias columnas`() {
        assertFalse(AnchoPantalla.COMPACTA.esAmplia)
        assertTrue(AnchoPantalla.MEDIANA.esAmplia)
        assertTrue(AnchoPantalla.EXPANDIDA.esAmplia)
    }

    @Test
    fun `las columnas de tarjetas crecen con el ancho pero nunca bajan de una`() {
        assertEquals(1, columnasAdaptables(328, 320))      // teléfono: una columna
        assertEquals(2, columnasAdaptables(768, 320))      // tableta en vertical
        assertEquals(3, columnasAdaptables(1248, 320))     // tableta en horizontal
        assertEquals(1, columnasAdaptables(200, 320))      // más angosto que una tarjeta
        assertEquals(1, columnasAdaptables(500, 0))        // sin dividir entre cero
    }

    @Test
    fun `la entrada de las tarjetas se escalona solo para las primeras`() {
        assertEquals(0, Movimiento.retrasoDeEntradaMs(0))
        assertEquals(Movimiento.PASO_ESCALONADO_MS * 3, Movimiento.retrasoDeEntradaMs(3))
        val tope = Movimiento.PASO_ESCALONADO_MS * Movimiento.MAX_ESCALONADOS
        assertEquals(tope, Movimiento.retrasoDeEntradaMs(Movimiento.MAX_ESCALONADOS))
        assertEquals(tope, Movimiento.retrasoDeEntradaMs(50))
        assertEquals(0, Movimiento.retrasoDeEntradaMs(-2))
        assertTrue("nadie espera más de medio segundo para ver su lista", tope <= 500)
    }
}
