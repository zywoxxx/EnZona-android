package com.uv.enzona.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Almacén local del token de sesión y de los datos del modo sin conexión del
 * validador (manifiesto y escaneos pendientes de sincronizar).
 */
interface AlmacenSesion {

    fun leer(clave: String): String?

    /** `valor = null` borra la clave. */
    fun guardar(clave: String, valor: String?)

    var token: String?
        get() = leer(TOKEN)
        set(valor) = guardar(TOKEN, valor)

    companion object {
        const val TOKEN = "token"
    }
}

/** Almacén volátil: pruebas unitarias y previews. */
class AlmacenEnMemoria : AlmacenSesion {

    private val datos = mutableMapOf<String, String>()

    override fun leer(clave: String): String? = datos[clave]

    override fun guardar(clave: String, valor: String?) {
        if (valor == null) datos.remove(clave) else datos[clave] = valor
    }
}

/**
 * Almacén cifrado en el dispositivo (RNF-01): `EncryptedSharedPreferences` con
 * una clave maestra AES-256 guardada en el Android Keystore. El token nunca se
 * escribe en claro ni sale del teléfono, y la copia de seguridad de la app está
 * desactivada en el manifiesto.
 */
class AlmacenCifrado(contexto: Context) : AlmacenSesion {

    private val preferencias: SharedPreferences = abrir(contexto.applicationContext)

    private fun abrir(contexto: Context): SharedPreferences = try {
        crear(contexto)
    } catch (e: Exception) {
        // Si el archivo cifrado quedó ilegible (p. ej. tras restaurar el teléfono), se descarta:
        // la persona solo tiene que volver a iniciar sesión.
        contexto.deleteSharedPreferences(ARCHIVO)
        crear(contexto)
    }

    private fun crear(contexto: Context): SharedPreferences {
        val claveMaestra = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            ARCHIVO, claveMaestra, contexto,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun leer(clave: String): String? = preferencias.getString(clave, null)

    override fun guardar(clave: String, valor: String?) {
        preferencias.edit().apply { if (valor == null) remove(clave) else putString(clave, valor) }.apply()
    }

    private companion object {
        const val ARCHIVO = "enzona_sesion"
    }
}
