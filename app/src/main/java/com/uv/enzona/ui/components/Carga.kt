package com.uv.enzona.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.SessionManager
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Estados de carga de las pantallas (v5).
 *
 * Con la API, leer datos tarda y puede fallar. Cada pantalla pide sus datos con
 * `rememberCarga { … }` y los pinta con `ContenidoCarga`, que muestra los tres
 * estados visibles: cargando, error con «Reintentar» y contenido.
 */

sealed interface Carga<out T> {
    data object Cargando : Carga<Nothing>
    data class Lista<T>(val valor: T) : Carga<T>
    data class Fallo(val mensaje: String, val sinConexion: Boolean) : Carga<Nothing>
}

/** Texto para la persona a partir de cualquier fallo. */
fun mensajeDe(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar la operación."

@Stable
class EstadoCarga<T> internal constructor() {
    var estado: Carga<T> by mutableStateOf(Carga.Cargando)
        internal set

    /** true mientras se vuelven a pedir datos que ya se estaban mostrando. */
    var actualizando: Boolean by mutableStateOf(false)
        internal set

    internal var intento by mutableIntStateOf(0)

    val valor: T? get() = (estado as? Carga.Lista<T>)?.valor

    fun recargar() {
        intento++
    }
}

/**
 * Pide datos al repositorio y recuerda el resultado. Vuelve a pedirlos cuando
 * cambia alguna `clave`, cuando se llama a `recargar()` o cuando una operación
 * modificó datos (`Repositorio.huboCambios()`). Mientras recarga conserva lo que
 * ya mostraba, para que la pantalla no parpadee.
 */
@Composable
fun <T> rememberCarga(vararg claves: Any?, cargar: suspend () -> T): EstadoCarga<T> {
    val estado = remember(*claves) { EstadoCarga<T>() }
    LaunchedEffect(estado, estado.intento, Repositorio.cambios) {
        if (estado.estado is Carga.Lista) estado.actualizando = true else estado.estado = Carga.Cargando
        try {
            estado.estado = Carga.Lista(cargar())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if ((e as? ErrorEnZona)?.sesionVencida == true) SessionManager.sesionExpirada()
            // Si ya había datos en pantalla se conservan; el fallo solo se muestra cuando no hay nada que enseñar
            if (estado.estado !is Carga.Lista) {
                estado.estado = Carga.Fallo(mensajeDe(e), (e as? ErrorEnZona)?.sinConexion == true)
            }
        } finally {
            estado.actualizando = false
        }
    }
    return estado
}

/** Pinta cargando / error con reintento / contenido. */
@Composable
fun <T> ContenidoCarga(
    estado: EstadoCarga<T>,
    modifier: Modifier = Modifier,
    textoCargando: String = "Cargando…",
    contenido: @Composable (T) -> Unit,
) {
    when (val e = estado.estado) {
        is Carga.Cargando -> PantallaCargando(textoCargando, modifier)
        is Carga.Fallo -> PantallaError(e.mensaje, e.sinConexion, onReintentar = estado::recargar, modifier = modifier)
        is Carga.Lista -> contenido(e.valor)
    }
}

/**
 * Pantalla completa de espera para las pantallas con barra superior: mientras
 * llegan los datos (o si fallan) conserva el título y el botón de volver.
 */
@Composable
fun <T> EsperaDeCarga(estado: EstadoCarga<T>, titulo: String, onVolver: (() -> Unit)?, textoCargando: String = "Cargando…") {
    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = titulo, onVolver = onVolver) },
    ) { relleno ->
        ContenidoCarga(estado, Modifier.padding(relleno), textoCargando) { }
    }
}

@Composable
fun PantallaCargando(texto: String = "Cargando…", modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(Espacio.xl)
            .semantics { contentDescription = texto },
        contentAlignment = Alignment.Center,
    ) {
        IndicadorCarga(texto)
    }
}

@Composable
fun PantallaError(mensaje: String, sinConexion: Boolean, onReintentar: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EstadoVacio(
            icono = if (sinConexion) Icons.Filled.CloudOff else Icons.Filled.ErrorOutline,
            titulo = if (sinConexion) "Sin conexión con el servidor" else "No se pudo cargar",
            texto = mensaje,
            textoAccion = "Reintentar",
            onAccion = onReintentar,
        )
    }
}

/**
 * Ejecuta operaciones que modifican datos (comprar, guardar, cancelar…) con su
 * estado visible: `enCurso` bloquea el botón y `error` trae el texto del fallo.
 */
@Stable
class Operacion internal constructor(private val lanzar: (suspend () -> Unit) -> Unit) {
    var enCurso: Boolean by mutableStateOf(false)
        internal set
    var error: String? by mutableStateOf(null)

    /**
     * @param alTerminar se llama solo si la operación tuvo éxito
     * @param alFallar recibe el error; si no se da, el texto queda en `error`
     */
    fun <T> ejecutar(
        alTerminar: (T) -> Unit = {},
        alFallar: ((Throwable) -> Unit)? = null,
        bloque: suspend () -> T,
    ) {
        if (enCurso) return            // evita el doble envío
        enCurso = true
        error = null
        lanzar {
            try {
                val resultado = bloque()
                Repositorio.huboCambios()
                enCurso = false
                alTerminar(resultado)
            } catch (e: CancellationException) {
                enCurso = false
                throw e
            } catch (e: Exception) {
                enCurso = false
                if ((e as? ErrorEnZona)?.sesionVencida == true) SessionManager.sesionExpirada()
                if (alFallar != null) alFallar(e) else error = mensajeDe(e)
            }
        }
    }
}

@Composable
fun rememberOperacion(): Operacion {
    val alcance = rememberCoroutineScope()
    return remember { Operacion { bloque -> alcance.launch { bloque() } } }
}

/** Rótulo visible cuando la app trabaja con datos de prueba en memoria y no con la API. */
@Composable
fun EtiquetaDatosDePrueba(modifier: Modifier = Modifier) {
    if (!Repositorio.esDemostracion) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            "Datos de prueba · sin conexión al servidor",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                // La app dibuja de borde a borde: el rótulo va debajo de la barra de estado, no encima del reloj
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Espacio.margen, vertical = Espacio.xs)
                .semantics { contentDescription = "La app muestra datos de prueba. No está conectada al servidor." },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360, heightDp = 420)
@Composable
private fun PreviewError() {
    EnZonaTheme {
        PantallaError("No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.", true, {})
    }
}
