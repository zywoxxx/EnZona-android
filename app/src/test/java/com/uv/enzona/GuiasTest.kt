package com.uv.enzona

import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import com.uv.enzona.data.Guias
import com.uv.enzona.ui.components.EstadoGuias
import com.uv.enzona.ui.components.PasoGuia
import com.uv.enzona.ui.components.posicionTarjeta
import com.uv.enzona.ui.components.trazarFlecha
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guías interactivas: cuándo se abren, cuándo se recuerdan y dónde se coloca la tarjeta. */
class GuiasTest {

    private val pasos = listOf(PasoGuia("a", "Paso 1", "…"), PasoGuia("b", "Paso 2", "…"))

    @After
    fun limpiar() = Guias.usar(null)

    @Test
    fun sinPreferenciasNingunaGuiaSeAbre() {
        Guias.usar(null)                       // vistas previas y pruebas de componentes
        assertTrue(Guias.vista("inicio"))
        assertFalse(EstadoGuias().pedir("inicio", pasos))
    }

    @Test
    fun unaGuiaSeAbreLaPrimeraVezYSeRecuerdaAlTerminar() {
        Guias.usar(PreferenciasEnMemoria())
        val estado = EstadoGuias()
        assertTrue(estado.pedir("inicio", pasos))
        assertEquals("inicio", estado.claveActiva)
        assertFalse(estado.pedir("boleto", pasos))   // no se apilan dos guías
        estado.terminar("inicio")
        assertNull(estado.claveActiva)
        assertTrue(Guias.vista("inicio"))
        assertFalse(estado.pedir("inicio", pasos))   // ya no vuelve a salir
        assertTrue(estado.pedir("boleto", pasos))     // las demás sí
    }

    @Test
    fun salirDeLaPantallaAMitadNoLaMarcaComoVistaYReiniciarLasDevuelveTodas() {
        Guias.usar(PreferenciasEnMemoria())
        val estado = EstadoGuias()
        estado.pedir("asientos", pasos)
        estado.cancelar("asientos")
        assertNull(estado.claveActiva)
        assertFalse(Guias.vista("asientos"))
        estado.pedir("asientos", pasos); estado.terminar("asientos")
        Guias.reiniciar()
        assertFalse(Guias.vista("asientos"))
        assertFalse(estado.pedir("asientos", emptyList()))   // sin pasos no hay guía
    }

    @Test
    fun laTarjetaVaDebajoDelFocoSiCabeYSiNoEncima() {
        val pantalla = Size(1000f, 2000f)
        val tarjeta = Size(900f, 300f)
        // Foco arriba: la tarjeta cabe debajo, separada 20 px
        assertEquals(IntOffset(50, 420), posicionTarjeta(Rect(100f, 200f, 600f, 400f), pantalla, tarjeta, separacion = 20f, margen = 16f))
        // Foco abajo: no cabe debajo, va encima
        assertEquals(IntOffset(50, 1480), posicionTarjeta(Rect(100f, 1800f, 600f, 1950f), pantalla, tarjeta, 20f, 16f))
        // Sin foco: centrada
        assertEquals(IntOffset(50, 850), posicionTarjeta(null, pantalla, tarjeta, 20f, 16f))
        // Foco que ocupa casi toda la pantalla: se pega al borde opuesto al centro del foco
        assertEquals(IntOffset(50, 1684), posicionTarjeta(Rect(0f, 100f, 1000f, 1900f), pantalla, tarjeta, 20f, 16f))
        assertEquals(IntOffset(50, 16), posicionTarjeta(Rect(0f, 300f, 1000f, 1990f), pantalla, tarjeta, 20f, 16f))
    }

    @Test
    fun laFlechaSaleDelTextoYLlegaAlBordeDelFocoMasCercano() {
        val pantalla = Size(1000f, 2000f)
        // Foco debajo y a la derecha del texto: sale por el lado derecho, llega al borde superior del foco
        val f = trazarFlecha(texto = Rect(24f, 300f, 600f, 500f), foco = Rect(700f, 900f, 980f, 1000f), pantalla = pantalla, holgura = 10f)
        assertEquals(Offset(610f, 400f), f.inicio)
        assertEquals(Offset(840f, 890f), f.fin)
        assertEquals(Offset(840f, 400f), f.control)          // nace horizontal y llega vertical
        // Foco ancho encima del texto: baja desde... sube desde la primera línea y llega al borde inferior del foco
        val g = trazarFlecha(texto = Rect(24f, 1200f, 680f, 1400f), foco = Rect(0f, 600f, 1000f, 700f), pantalla = pantalla, holgura = 10f)
        assertEquals(Offset(44f, 1190f), g.inicio)
        assertEquals(710f, g.fin.y, 0f)
        assertTrue(g.fin.x >= 10f && g.fin.x <= 990f)
    }

    /** SharedPreferences mínimas en memoria para probar sin Android. */
    private class PreferenciasEnMemoria : SharedPreferences {
        private val datos = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = datos
        override fun getString(key: String?, defValue: String?) = datos[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = @Suppress("UNCHECKED_CAST") (datos[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int) = datos[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = datos[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = datos[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = datos[key] as? Boolean ?: defValue
        override fun contains(key: String?) = datos.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { datos[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { datos[key!!] = values }
            override fun putInt(key: String?, value: Int) = apply { datos[key!!] = value }
            override fun putLong(key: String?, value: Long) = apply { datos[key!!] = value }
            override fun putFloat(key: String?, value: Float) = apply { datos[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { datos[key!!] = value }
            override fun remove(key: String?) = apply { datos.remove(key) }
            override fun clear() = apply { datos.clear() }
            override fun commit() = true
            override fun apply() {}
        }
    }
}
