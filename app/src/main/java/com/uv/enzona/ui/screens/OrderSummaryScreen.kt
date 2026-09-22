package com.uv.enzona.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.ui.components.Aviso
import com.uv.enzona.ui.components.BarraSuperiorEnZona
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.BotonSecundario
import com.uv.enzona.ui.components.EtiquetaEstadoBoleto
import com.uv.enzona.ui.components.FilaDato
import com.uv.enzona.ui.components.TarjetaEnZona
import com.uv.enzona.ui.components.Tono
import com.uv.enzona.ui.preview.Fixtures
import com.uv.enzona.ui.theme.EnZonaTema
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.Formato
import com.uv.enzona.util.FechaEvento

/**
 * Confirmación de una compra de N boletos: resumen de la orden y acceso a cada
 * QR ("Boleto k de N"). Cada boleto es independiente para el acceso; el
 * comprador puede mostrarlos desde su cuenta a sus acompañantes.
 */
@Composable
fun OrderSummaryScreen(
    ordenId: Long,
    onVerBoleto: (Long) -> Unit,
    onVerMisBoletos: () -> Unit,
    onVolverAInicio: () -> Unit,
) {
    val orden = MockRepository.ordenes.find { it.id == ordenId } ?: return
    val boletos = MockRepository.boletosDeOrden(ordenId)
    val evento = boletos.firstOrNull()?.evento ?: MockRepository.obtenerEvento(orden.eventoId) ?: return
    val n = boletos.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = "Compra confirmada", onVolver = null) },
        bottomBar = {
            Column(Modifier.padding(horizontal = Espacio.margen, vertical = Espacio.m), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                BotonEnZona(texto = "Ver mis boletos", onClick = onVerMisBoletos)
                BotonSecundario(texto = "Volver a Inicio", onClick = onVolverAInicio)
            }
        },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Espacio.margen, vertical = Espacio.s),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle, contentDescription = null,
                        tint = EnZonaTema.semanticos.exito, modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(Espacio.m))
                    Text(
                        if (n == 1) "Tu boleto está listo" else "Tus $n boletos están listos",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            item {
                TarjetaEnZona {
                    Text(evento.nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Espacio.m))
                    FilaDato(Icons.Filled.CalendarMonth, FechaEvento.corto(evento.fecha), descripcionIcono = "Fecha")
                    Spacer(Modifier.height(Espacio.s))
                    FilaDato(Icons.Filled.LocationOn, evento.lugar, descripcionIcono = "Lugar")
                    Spacer(Modifier.height(Espacio.m))
                    Text(
                        if (orden.total > 0) "Total pagado: ${Formato.importeMxn(orden.total)} · orden #${orden.id}"
                        else "Sin cobro · orden #${orden.id}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (n > 1) {
                item {
                    Aviso(
                        texto = "Cada boleto tiene su propio código QR y se valida por separado en el acceso. Muéstralos desde tu cuenta a tus acompañantes.",
                        tono = Tono.Neutro,
                    )
                }
            }
            itemsIndexed(boletos, key = { _, b -> b.id }) { indice, boleto ->
                FilaBoletoDeOrden(boleto = boleto, posicion = indice + 1, total = n, onClick = { onVerBoleto(boleto.id) })
            }
        }
    }
}

@Composable
private fun FilaBoletoDeOrden(boleto: BoletoDetalle, posicion: Int, total: Int, onClick: () -> Unit) {
    TarjetaEnZona(onClick = onClick, relleno = PaddingValues(Espacio.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.QrCode2, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Text(
                    "Boleto $posicion de $total",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    listOfNotNull(boleto.nombreTipo, boleto.etiquetaAsiento).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    boleto.codigo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EtiquetaEstadoBoleto(boleto.estado)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewFilaOrden() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            FilaBoletoDeOrden(Fixtures.boletoValido, 1, 3) {}
            FilaBoletoDeOrden(Fixtures.boletoUsado, 2, 3) {}
        }
    }
}
