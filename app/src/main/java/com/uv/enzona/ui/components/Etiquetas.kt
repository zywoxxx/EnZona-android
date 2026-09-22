package com.uv.enzona.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.ui.theme.EnZonaTema
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio

/**
 * Tono de una etiqueta o aviso. `Marca` usa el naranja; los demás son
 * semánticos y no se usan para decorar.
 */
enum class Tono { Neutro, Marca, Destacado, Exito, Aviso, Error }

/** Par de colores (contenedor, contenido) de un tono, tomado del tema. */
@Composable
fun coloresDeTono(tono: Tono): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val s = EnZonaTema.semanticos
    return when (tono) {
        Tono.Neutro -> s.informacionContenedor to s.sobreInformacionContenedor
        Tono.Marca -> c.primaryContainer to c.onPrimaryContainer
        Tono.Destacado -> c.tertiaryContainer to c.onTertiaryContainer
        Tono.Exito -> s.exitoContenedor to s.sobreExitoContenedor
        Tono.Aviso -> s.avisoContenedor to s.sobreAvisoContenedor
        Tono.Error -> c.errorContainer to c.onErrorContainer
    }
}

/**
 * Etiqueta compacta (chip de solo lectura). Combina color con texto y, cuando
 * hace falta, un icono, para que el estado no dependa solo del color.
 */
@Composable
fun Etiqueta(
    texto: String,
    modifier: Modifier = Modifier,
    tono: Tono = Tono.Neutro,
    icono: ImageVector? = null,
) {
    val (fondo, contenido) = coloresDeTono(tono)
    Row(
        modifier = modifier
            .background(fondo, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = texto },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, tint = contenido, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Espacio.xs))
        }
        Text(texto, color = contenido, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Etiqueta de estado del boleto (diagrama de estados: válido, usado,
 * cancelado, expirado). El estado del boleto es distinto del estado del pago.
 */
@Composable
fun EtiquetaEstadoBoleto(estado: EstadoBoleto, modifier: Modifier = Modifier) {
    val (texto, tono, icono) = when (estado) {
        EstadoBoleto.VALIDO -> Triple("Válido", Tono.Exito, Icons.Filled.CheckCircle)
        EstadoBoleto.USADO -> Triple("Usado", Tono.Neutro, Icons.Filled.DoneAll)
        EstadoBoleto.CANCELADO -> Triple("Cancelado", Tono.Error, Icons.Filled.Block)
        EstadoBoleto.EXPIRADO -> Triple("Expirado", Tono.Neutro, Icons.Filled.HistoryToggleOff)
    }
    Etiqueta(texto = texto, tono = tono, icono = icono, modifier = modifier)
}

/** Etiqueta "Agotado" para tarjetas y detalle. */
@Composable
fun EtiquetaAgotado(modifier: Modifier = Modifier) {
    Etiqueta(texto = "Agotado", tono = Tono.Error, icono = Icons.Filled.EventBusy, modifier = modifier)
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6)
@Composable
private fun PreviewEtiquetas() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Espacio.s)) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Espacio.s)) {
                Etiqueta("Música")
                Etiqueta("Gratis", tono = Tono.Destacado)
                Etiqueta("Asientos numerados", tono = Tono.Marca)
            }
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Espacio.s)) {
                EtiquetaEstadoBoleto(EstadoBoleto.VALIDO)
                EtiquetaEstadoBoleto(EstadoBoleto.USADO)
                EtiquetaEstadoBoleto(EstadoBoleto.CANCELADO)
                EtiquetaEstadoBoleto(EstadoBoleto.EXPIRADO)
            }
            EtiquetaAgotado()
        }
    }
}
