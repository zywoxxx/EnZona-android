package com.uv.enzona.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.Tamanos
import com.uv.enzona.util.FechaEvento

/*
 * Portada del evento.
 *
 * Diferencia registrada: ni el esquema (`evento`) ni `EventoDto` tienen imagen
 * de portada, así que hoy `imagen` siempre es null. El sustituto es un panel
 * plano por categoría con la fecha como pieza tipográfica: información real del
 * evento, no decoración. Cuando el backend entregue una URL habrá que añadir un
 * cargador de imágenes (p. ej. Coil); este componente ya acepta `cargando` y
 * un `Painter` para no cambiar las pantallas.
 */

/** Tonos planos del sustituto de portada. Son fixtures visuales, no colores de marca. */
private data class TonoPortada(val fondo: Color, val tinta: Color, val icono: ImageVector)

// Paneles claros (tintes) con texto azul marino: legibles y coherentes con la paleta v2
private val tonosPorCategoria = mapOf(
    "música" to TonoPortada(Color(0xFFD7E1EE), Color(0xFF1F3A5F), Icons.Filled.MusicNote),
    "teatro" to TonoPortada(Color(0xFFFDE9E2), Color(0xFF8E2A1F), Icons.Filled.TheaterComedy),
    "deportes" to TonoPortada(Color(0xFFE3F0F6), Color(0xFF1B4F63), Icons.Filled.SportsSoccer),
    "deportivo" to TonoPortada(Color(0xFFE3F0F6), Color(0xFF1B4F63), Icons.Filled.SportsSoccer),
    "tecnología" to TonoPortada(Color(0xFFE6EBF3), Color(0xFF24708F), Icons.Filled.Memory),
    "académico" to TonoPortada(Color(0xFFE5F3EC), Color(0xFF1E7A45), Icons.Filled.School),
    "cultural" to TonoPortada(Color(0xFFFBF0D5), Color(0xFF1F3A5F), Icons.Filled.Celebration),
    "taller" to TonoPortada(Color(0xFFE6EBF3), Color(0xFF24708F), Icons.Filled.School),
    "gastronómico" to TonoPortada(Color(0xFFFDE9E2), Color(0xFF8E2A1F), Icons.Filled.Celebration),
)
private val tonoGenerico = TonoPortada(Color(0xFFD7E1EE), Color(0xFF1F3A5F), Icons.Filled.Celebration)

private fun tonoDe(categoria: String): TonoPortada =
    tonosPorCategoria[categoria.trim().lowercase()] ?: tonoGenerico

/**
 * Portada 16:9 con estado de carga, imagen real (si existe) y sustituto.
 *
 * @param fecha fecha y hora del evento; se usa para el bloque "25 sep".
 * @param mostrarFecha oculta el bloque de fecha cuando la pantalla ya la muestra grande.
 */
@Composable
fun PortadaEvento(
    nombre: String,
    categoria: String,
    fecha: java.time.LocalDateTime,
    modifier: Modifier = Modifier,
    imagen: Painter? = null,
    cargando: Boolean = false,
    forma: Shape = MaterialTheme.shapes.large,
    mostrarFecha: Boolean = true,
) {
    val tono = tonoDe(categoria)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(Tamanos.PROPORCION_PORTADA)
            .clip(forma)
            .background(if (imagen != null) MaterialTheme.colorScheme.surfaceContainerLow else tono.fondo)
            .semantics { contentDescription = "Portada de $nombre, categoría $categoria" },
    ) {
        when {
            cargando -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            imagen != null -> Image(
                painter = imagen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            else -> {
                // Marca de agua de la categoría, desplazada hacia la esquina inferior derecha
                Icon(
                    tono.icono,
                    contentDescription = null,
                    tint = tono.tinta.copy(alpha = 0.18f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(120.dp)
                        .offset(x = 18.dp, y = 22.dp),
                )
                Text(
                    categoria,
                    color = tono.tinta,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Espacio.m),
                )
            }
        }

        if (mostrarFecha) {
            BloqueFecha(fecha = fecha, modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Espacio.m))
        }
    }
}

/** Bloque "25 / sep" tipo calendario: la fecha como pieza tipográfica de la portada. */
@Composable
fun BloqueFecha(fecha: java.time.LocalDateTime, modifier: Modifier = Modifier) {
    val dia = FechaEvento.dia(fecha)
    val mes = FechaEvento.mesCorto(fecha)
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "$dia de $mes" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            dia,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            mes,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewPortada() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Espacio.m)) {
            PortadaEvento(nombre = "Concierto", categoria = "Música", fecha = java.time.LocalDateTime.of(2026, 10, 3, 19, 0))
            PortadaEvento(nombre = "Torneo", categoria = "Deportes", fecha = java.time.LocalDateTime.of(2026, 10, 18, 9, 0))
            PortadaEvento(nombre = "Cargando", categoria = "Teatro", fecha = java.time.LocalDateTime.of(2026, 10, 15, 18, 30), cargando = true)
        }
    }
}
