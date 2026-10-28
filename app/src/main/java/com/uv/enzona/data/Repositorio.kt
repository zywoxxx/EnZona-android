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

    private var contexto: Context? = null
    private var almacen: AlmacenCifrado? = null

    /** Se llama una vez al crear la actividad. Las pruebas pueden fijar otra implementación con [usar]. */
    fun iniciar(contexto: Context) {
        if (instancia != null) return
        this.contexto = contexto.applicationContext
        conectar(urlGuardada(contexto) ?: urlPorDefecto)
    }

    private fun conectar(url: String) {
        urlBase = url.trim().trimEnd('/')
        instancia = if (url.isBlank()) RepositorioMock else RepositorioRemoto(url, almacenCifrado())
    }

    private fun almacenCifrado(): AlmacenCifrado =
        almacen ?: AlmacenCifrado(checkNotNull(contexto) { "Repositorio.iniciar no se llamó" }).also { almacen = it }

    /** Dirección de la API (sin barra final); vacía con datos de prueba. */
    var urlBase: String = ""
        private set

    /** Dirección compilada desde `local.properties`; vacía significa datos de prueba. */
    val urlPorDefecto: String get() = BuildConfig.ENZONA_API_URL.trim()

    /**
     * Se incrementa al cambiar de servidor: la pantalla de arranque vuelve a conectar
     * y a restaurar la sesión contra la dirección nueva.
     */
    var generacion by mutableIntStateOf(0)
        private set

    /**
     * Opción oculta de desarrollo: fija el servidor a mano (la IP de la PC cambia
     * con cada red). La dirección se guarda en el teléfono y anula la detección
     * automática; `null` la borra y vuelve a detectar. La sesión actual se cierra
     * porque el token no sirve en otro servidor.
     */
    fun cambiarServidor(url: String?) {
        val ctx = contexto ?: return
        ctx.getSharedPreferences(ARCHIVO_CONFIG, Context.MODE_PRIVATE).edit().apply {
            if (url == null) remove(CLAVE_URL) else putString(CLAVE_URL, url)
        }.apply()
        SessionManager.cerrarSesion()
        conectar(url ?: urlPorDefecto)
        generacion++
    }

    /** Dirección fijada a mano desde el diálogo oculto, si la hay. */
    fun urlGuardada(contexto: Context): String? =
        contexto.getSharedPreferences(ARCHIVO_CONFIG, Context.MODE_PRIVATE).getString(CLAVE_URL, null)

    /** Sonda de red; las pruebas la sustituyen. */
    var sonda: suspend (String) -> Boolean = sondaHttp()

    /**
     * Detección automática al arrancar: la persona no configura nada. Se prueban a
     * la vez la dirección compilada, la PC por cable USB y el emulador, y se usa la
     * primera que responde. Si ninguna responde se queda la compilada (la pantalla
     * de arranque mostrará «Sin conexión»). No aplica con datos de prueba ni cuando
     * se fijó una dirección a mano.
     */
    suspend fun elegirServidorDisponible() {
        val ctx = contexto ?: return
        if (urlPorDefecto.isBlank() || urlGuardada(ctx) != null) return
        val candidatos = listOf(urlPorDefecto, URL_USB, URL_EMULADOR).distinct()
        val disponible = primeroQueResponde(candidatos, sonda) ?: return
        if (disponible.trimEnd('/') != urlBase) conectar(disponible)
    }

    private const val ARCHIVO_CONFIG = "enzona_config"
    private const val CLAVE_URL = "url_api"

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
