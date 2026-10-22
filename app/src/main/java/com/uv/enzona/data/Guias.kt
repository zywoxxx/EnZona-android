package com.uv.enzona.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Qué guías interactivas (recorridos de la primera vez) ya vio esta persona en
 * este teléfono. Hay guías para asistentes y organizadores; el validador y el
 * administrador no las tienen: su pantalla es de trabajo y se explica sola.
 * Se guarda en preferencias locales: no viaja al servidor y se puede reiniciar
 * desde Perfil → «Volver a ver las guías».
 *
 * Sin [iniciar] (vistas previas y pruebas) todas las guías cuentan como vistas,
 * para que nunca aparezcan encima de lo que se está probando.
 */
object Guias {

    private const val ARCHIVO = "guias_vistas"

    private var preferencias: SharedPreferences? = null

    /** Cambia cada vez que se marca o reinicia una guía, para que las pantallas reaccionen. */
    var cambios by mutableIntStateOf(0)
        private set

    fun iniciar(contexto: Context) {
        preferencias = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
    }

    /** Solo para pruebas: usa estas preferencias (o ninguna). */
    fun usar(prefs: SharedPreferences?) {
        preferencias = prefs
        cambios++
    }

    fun vista(clave: String): Boolean = preferencias?.getBoolean(clave, false) ?: true

    fun marcarVista(clave: String) {
        preferencias?.edit()?.putBoolean(clave, true)?.apply()
        cambios++
    }

    /** Vuelve a mostrar todas las guías la próxima vez que se abra cada pantalla. */
    fun reiniciar() {
        preferencias?.edit()?.clear()?.apply()
        cambios++
    }

    /** Claves de las guías de la app (una por pantalla que necesita explicación). */
    object Clave {
        const val INICIO = "inicio"
        const val DETALLE_EVENTO = "detalle_evento"
        const val ASIENTOS = "asientos"
        const val BOLETO = "boleto"
        const val ORGANIZADOR = "organizador"
        const val NUEVO_EVENTO = "nuevo_evento"
        const val PANEL_EVENTO = "panel_evento"
    }
}
