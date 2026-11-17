package com.uv.enzona.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/*
 * Dónde está el API.
 *
 * La persona que usa la app no configura nada: al arrancar se prueban en
 * paralelo las direcciones posibles (la compilada, el cable USB con
 * `adb reverse` y el emulador) y se usa la primera que responde, en ese orden
 * de preferencia. El diálogo para fijar una dirección a mano existe solo como
 * opción oculta de desarrollo (ver `gestoOculto`).
 */

/** La PC vista desde un teléfono conectado por USB con `adb reverse tcp:8080 tcp:8080`. */
const val URL_USB = "http://127.0.0.1:8080/"

/** La PC vista desde el emulador de Android Studio. */
const val URL_EMULADOR = "http://10.0.2.2:8080/"

/** Tiempo máximo para que una dirección responda al sondeo. */
private const val SONDEO_SEGUNDOS = 3L

/**
 * Sondea todas las direcciones a la vez y devuelve la primera de la lista que
 * respondió (la lista va de la más a la menos preferida). `null` si ninguna.
 */
suspend fun primeroQueResponde(candidatos: List<String>, sonda: suspend (String) -> Boolean): String? =
    coroutineScope {
        val respuestas = candidatos.map { url -> async { runCatching { sonda(url) }.getOrDefault(false) } }.awaitAll()
        candidatos.zip(respuestas).firstOrNull { (_, responde) -> responde }?.first
    }

/** Cuánto se insiste con un servidor que no responde: Render gratuito tarda 2 a 3 minutos en despertar. */
const val ESPERA_DESPERTAR_MS = 240_000L

/** Pausa entre un sondeo fallido y el siguiente mientras se espera al servidor. */
const val PAUSA_ENTRE_SONDEOS_MS = 5_000L

/**
 * Insiste hasta que alguna dirección responda o se agote `limiteMs`. Devuelve la dirección que
 * respondió o `null`. `alEsperar` recibe los milisegundos transcurridos tras cada intento fallido
 * (para avisar en pantalla); `ahora` y `pausar` se inyectan para probarlo sin esperar de verdad.
 */
suspend fun esperarAQueResponda(
    candidatos: List<String>,
    sonda: suspend (String) -> Boolean,
    limiteMs: Long = ESPERA_DESPERTAR_MS,
    pausaMs: Long = PAUSA_ENTRE_SONDEOS_MS,
    ahora: () -> Long = System::currentTimeMillis,
    pausar: suspend (Long) -> Unit = { delay(it) },
    alEsperar: (Long) -> Unit = {},
): String? {
    val inicio = ahora()
    while (true) {
        primeroQueResponde(candidatos, sonda)?.let { return it }
        val transcurrido = ahora() - inicio
        if (transcurrido >= limiteMs) return null
        alEsperar(transcurrido)
        pausar(pausaMs)
    }
}

/** A partir de cuántos ms de espera se explica que el servidor está arrancando. */
const val AVISO_DESPERTANDO_MS = 15_000L

/** A partir de cuántos ms se tranquiliza: sigue en ello, falta poco. */
const val AVISO_CASI_MS = 120_000L

const val MENSAJE_CONECTANDO = "Conectando con EnZona…"
const val MENSAJE_DESPERTANDO = "El servidor está despertando. Suele tardar un par de minutos; gracias por esperar."
const val MENSAJE_CASI = "Ya casi… el servidor sigue arrancando. No cierres la app."

/** Qué decir en la pantalla de conexión según cuánto se lleva esperando. */
fun mensajeDeEspera(transcurridoMs: Long): String = when {
    transcurridoMs >= AVISO_CASI_MS -> MENSAJE_CASI
    transcurridoMs >= AVISO_DESPERTANDO_MS -> MENSAJE_DESPERTANDO
    else -> MENSAJE_CONECTANDO
}

/** Sondeo real: `GET {url}actuator/health` debe contestar 200 con el estado UP. */
fun sondaHttp(cliente: OkHttpClient = clienteDeSondeo()): suspend (String) -> Boolean = { url ->
    withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url(url.trimEnd('/') + "/actuator/health").build()
        cliente.newCall(peticion).execute().use { respuesta ->
            respuesta.isSuccessful && (respuesta.body?.string()?.contains("UP") == true)
        }
    }
}

private fun clienteDeSondeo(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(SONDEO_SEGUNDOS, TimeUnit.SECONDS)
    .readTimeout(SONDEO_SEGUNDOS, TimeUnit.SECONDS)
    .build()

/**
 * Convierte lo que se escribe en el diálogo oculto «Servidor» en una dirección
 * válida para Retrofit, o devuelve `null` si no tiene forma de dirección.
 *
 *  - `10.20.252.100`              → `http://10.20.252.100:8080/`
 *  - `192.168.1.50:9090`          → `http://192.168.1.50:9090/`
 *  - `https://api.enzona.mx`      → `https://api.enzona.mx/`
 *  - `http://10.0.2.2:8080/api`   → `http://10.0.2.2:8080/api/`
 *
 * Sin esquema se asume HTTP (la API de desarrollo no tiene certificado) y sin
 * puerto el 8080, que es donde arranca EnZona_Api.
 */
fun normalizarUrlServidor(texto: String): String? {
    val limpio = texto.trim()
    if (limpio.isEmpty() || limpio.any { it.isWhitespace() }) return null
    val conEsquema = if (ESQUEMA.containsMatchIn(limpio)) limpio else "http://$limpio"
    val partes = DIRECCION.matchEntire(conEsquema) ?: return null
    val esquema = partes.groupValues[1].lowercase()
    val host = partes.groupValues[2]
    val puerto = partes.groupValues[3].ifEmpty { if (esquema == "http") ":$PUERTO_API" else "" }
    val ruta = partes.groupValues[4].trimEnd('/')
    return "$esquema://$host$puerto$ruta/"
}

private const val PUERTO_API = 8080
private val ESQUEMA = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val DIRECCION = Regex("^(https?)://([a-zA-Z0-9.-]+|\\[[0-9a-fA-F:]+])(:\\d{1,5})?(/[^?#]*)?$", RegexOption.IGNORE_CASE)
