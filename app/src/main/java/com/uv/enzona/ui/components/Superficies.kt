package com.uv.enzona.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.Tamanos

/**
 * Tarjeta de la marca: superficie morada con esquinas grandes. Si recibe
 * `onClick`, toda la tarjeta es un control (con semántica de botón).
 */
@Composable
fun TarjetaEnZona(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    relleno: PaddingValues = PaddingValues(Espacio.l),
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val colores = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colores) {
            Column(Modifier.padding(relleno), content = contenido)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colores) {
            Column(Modifier.padding(relleno), content = contenido)
        }
    }
}

/**
 * Aviso en línea con tono semántico: icono + texto y acción opcional. Para
 * errores recuperables, resultados y notas que el usuario debe leer.
 */
@Composable
fun Aviso(
    texto: String,
    modifier: Modifier = Modifier,
    tono: Tono = Tono.Neutro,
    titulo: String? = null,
    icono: ImageVector? = null,
    textoAccion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    val (fondo, contenido) = coloresDeTono(tono)
    val iconoFinal = icono ?: when (tono) {
        Tono.Error -> Icons.Filled.ErrorOutline
        Tono.Aviso -> Icons.Filled.WarningAmber
        else -> Icons.Filled.Info
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = fondo,
        contentColor = contenido,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(iconoFinal, contentDescription = null, modifier = Modifier.size(Tamanos.iconoDato).padding(top = 1.dp))
            Column(
                Modifier
                    .padding(start = Espacio.m)
                    .weight(1f)
            ) {
                if (titulo != null) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                }
                Text(texto, style = MaterialTheme.typography.bodyMedium)
                if (textoAccion != null && onAccion != null) {
                    TextButton(onClick = onAccion, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(textoAccion, color = contenido, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Marco para controles de demostración (pagos simulados, OTP visible…). Se ve
 * distinto a propósito: borde discontinuo y rótulo, para que nadie confunda una
 * simulación con una operación real.
 */
@Composable
fun ZonaDemostracion(
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Science, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Espacio.s))
                Text(
                    "Zona de demostración",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(Espacio.s))
            contenido()
        }
    }
}

/** Fila de dato: icono, valor y, opcionalmente, un texto de apoyo debajo. */
@Composable
fun FilaDato(
    icono: ImageVector,
    valor: String,
    modifier: Modifier = Modifier,
    apoyo: String? = null,
    descripcionIcono: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            icono,
            contentDescription = descripcionIcono,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(Tamanos.iconoDato)
                .padding(top = 2.dp),
        )
        Column(Modifier.padding(start = Espacio.m)) {
            Text(valor, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (apoyo != null) {
                Text(apoyo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Fila de importe para desgloses de pago. */
@Composable
fun FilaImporte(
    concepto: String,
    importe: String,
    modifier: Modifier = Modifier,
    destacada: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (destacada) 6.dp else 3.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            concepto,
            color = if (destacada) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = if (destacada) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
        Text(
            importe,
            color = MaterialTheme.colorScheme.onSurface,
            style = if (destacada) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6)
@Composable
private fun PreviewSuperficies() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Espacio.m)) {
            TarjetaEnZona {
                FilaDato(Icons.Filled.CalendarMonth, "Sáb 3 oct · 19:00", apoyo = "Hora local del evento")
                Spacer(Modifier.height(Espacio.m))
                FilaDato(Icons.Filled.LocationOn, "Teatro de la Ciudad", apoyo = "Av. Zaragoza 512, Centro")
            }
            Aviso("La pasarela rechazó el pago. No se realizó ningún cargo.", tono = Tono.Error, textoAccion = "Reintentar", onAccion = {})
            Aviso("Ya tienes un boleto para este evento.", tono = Tono.Exito)
            ZonaDemostracion {
                Text("Los controles de esta zona no representan cobros reales.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
