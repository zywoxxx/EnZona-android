package com.uv.enzona.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.normalizarUrlServidor
import com.uv.enzona.ui.theme.TextoSecundario

/**
 * Opción oculta de desarrollo: fija la dirección del API a mano cuando la
 * detección automática no basta (p. ej. otra PC en la red). Se abre con siete
 * toques en el logo de la pantalla de acceso o en el aviso de «Sin conexión».
 * Quien usa la app nunca la ve.
 */
@Composable
fun DialogoServidor(onCerrar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf(Repositorio.urlBase.ifBlank { Repositorio.urlPorDefecto }) }
    var error by remember { mutableStateOf<String?>(null) }
    val hayPorDefecto = Repositorio.urlPorDefecto.isNotBlank()

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Servidor (desarrollo)") },
        text = {
            Column {
                Text(
                    "La app detecta sola el servidor al arrancar. Fija aquí una dirección solo si hace falta; " +
                        "sin puerto se usa el 8080.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("http://IP:8080/") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (hayPorDefecto) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Compilada: ${Repositorio.urlPorDefecto}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = normalizarUrlServidor(texto)
                if (url == null) {
                    error = "Escribe una dirección como 192.168.1.50 o http://192.168.1.50:8080/"
                } else {
                    Repositorio.cambiarServidor(url)
                    onCerrar()
                }
            }) { Text("Conectar") }
        },
        dismissButton = {
            if (hayPorDefecto) {
                TextButton(onClick = { Repositorio.cambiarServidor(null); onCerrar() }) { Text("Automático") }
            } else {
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}
