package com.uv.enzona

import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.ui.screens.auth.ReenvioCodigo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Reglas del botón «Reenviar código» de la pantalla OTP: cuenta regresiva de
 * 60 s tras cada emisión y mensaje propio cuando falla el reenvío.
 */
class ReenvioCodigoTest {

    @Test
    fun `el boton muestra la cuenta regresiva mientras quedan segundos`() {
        assertEquals("Reenviar código (60 s)", ReenvioCodigo.textoReenvio(60))
        assertEquals("Reenviar código (45 s)", ReenvioCodigo.textoReenvio(45))
        assertEquals("Reenviar código (1 s)", ReenvioCodigo.textoReenvio(1))
    }

    @Test
    fun `el boton vuelve a su texto normal al llegar a cero o con valores negativos`() {
        assertEquals("Reenviar código", ReenvioCodigo.textoReenvio(0))
        assertEquals("Reenviar código", ReenvioCodigo.textoReenvio(-3))
    }

    @Test
    fun `la espera entre reenvios es la que exige la API`() {
        assertEquals(60, ReenvioCodigo.ESPERA_SEGUNDOS)
    }

    @Test
    fun `los errores de la API conservan su detalle`() {
        val fallo = ErrorEnZona(409, "SIN_VERIFICACION_PENDIENTE", "No hay una verificación pendiente en este dispositivo.")
        assertEquals("No hay una verificación pendiente en este dispositivo.", ReenvioCodigo.mensajeDeFallo(fallo))
    }

    @Test
    fun `un fallo sin mensaje usa el texto generico`() {
        assertEquals("No se pudo reenviar el código. Inténtalo de nuevo.", ReenvioCodigo.mensajeDeFallo(IOException()))
        assertEquals("No se pudo reenviar el código. Inténtalo de nuevo.", ReenvioCodigo.mensajeDeFallo(IOException("  ")))
    }
}
