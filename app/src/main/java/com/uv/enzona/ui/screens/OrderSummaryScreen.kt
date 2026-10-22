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
import androidx.compose.foundation.lazy.LazyRow
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
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.model.EstadoPago
import com.uv.enzona.ui.components.EsperaDeCarga
import com.uv.enzona.ui.components.ZonaDemostracion
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.components.rememberOperacion
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
    val carga = rememberCarga(ordenId) {
        val repositorio = Repositorio.actual
        val compra = repositorio.orden(ordenId)
        compra to (compra.boletos.firstOrNull()?.evento ?: repositorio.evento(compra.orden.eventoId))
    }
    val cargado = carga.valor
    if (cargado == null) {
        EsperaDeCarga(carga, titulo = "Tu orden", onVolver = null, textoCargando = "Cargando tu orden…")
        return
    }
    val (compra, evento) = cargado
    val orden = compra.orden
    val boletos = compra.boletos
    val n = boletos.size
    // OXXO / SPEI: la orden existe pero los boletos se emiten cuando la pasarela confirma el pago
    val pagoPendiente = compra.pago?.takeIf { it.estado == EstadoPago.PENDIENTE }
    val operacion = rememberOperacion()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = if (pagoPendiente != null) "Pago pendiente" else "Compra confirmada", onVolver = null) },
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
            if (pagoPendiente != null) {
                item {
                    Aviso(
                        titulo = "Tu orden está reservada",
                        texto = "Paga con esta referencia para recibir tus boletos: ${pagoPendiente.referenciaPasarela ?: "—"}. " +
                            "Tus lugares quedan apartados durante 24 horas.",
                        tono = Tono.Aviso,
                    )
                }
                item {
                    ZonaDemostracion {
                        Text(
                            "La pasarela está simulada: no hay ningún cobro real. Este botón hace lo que haría el aviso " +
                                "de la pasarela al recibir el pago.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Espacio.s))
                        BotonSecundario(
                            texto = if (operacion.enCurso) "Confirmando…" else "Simular confirmación del pago",
                            habilitado = !operacion.enCurso,
                            onClick = { operacion.ejecutar<Any> { Repositorio.actual.confirmarPagoPendiente(pagoPendiente.id) } },
                        )
                        operacion.error?.let { Spacer(Modifier.height(Espacio.s)); Aviso(texto = it, tono = Tono.Error) }
                    }
                }
            } else {
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
                        when {
                            pagoPendiente != null -> "Total a pagar: ${Formato.importeMxn(orden.total)} · orden #${orden.id}"
                            orden.total > 0 -> "Total pagado: ${Formato.importeMxn(orden.total)} · orden #${orden.id}"
                            else -> "Sin cobro · orden #${orden.id}"
                        },
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
            if (n > 0) item {
                Text(
                    if (n == 1) "Tu boleto" else "Tus boletos · desliza para verlos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            item {
                // Carrusel horizontal: los N boletos se recorren deslizando, sin alargar la pantalla
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Espacio.m), contentPadding = PaddingValues(end = Espacio.l)) {
                    itemsIndexed(boletos, key = { _, b -> b.id }) { indice, boleto ->
                        TarjetaBoletoDeOrden(boleto = boleto, posicion = indice + 1, total = n, onClick = { onVerBoleto(boleto.id) })
                    }
                }
            }
        }
    }
}

/** Tarjeta de un boleto en el carrusel de la orden (ancho fijo, todas del mismo alto). */
@Composable
private fun TarjetaBoletoDeOrden(boleto: BoletoDetalle, posicion: Int, total: Int, onClick: () -> Unit) {
    TarjetaEnZona(onClick = onClick, relleno = PaddingValues(Espacio.m), modifier = Modifier.width(220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.QrCode2, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(Espacio.s))
            Text(
                "Boleto $posicion de $total",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Espacio.s))
        Text(
            listOfNotNull(boleto.nombreTipo, boleto.etiquetaAsiento).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
        )
        Text(
            boleto.codigo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Espacio.s))
        EtiquetaEstadoBoleto(boleto.estado)
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
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
                TarjetaBoletoDeOrden(Fixtures.boletoValido, 1, 3) {}
                TarjetaBoletoDeOrden(Fixtures.boletoUsado, 2, 3) {}
            }
            FilaBoletoDeOrden(Fixtures.boletoValido, 1, 3) {}
        }
    }
}
