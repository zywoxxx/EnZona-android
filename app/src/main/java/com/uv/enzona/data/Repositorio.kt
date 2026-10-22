package com.uv.enzona.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uv.enzona.BuildConfig
import com.uv.enzona.data.remote.AlmacenCifrado
import com.uv.enzona.data.remote.RepositorioRemoto

/**
 * Punto único de acceso a los datos.
 *
 * La implementación se elige con `BuildConfig.ENZONA_API_URL`, que sale de
 * `enzona.api.url` en `local.properties`:
 *  - con URL → [RepositorioRemoto] (la API REST del backend);
 *  - vacía  → [RepositorioMock] (datos de prueba en memoria), y la app muestra
 *    la etiqueta «Datos de prueba» para que sea evidente.
 */
object Repositorio {

    @Volatile
    private var instancia: RepositorioEnZona? = null

    /** Se llama una vez al crear la actividad. Las pruebas pueden fijar otra implementación con [usar]. */
    fun iniciar(contexto: Context) {
        if (instancia != null) return
        val url = BuildConfig.ENZONA_API_URL
        urlBase = url.trim().trimEnd('/')
        instancia = if (url.isBlank()) RepositorioMock else RepositorioRemoto(url, AlmacenCifrado(contexto))
    }

    /** Dirección de la API (sin barra final); vacía con datos de prueba. */
    var urlBase: String = ""
        private set

    /** Las portadas del almacén local de la API llegan relativas (`/api/imagenes/…`); aquí se completan. */
    fun urlAbsoluta(url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.startsWith("/") && urlBase.isNotBlank() -> urlBase + url
        else -> url
    }

    fun usar(repositorio: RepositorioEnZona) {
        instancia = repositorio
    }

    /** Sin iniciar (previews, pruebas) se trabaja con los datos de prueba. */
    val actual: RepositorioEnZona get() = instancia ?: RepositorioMock

    val esDemostracion: Boolean get() = actual.esDemostracion

    /**
     * Contador de cambios. Cada operación que modifica datos lo incrementa y las
     * pantallas que están cargando datos los vuelven a pedir (ver `rememberCarga`).
     */
    var cambios by mutableIntStateOf(0)
        private set

    fun huboCambios() {
        cambios++
    }
}

/**
 * Preferencias locales del teléfono (no viajan al servidor): zona elegida a
 * mano, consentimiento de ubicación y su última lectura. RNF-11: la ubicación
 * solo se usa si la persona dio su consentimiento y no se guarda historial.
 */
object Preferencias {

    val ciudad = mutableStateOf("Orizaba, Veracruz")
    val pais = mutableStateOf("México")

    /** Consentimiento para ordenar los eventos por cercanía. */
    val contenidoPorUbicacion = mutableStateOf(false)

    /** Última ubicación leída con permiso; solo vive en memoria. */
    var ubicacion: Ubicacion? by mutableStateOf(null)
}
