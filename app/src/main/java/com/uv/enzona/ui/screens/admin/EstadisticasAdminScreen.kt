package com.uv.enzona.ui.screens.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.model.EstadisticasAdmin
import com.uv.enzona.data.model.ParDato
import com.uv.enzona.ui.components.ContenidoCarga
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.AzulProfundo
import com.uv.enzona.ui.theme.MoradoClaro
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import com.uv.enzona.util.Formato

/**
 * Pestaña «Resumen» de administración (RF-18): indicadores de la plataforma y
 * gráficas de eventos, ocupación e ingresos. Los datos salen de
 * `RepositorioEnZona.estadisticasAdmin()`; aquí solo se pintan.
 *
 * Las gráficas se dibujan con Compose (sin bibliotecas externas) y cada una
 * lleva una descripción con sus valores para el lector de pantalla.
 */
@Composable
fun PestanaEstadisticas() {
    val carga = rememberCarga { Repositorio.actual.estadisticasAdmin() }
    ContenidoCarga(carga, textoCargando = "Calculando estadísticas…") { e ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "indicadores") { Indicadores(e) }
            item(key = "cuentas") { TarjetaCuentas(e) }
            item(key = "estados") { TarjetaEventosPorEstado(e) }
            item(key = "categorias") {
                TarjetaSeccion("Eventos por categoría") {
                    if (e.eventosPorCategoria.isEmpty()) Vacio("Todavía no hay eventos.")
                    else BarrasHorizontales(e.eventosPorCategoria, AzulProfundo) { it.toInt().toString() }
                }
            }
            item(key = "meses") {
                TarjetaSeccion("Eventos por mes de inicio") {
                    if (e.eventosPorMes.isEmpty()) Vacio("Todavía no hay eventos.")
                    else BarrasVerticales(e.eventosPorMes, NaranjaAcento)
                }
            }
            item(key = "ingresos") { TarjetaIngresos(e) }
            item(key = "ocupacion") {
                TarjetaSeccion("Ocupación de los eventos publicados") {
                    if (e.ocupacionPorEvento.isEmpty()) Vacio("No hay eventos publicados.")
                    else BarrasHorizontales(e.ocupacionPorEvento, VerdeOk) { "${it.toInt()} %" }
                }
            }
        }
    }
}

// ==================================================================
//  Secciones
// ==================================================================

@Composable
private fun Indicadores(e: EstadisticasAdmin) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Indicador("Usuarios", e.usuariosTotal.toString(), Modifier.weight(1f))
            Indicador("Eventos", e.eventosTotal.toString(), Modifier.weight(1f))
            Indicador("Ingresos", Formato.importe(e.ingresosTotales), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Indicador("Boletos emitidos", e.boletosEmitidos.toString(), Modifier.weight(1f))
            Indicador("Accesos validados", e.accesosValidados.toString(), Modifier.weight(1f))
            Indicador("Ocupación", "${e.ocupacionGlobal.toInt()} %", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TarjetaCuentas(e: EstadisticasAdmin) {
    TarjetaSeccion("Cuentas") {
        BarraSegmentada(
            listOf(
                Segmento("Activas", e.usuariosActivos, VerdeOk),
                Segmento("Pendientes", e.usuariosPendientes, AmarilloAcento),
                Segmento("Bloqueadas", e.usuariosBloqueados, RojoError),
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Organizadores ${e.organizadores} · Validadores ${e.validadores} · Administradores ${e.administradores}",
            color = TextoSecundario, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TarjetaEventosPorEstado(e: EstadisticasAdmin) {
    TarjetaSeccion("Eventos por estado") {
        BarrasHorizontales(
            listOf(
                ParDato("Publicados", e.eventosPublicados.toDouble()),
                ParDato("Borradores", e.eventosBorrador.toDouble()),
                ParDato("Realizados", e.eventosRealizados.toDouble()),
                ParDato("Cancelados", e.eventosCancelados.toDouble()),
            ),
            NaranjaAcento,
        ) { it.toInt().toString() }
        Spacer(Modifier.height(8.dp))
        Text(
            "Gratuitos ${e.eventosGratuitos} · De pago ${e.eventosDePago}",
            color = TextoSecundario, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TarjetaIngresos(e: EstadisticasAdmin) {
    TarjetaSeccion("Ingresos por evento") {
        if (e.ingresosPorEvento.isEmpty()) {
            Vacio("Aún no hay ventas registradas.")
        } else {
            BarrasHorizontales(e.ingresosPorEvento, NaranjaAcento) { Formato.importe(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("Total ${Formato.importeMxn(e.ingresosTotales)}")
                if (e.reembolsado > 0) append(" · Reembolsado ${Formato.importeMxn(e.reembolsado)} · Neto ${Formato.importeMxn(e.ingresosNetos)}")
                if (e.boletosCancelados > 0) append(" · ${e.boletosCancelados} boletos cancelados")
                if (e.boletosEmitidos > 0) append(" · Asistencia ${e.asistencia.toInt()} % de los boletos emitidos")
            },
            color = TextoSecundario, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ==================================================================
//  Piezas
// ==================================================================

@Composable
private fun Indicador(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MoradoSuperficie)
            .padding(vertical = 14.dp, horizontal = 8.dp)
            .semantics { contentDescription = "$etiqueta: $valor" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(valor, color = NaranjaAcento, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(etiqueta, color = TextoSecundario, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TarjetaSeccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MoradoSuperficie)
            .padding(14.dp),
    ) {
        Text(titulo, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        contenido()
    }
}

@Composable
private fun Vacio(texto: String) {
    Text(texto, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
}

/** Barras horizontales proporcionales al valor mayor, con su etiqueta y su valor. */
@Composable
private fun BarrasHorizontales(datos: List<ParDato>, color: Color, formato: (Double) -> String) {
    val mayor = datos.maxOf { it.valor }.coerceAtLeast(1.0)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { contentDescription = datos.joinToString { "${it.etiqueta}: ${formato(it.valor)}" } },
    ) {
        datos.forEach { dato ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dato.etiqueta, color = TextoPrincipal, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1.3f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MoradoClaro),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((dato.valor / mayor).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(color),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formato(dato.valor), color = TextoSecundario, style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End, modifier = Modifier.width(72.dp),
                )
            }
        }
    }
}

/** Barras verticales dibujadas con Canvas; los valores y las etiquetas van en filas alineadas. */
@Composable
private fun BarrasVerticales(datos: List<ParDato>, color: Color) {
    val mayor = datos.maxOf { it.valor }.coerceAtLeast(1.0)
    Column(Modifier.semantics { contentDescription = datos.joinToString { "${it.etiqueta}: ${it.valor.toInt()}" } }) {
        Row(Modifier.fillMaxWidth()) {
            datos.forEach {
                Text(it.valor.toInt().toString(), color = TextoSecundario, style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val ancho = size.width / datos.size
            datos.forEachIndexed { i, dato ->
                val alto = (dato.valor / mayor * size.height).toFloat()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * ancho + ancho * 0.22f, size.height - alto),
                    size = Size(ancho * 0.56f, alto),
                    cornerRadius = CornerRadius(10f, 10f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            datos.forEach {
                Text(it.etiqueta, color = TextoSecundario, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class Segmento(val etiqueta: String, val cantidad: Int, val color: Color)

/** Una barra dividida en proporción (cuentas activas, pendientes y bloqueadas) con su leyenda. */
@Composable
private fun BarraSegmentada(segmentos: List<Segmento>) {
    val total = segmentos.sumOf { it.cantidad }
    Column(Modifier.semantics { contentDescription = segmentos.joinToString { "${it.etiqueta}: ${it.cantidad}" } }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MoradoClaro),
        ) {
            segmentos.filter { it.cantidad > 0 }.forEach { s ->
                Box(
                    Modifier
                        .weight(s.cantidad.toFloat())
                        .fillMaxHeight()
                        .background(s.color),
                )
            }
            if (total == 0) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            segmentos.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(4.dp))
                    Text("${s.etiqueta} ${s.cantidad}", color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
