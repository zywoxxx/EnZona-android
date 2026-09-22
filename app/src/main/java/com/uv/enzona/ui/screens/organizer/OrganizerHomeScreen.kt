package com.uv.enzona.ui.screens.organizer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.Evento
import com.uv.enzona.ui.theme.AmarilloAcento
import com.uv.enzona.ui.theme.MoradoClaro
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk
import com.uv.enzona.util.FechaEvento

/** Panel del organizador: sus eventos con conteos (RF-17). */
@Composable
fun OrganizerHomeScreen(onNuevoEvento: () -> Unit, onEventoClick: (Long) -> Unit) {
    val organizadorId = SessionManager.usuarioId
    val eventos = MockRepository.eventosDe(organizadorId)

    val totalVendidos = eventos.sumOf { it.vendidos }
    val totalIngresos = eventos.sumOf { it.vendidos * it.precioDesde }
    val publicados = eventos.count { it.estado == EstadoEvento.PUBLICADO }

    Scaffold(
        containerColor = MoradoFondo,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNuevoEvento,
                containerColor = NaranjaAcento,
                contentColor = com.uv.enzona.ui.theme.SobreNaranja,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo evento") }
            )
        }
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("Mis eventos", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
                    Text(SessionManager.usuario?.nombre ?: "", color = TextoSecundario)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metrica("Publicados", "$publicados", Modifier.weight(1f))
                    Metrica("Asistentes", "$totalVendidos", Modifier.weight(1f))
                    Metrica("Ingresos", "$${"%.0f".format(totalIngresos)}", Modifier.weight(1f))
                }
            }
            items(eventos, key = { it.id }) { evento ->
                TarjetaEventoOrganizador(evento) { onEventoClick(evento.id) }
            }
            if (eventos.isEmpty()) {
                item {
                    Text(
                        "Aún no has creado eventos. Usa el botón «Nuevo evento» para publicar el primero.",
                        color = TextoSecundario, modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }
}

@Composable
private fun Metrica(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MoradoSuperficie)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(valor, color = NaranjaAcento, style = MaterialTheme.typography.titleLarge)
        Text(etiqueta, color = TextoSecundario, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TarjetaEventoOrganizador(evento: Evento, onClick: () -> Unit) {
    val ocupacion = if (evento.aforo > 0) evento.vendidos.toFloat() / evento.aforo else 0f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MoradoSuperficie)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    evento.nombre, color = TextoPrincipal, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                EtiquetaEstado(evento.estado)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${evento.lugar} · ${FechaEvento.corto(evento.fecha)}",
                color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ocupacion.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NaranjaAcento,
                trackColor = MoradoClaro,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${evento.vendidos} de ${evento.aforo} lugares · ${evento.disponibles} disponibles",
                color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun EtiquetaEstado(estado: EstadoEvento) {
    val (texto, color) = when (estado) {
        EstadoEvento.BORRADOR -> "Borrador" to TextoSecundario
        EstadoEvento.PUBLICADO -> "Publicado" to VerdeOk
        EstadoEvento.REALIZADO -> "Realizado" to AmarilloAcento
        EstadoEvento.CANCELADO -> "Cancelado" to RojoError
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(texto, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
