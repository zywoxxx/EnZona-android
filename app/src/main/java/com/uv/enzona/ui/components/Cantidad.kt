package com.uv.enzona.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.Tamanos

/**
 * Selector de cantidad de boletos: botones de 48 dp para restar y sumar, y la
 * cifra en grande. El máximo lo fija la disponibilidad real del evento (cupo,
 * stock del tipo y asientos libres), no un límite comercial inventado.
 */
@Composable
fun SelectorCantidad(
    cantidad: Int,
    maximo: Int,
    onCambio: (Int) -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String = "Cantidad de boletos",
    habilitado: Boolean = true,
) {
    val minimo = 1
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(etiqueta, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (maximo <= 0) "Sin disponibilidad" else "Hasta $maximo disponibles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { onCambio((cantidad - 1).coerceAtLeast(minimo)) },
                enabled = habilitado && cantidad > minimo,
                modifier = Modifier.size(Tamanos.control),
            ) { Icon(Icons.Filled.Remove, contentDescription = "Quitar un boleto") }
            Text(
                cantidad.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .padding(horizontal = Espacio.xs)
                    .semantics { contentDescription = "$cantidad boletos" },
            )
            FilledTonalIconButton(
                onClick = { onCambio((cantidad + 1).coerceAtMost(maximo)) },
                enabled = habilitado && cantidad < maximo,
                modifier = Modifier.size(Tamanos.control),
            ) { Icon(Icons.Filled.Add, contentDescription = "Agregar un boleto") }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6)
@Composable
private fun PreviewSelectorCantidad() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l)) {
            SelectorCantidad(cantidad = 2, maximo = 8, onCambio = {})
            Spacer(Modifier.height(Espacio.l))
            SelectorCantidad(cantidad = 1, maximo = 1, onCambio = {})
        }
    }
}
