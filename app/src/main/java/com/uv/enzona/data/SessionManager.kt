package com.uv.enzona.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.Usuario

/**
 * Cuenta con sesión iniciada, para que las pantallas la observen.
 *
 * El token JWT no vive aquí: lo guarda cifrado `AlmacenCifrado` y lo envía el
 * interceptor de `RepositorioRemoto` en la cabecera Authorization (RNF-01). Los
 * datos de la cuenta vienen de `GET /api/usuarios/me`.
 */
object SessionManager {

    var usuario: Usuario? by mutableStateOf(null)
        private set

    /**
     * Indica si la sesión se abrió por el acceso del personal.
     * Un usuario que es asistente y organizador a la vez entra a «Inicio»
     * por el acceso normal y a su panel por el acceso de personal.
     */
    var accesoPersonal: Boolean by mutableStateOf(false)
        private set

    val autenticado: Boolean get() = usuario != null

    /** La sesión terminó porque el token expiró o dejó de ser válido: el acceso lo explica. */
    var expirada: Boolean by mutableStateOf(false)
        private set

    val usuarioId: Long get() = usuario?.id ?: -1L

    fun iniciarSesion(u: Usuario, comoPersonal: Boolean = false) {
        usuario = u
        accesoPersonal = comoPersonal
        expirada = false
    }

    fun cerrarSesion() {
        usuario = null
        accesoPersonal = false
    }

    /** El servidor respondió 401: se cierra la sesión local y la app vuelve al acceso. */
    fun sesionExpirada() {
        if (usuario == null) return
        cerrarSesion()
        expirada = true
    }

    /** Sustituye los datos de la cuenta por los que devolvió el servidor (perfil o roles cambiaron). */
    fun actualizar(u: Usuario) {
        usuario = u
        // Si la sesión entró por el acceso del personal y ya no tiene roles de personal, vuelve al acceso normal
        if (accesoPersonal && !u.esPersonal) accesoPersonal = false
    }

    /** Vuelve a leer la cuenta del repositorio (en la API: `GET /api/usuarios/me`). */
    suspend fun refrescar() {
        if (usuario == null) return
        actualizar(Repositorio.actual.usuarioActual())
    }

    fun tieneRol(rol: Rol): Boolean = usuario?.roles?.contains(rol) == true
}
