package com.uv.enzona.ui.screens.auth

/**
 * SIMULACIÓN del envío del código de verificación. Guarda en memoria el código
 * que devolvió el registro para mostrarlo en la pantalla de verificación. Solo
 * tiene contenido cuando el backend corre en su perfil dev (o con datos de
 * prueba); con un proveedor real de correo o SMS queda vacío.
 */
internal object CodigoSimulado {

    private val codigos = mutableMapOf<Long, String>()

    fun recordar(usuarioId: Long, codigo: String?) {
        if (codigo.isNullOrBlank()) codigos.remove(usuarioId) else codigos[usuarioId] = codigo
    }

    fun de(usuarioId: Long): String = codigos[usuarioId].orEmpty()

    fun olvidar(usuarioId: Long) {
        codigos.remove(usuarioId)
    }
}
