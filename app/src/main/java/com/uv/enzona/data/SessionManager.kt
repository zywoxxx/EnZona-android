package com.uv.enzona.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.Usuario

/**
 * Sesión en memoria. En producción el token JWT se guardaría cifrado con
 * DataStore y se enviaría en la cabecera Authorization de cada llamada (RNF-01).
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

    val usuarioId: Long get() = usuario?.id ?: -1L

    fun iniciarSesion(u: Usuario, comoPersonal: Boolean = false) {
        usuario = u
        accesoPersonal = comoPersonal
    }

    fun cerrarSesion() {
        usuario = null
        accesoPersonal = false
    }

    fun refrescar() {
        val id = usuario?.id ?: return
        usuario = MockRepository.usuarios.find { it.id == id }
        // Si la sesión entró por el acceso del personal y ya no tiene roles de personal, vuelve al acceso normal
        if (accesoPersonal && usuario?.esPersonal != true) accesoPersonal = false
    }

    fun tieneRol(rol: Rol): Boolean = usuario?.roles?.contains(rol) == true
}
