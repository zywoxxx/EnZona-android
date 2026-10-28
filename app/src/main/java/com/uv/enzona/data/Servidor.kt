package com.uv.enzona.data

/**
 * Convierte lo que la persona escribe en el diálogo «Servidor» en una dirección
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
