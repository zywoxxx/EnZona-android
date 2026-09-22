package com.uv.enzona.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio

/**
 * Confirmación antes de adquirir el rol de organizador (v3, cambio 2).
 *
 * El botón de confirmar va en coral (acción destacada) y "Cancelar" en texto
 * azul marino; ninguno se activa con un solo toque accidental sobre la tarjeta
 * de Perfil porque el diálogo se abre primero.
 */
@Composable
fun DialogoConvertirseOrganizador(
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("¿Convertirte en organizador?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s), modifier = Modifier.testTag("dialogo_convertirse")) {
                Text("Al confirmar, en tu cuenta cambia esto:", style = MaterialTheme.typography.bodyMedium)
                Text("• Aparece la pestaña Mis eventos.", style = MaterialTheme.typography.bodyMedium)
                Text("• Podrás crear y publicar eventos gratuitos o de pago, definir aforo y asientos.", style = MaterialTheme.typography.bodyMedium)
                Text("• Podrás designar personal de acceso para tus eventos.", style = MaterialTheme.typography.bodyMedium)
                Text("• Conservas tu cuenta de asistente y podrás dejar de ser organizador más adelante.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier.testTag("boton_confirmar_organizador"),
            ) { Text("Sí, convertirme en organizador") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.testTag("boton_cancelar_organizador")) {
                Text("Cancelar", color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

/**
 * Confirmación para dejar de ser organizador. Si `motivoBloqueo` no es null,
 * la acción está deshabilitada y se muestra por qué.
 */
@Composable
fun DialogoDejarOrganizador(
    motivoBloqueo: String?,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("¿Dejar de ser organizador?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s), modifier = Modifier.testTag("dialogo_dejar")) {
                if (motivoBloqueo != null) {
                    Aviso(titulo = "Por ahora no es posible", texto = motivoBloqueo, tono = Tono.Aviso)
                } else {
                    Text("Se quitará el rol de organizador de tu cuenta:", style = MaterialTheme.typography.bodyMedium)
                    Text("• Desaparece la pestaña Mis eventos.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Tus eventos en borrador o realizados se conservan en el historial.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Sigues siendo asistente y puedes volver a activar el rol cuando quieras.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                enabled = motivoBloqueo == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag("boton_confirmar_dejar"),
            ) { Text("Sí, dejar de ser organizador") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.testTag("boton_cancelar_dejar")) {
                Text(if (motivoBloqueo == null) "Cancelar" else "Entendido", color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewConvertirse() {
    EnZonaTheme { DialogoConvertirseOrganizador({}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewDejarBloqueado() {
    EnZonaTheme { DialogoDejarOrganizador("Tienes 1 evento publicado con boletos vigentes: «Concierto».", {}, {}) }
}
