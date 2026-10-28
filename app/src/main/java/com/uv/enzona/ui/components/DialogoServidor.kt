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
 * Cambia la dirección del API sin recompilar: la IP de la PC donde corre el
 * servidor cambia con cada red. Se abre desde la pantalla de acceso y desde el
 * aviso de «Sin conexión con el servidor».
 */
@Composable
fun DialogoServidor(onCerrar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf(Repositorio.urlBase.ifBlank { Repositorio.urlPorDefecto }) }
    var error by remember { mutableStateOf<String?>(null) }
    val hayPorDefecto = Repositorio.urlPorDefecto.isNotBlank()

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Servidor de EnZona") },
        text = {
            Column {
                Text(
                    "Dirección del API. Si cambió la red, escribe la IP de la PC donde corre el servidor; " +
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
                TextButton(onClick = { Repositorio.cambiarServidor(null); onCerrar() }) { Text("Usar la compilada") }
            } else {
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}
