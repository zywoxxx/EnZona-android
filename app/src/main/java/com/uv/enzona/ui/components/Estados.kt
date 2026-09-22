package com.uv.enzona.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.Tamanos

/** Barra superior con botón de volver. Título en una línea con elipsis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperiorEnZona(
    titulo: String,
    onVolver: (() -> Unit)?,
    modifier: Modifier = Modifier,
    volverHabilitado: Boolean = true,
    acciones: @Composable () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onVolver != null) {
                IconButton(onClick = onVolver, enabled = volverHabilitado) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = { acciones() },
        // El Scaffold raíz (MainActivity) ya aplica el inset de la barra de estado;
        // sin esto la barra superior lo sumaba dos veces y dejaba un hueco.
        windowInsets = WindowInsets(0.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** Estado vacío: icono, título, explicación y una acción para salir de él. */
@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    textoAccion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Espacio.xl, vertical = Espacio.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icono, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(Espacio.l))
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Espacio.s))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (textoAccion != null && onAccion != null) {
            Spacer(Modifier.height(Espacio.xl))
            BotonSecundario(texto = textoAccion, onClick = onAccion, modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}

/** Indicador de carga con texto, centrado en una fila. */
@Composable
fun IndicadorCarga(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(Espacio.m))
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Selector desplegable accesible (botón de 48 dp + menú). Se usa donde un
 * control táctil pequeño no sería suficiente, por ejemplo para elegir asiento
 * por sección, fila y número.
 */
@Composable
fun SelectorDesplegable(
    etiqueta: String,
    opciones: List<String>,
    seleccion: String?,
    onSeleccion: (String) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { abierto = true },
            enabled = habilitado && opciones.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(Tamanos.control),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.weight(1f)) {
                Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    seleccion ?: "Elegir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = { onSeleccion(opcion); abierto = false },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6)
@Composable
private fun PreviewEstados() {
    EnZonaTheme {
        Column {
            BarraSuperiorEnZona(titulo = "Elige tu asiento", onVolver = {})
            EstadoVacio(
                icono = Icons.Filled.ConfirmationNumber,
                titulo = "Aún no tienes boletos",
                texto = "Cuando confirmes asistencia o compres un boleto, aparecerá aquí con su código QR.",
                textoAccion = "Explorar eventos", onAccion = {},
            )
            IndicadorCarga("Procesando el pago…")
            Spacer(Modifier.height(Espacio.l))
            Row(Modifier.padding(Espacio.l), horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                SelectorDesplegable("Sección", listOf("Planta baja", "Balcón"), "Planta baja", {}, Modifier.weight(1f))
                SelectorDesplegable("Fila", listOf("A", "B"), null, {}, Modifier.weight(1f))
            }
        }
    }
}
