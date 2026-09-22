package com.uv.enzona.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.FiltroFecha
import com.uv.enzona.data.FiltroPrecio
import com.uv.enzona.data.FiltrosEventos
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.model.Evento
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.components.EstadoVacio
import com.uv.enzona.ui.components.Etiqueta
import com.uv.enzona.ui.components.EtiquetaAgotado
import com.uv.enzona.ui.components.PortadaEvento
import com.uv.enzona.ui.components.TarjetaEnZona
import com.uv.enzona.ui.components.Tono
import com.uv.enzona.ui.preview.Fixtures
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.Tamanos
import com.uv.enzona.util.Formato
import com.uv.enzona.util.FechaEvento

/*
 * Inicio: descubrimiento de eventos (RF-05, RF-06, RF-07).
 *
 * Los filtros viven en `rememberSaveable`, de modo que al abrir un evento y
 * volver, la búsqueda y los filtros siguen ahí. La zona elegida se muestra
 * arriba y se cambia a mano: el descubrimiento no exige GPS.
 */

private val filtrosSaver = Saver<FiltrosEventos, List<String>>(
    save = { listOf(it.busqueda, it.categoria ?: "", it.lugar ?: "", it.precio.name, it.fecha.name) },
    restore = {
        FiltrosEventos(
            busqueda = it[0],
            categoria = it[1].ifEmpty { null },
            lugar = it[2].ifEmpty { null },
            precio = FiltroPrecio.valueOf(it[3]),
            fecha = FiltroFecha.valueOf(it[4]),
        )
    },
)

@Composable
fun HomeScreen(onEventoClick: (Long) -> Unit) {
    var filtros by rememberSaveable(stateSaver = filtrosSaver) { mutableStateOf(FiltrosEventos()) }
    var cambiandoZona by rememberSaveable { mutableStateOf(false) }
    val estadoLista = rememberLazyListState()

    val zona = MockRepository.ciudad.value
    val ciudadZona = zona.substringBefore(",").trim()
    // RF-19/RNF-11 (parcial): la ciudad elegida a mano filtra por `evento.ciudad` (columna v2).
    // No hay GPS ni orden por cercanía todavía; ver notas técnicas.
    val publicados = MockRepository.eventosPublicados().filter { it.ciudad.equals(ciudadZona, ignoreCase = true) }
    val categorias = publicados.map { it.categoria }.distinct().sorted()
    val lugares = publicados.map { it.lugar }.distinct().sorted()
    val eventos = filtros.aplicar(publicados)

    if (cambiandoZona) {
        DialogoZona(
            zonaActual = zona,
            onCerrar = { cambiandoZona = false },
            onElegir = { nueva ->
                MockRepository.ciudad.value = nueva
                cambiandoZona = false
            },
        )
    }

    LazyColumn(
        state = estadoLista,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Espacio.margen, end = Espacio.margen, top = Espacio.m, bottom = Espacio.xl),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item(key = "encabezado") {
            Encabezado(zona = zona, onCambiarZona = { cambiandoZona = true })
        }
        item(key = "busqueda") {
            CampoBusqueda(
                valor = filtros.busqueda,
                onCambio = { filtros = filtros.copy(busqueda = it) },
            )
        }
        item(key = "filtros") {
            FilaFiltros(
                filtros = filtros,
                categorias = categorias,
                lugares = lugares,
                onCambio = { filtros = it },
            )
        }
        item(key = "resumen") {
            ResumenFiltros(
                total = eventos.size,
                filtros = filtros,
                onLimpiar = { filtros = filtros.limpiar() },
            )
        }
        items(eventos, key = { it.id }) { evento ->
            TarjetaEvento(evento = evento, onClick = { onEventoClick(evento.id) })
        }
        if (eventos.isEmpty()) {
            item(key = "vacio") {
                EstadoVacio(
                    icono = Icons.Filled.SearchOff,
                    titulo = if (publicados.isEmpty()) "No hay eventos publicados en $ciudadZona" else "Nada coincide con tu búsqueda",
                    texto = if (publicados.isEmpty()) "El área de estudio de EnZona es Orizaba. Puedes explorar sus eventos o cambiar de zona más tarde."
                    else "Prueba con otra palabra o quita algún filtro.",
                    textoAccion = when {
                        publicados.isEmpty() && !ciudadZona.equals("Orizaba", true) -> "Explorar Orizaba"
                        filtros.hayAlgo -> "Limpiar búsqueda y filtros"
                        else -> null
                    },
                    onAccion = {
                        if (publicados.isEmpty() && !ciudadZona.equals("Orizaba", true)) MockRepository.ciudad.value = "Orizaba, Veracruz"
                        else filtros = filtros.limpiar()
                    },
                )
            }
        }
    }
}

// ==================================================================
//  Encabezado y zona
// ==================================================================

@Composable
private fun Encabezado(zona: String, onCambiarZona: () -> Unit) {
    Column {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.uv.enzona.R.drawable.enzona_logo),
            contentDescription = "EnZona",
            modifier = Modifier.height(40.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            alignment = Alignment.CenterStart,
        )
        Spacer(Modifier.height(Espacio.xs))
        Text(
            "Eventos cerca de ti",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(Espacio.xs))
        TextButton(
            onClick = onCambiarZona,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            modifier = Modifier
                .heightIn(min = Tamanos.control)
                .semantics { contentDescription = "Zona: $zona. Cambiar zona" },
        ) {
            Icon(
                Icons.Filled.Place, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = Espacio.xs)
            )
            Text(zona, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Zonas de muestra: FIXTURE de demostración, no un catálogo real. */
private val zonasDeMuestra = listOf(
    "Orizaba, Veracruz",
    "Córdoba, Veracruz",
    "Fortín, Veracruz",
    "Xalapa, Veracruz",
    "Veracruz, Veracruz",
)

@Composable
private fun DialogoZona(zonaActual: String, onCerrar: () -> Unit, onElegir: (String) -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("¿En qué zona buscas?") },
        text = {
            Column {
                Text(
                    "Elige una zona o escribe la tuya. No hace falta activar la ubicación del teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Espacio.m))
                zonasDeMuestra.forEach { z ->
                    TextButton(
                        onClick = { onElegir(z) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Tamanos.control),
                        contentPadding = PaddingValues(horizontal = Espacio.s),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                z,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (z == zonaActual) {
                                Icon(Icons.Filled.Check, contentDescription = "Zona actual", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Espacio.s))
                CampoEnZona(valor = texto, onCambio = { texto = it }, etiqueta = "Otra ciudad o colonia")
                Spacer(Modifier.height(Espacio.xs))
                Text(
                    "El área de estudio es Orizaba. Si eliges otra ciudad y no hay eventos, podrás volver a Orizaba desde aquí.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onElegir(texto.trim()) }, enabled = texto.isNotBlank()) { Text("Usar esta zona") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ==================================================================
//  Búsqueda y filtros
// ==================================================================

@Composable
private fun CampoBusqueda(valor: String, onCambio: (String) -> Unit) {
    val colores = MaterialTheme.colorScheme
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar evento o lugar") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (valor.isNotEmpty()) {
                IconButton(onClick = { onCambio("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda")
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colores.primary,
            unfocusedBorderColor = colores.outline,
            focusedTextColor = colores.onSurface,
            unfocusedTextColor = colores.onSurface,
            cursorColor = colores.primary,
            focusedContainerColor = colores.surfaceContainer,
            unfocusedContainerColor = colores.surfaceContainer,
            focusedPlaceholderColor = colores.onSurfaceVariant,
            unfocusedPlaceholderColor = colores.onSurfaceVariant,
            focusedLeadingIconColor = colores.primary,
            unfocusedLeadingIconColor = colores.onSurfaceVariant,
            focusedTrailingIconColor = colores.onSurfaceVariant,
            unfocusedTrailingIconColor = colores.onSurfaceVariant,
        ),
    )
}

/**
 * Chips de filtro (RF-07): Gratis, fecha, lugar y tipo. Los desplegables
 * muestran la opción activa en el propio chip para que el filtro sea visible.
 */
@Composable
private fun FilaFiltros(
    filtros: FiltrosEventos,
    categorias: List<String>,
    lugares: List<String>,
    onCambio: (FiltrosEventos) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        item {
            ChipFiltro(
                texto = "Gratis",
                activo = filtros.precio == FiltroPrecio.GRATIS,
                onClick = {
                    onCambio(filtros.copy(precio = if (filtros.precio == FiltroPrecio.GRATIS) FiltroPrecio.TODOS else FiltroPrecio.GRATIS))
                },
            )
        }
        item {
            ChipDesplegable(
                etiqueta = "Fecha",
                valor = filtros.fecha.takeIf { it != FiltroFecha.CUALQUIERA }?.etiqueta,
                opciones = FiltroFecha.entries.map { it.etiqueta },
                onElegir = { elegido -> onCambio(filtros.copy(fecha = FiltroFecha.entries.first { it.etiqueta == elegido })) },
            )
        }
        item {
            ChipDesplegable(
                etiqueta = "Lugar",
                valor = filtros.lugar,
                opciones = listOf("Cualquier lugar") + lugares,
                onElegir = { elegido -> onCambio(filtros.copy(lugar = elegido.takeIf { it != "Cualquier lugar" })) },
            )
        }
        item {
            ChipDesplegable(
                etiqueta = "Tipo",
                valor = filtros.categoria,
                opciones = listOf("Todos los tipos") + categorias,
                onElegir = { elegido -> onCambio(filtros.copy(categoria = elegido.takeIf { it != "Todos los tipos" })) },
            )
        }
        item {
            ChipFiltro(
                texto = "De pago",
                activo = filtros.precio == FiltroPrecio.DE_PAGO,
                onClick = {
                    onCambio(filtros.copy(precio = if (filtros.precio == FiltroPrecio.DE_PAGO) FiltroPrecio.TODOS else FiltroPrecio.DE_PAGO))
                },
            )
        }
    }
}

@Composable
private fun ChipFiltro(texto: String, activo: Boolean, onClick: () -> Unit, iconoFinal: (@Composable () -> Unit)? = null) {
    val colores = MaterialTheme.colorScheme
    FilterChip(
        selected = activo,
        onClick = onClick,
        label = { Text(texto, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (activo) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.width(16.dp)) }
        } else null,
        trailingIcon = iconoFinal,
        modifier = Modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colores.surfaceContainer,
            labelColor = colores.onSurface,
            iconColor = colores.onSurfaceVariant,
            selectedContainerColor = colores.primaryContainer,
            selectedLabelColor = colores.onPrimaryContainer,
            selectedLeadingIconColor = colores.onPrimaryContainer,
            selectedTrailingIconColor = colores.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = activo,
            borderColor = colores.outline, selectedBorderColor = colores.primary,
        ),
    )
}

@Composable
private fun ChipDesplegable(
    etiqueta: String,
    valor: String?,
    opciones: List<String>,
    onElegir: (String) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        ChipFiltro(
            texto = valor ?: etiqueta,
            activo = valor != null,
            onClick = { abierto = true },
            iconoFinal = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir opciones de $etiqueta") },
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    trailingIcon = if (opcion == valor) {
                        { Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = { onElegir(opcion); abierto = false },
                )
            }
        }
    }
}

@Composable
private fun ResumenFiltros(total: Int, filtros: FiltrosEventos, onLimpiar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                total == 1 -> "1 evento"
                filtros.hayAlgo -> "$total eventos encontrados"
                else -> "$total eventos publicados"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (filtros.hayAlgo) {
            TextButton(onClick = onLimpiar, contentPadding = PaddingValues(horizontal = Espacio.s)) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(Espacio.xs))
                Text(
                    if (filtros.activos > 0) "Limpiar filtros (${filtros.activos})" else "Limpiar búsqueda",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ==================================================================
//  Tarjeta de evento
// ==================================================================

/** Tarjeta de evento: portada, nombre, fecha, lugar y precio (o "Gratis"). */
@Composable
fun TarjetaEvento(evento: Evento, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TarjetaEnZona(modifier = modifier, onClick = onClick, relleno = PaddingValues(0.dp)) {
        PortadaEvento(
            nombre = evento.nombre,
            categoria = evento.categoria,
            fecha = evento.fecha,
            forma = MaterialTheme.shapes.large.copy(
                bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
            ),
        )
        Column(Modifier.padding(Espacio.l)) {
            Text(
                evento.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Espacio.s))
            FilaCompacta(Icons.Filled.CalendarMonth, FechaEvento.corto(evento.fecha))
            Spacer(Modifier.height(Espacio.xs))
            FilaCompacta(Icons.Filled.LocationOn, evento.lugar)
            Spacer(Modifier.height(Espacio.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (evento.esDePago) {
                    Text(
                        Formato.etiquetaPrecio(true, evento.precioDesde),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Etiqueta("Gratis", tono = Tono.Destacado)
                }
                if (evento.agotado) {
                    EtiquetaAgotado()
                } else {
                    Text(
                        Formato.lugares(evento.disponibles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaCompacta(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icono, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp)
        )
        Spacer(Modifier.width(Espacio.s))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ==================================================================
//  Previews
// ==================================================================

@Preview(name = "Tarjetas de evento", showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewTarjetas() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            TarjetaEvento(Fixtures.concierto, onClick = {})
            TarjetaEvento(Fixtures.feriaGratis, onClick = {})
            TarjetaEvento(Fixtures.torneoAgotado, onClick = {})
        }
    }
}

@Preview(name = "Tarjeta en pantalla angosta", showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 300, fontScale = 1.3f)
@Composable
private fun PreviewTarjetaAngosta() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l)) {
            TarjetaEvento(Fixtures.feriaGratis, onClick = {})
        }
    }
}
