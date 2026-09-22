package com.uv.enzona.ui.screens.organizer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.LayoutSeccion
import com.uv.enzona.ui.components.Aviso
import com.uv.enzona.ui.components.BarraSuperiorEnZona
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.BotonSecundario
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.components.SelectorFechaHora
import com.uv.enzona.ui.components.SelectorUbicacionMapa
import com.uv.enzona.ui.components.TarjetaEnZona
import com.uv.enzona.ui.components.Tono
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.FechaEvento
import com.uv.enzona.util.FechaHoraEvento
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Nombres del catálogo `categoria` (tabla con id y nombre UNIQUE). */
private val CATEGORIAS: List<String> get() = MockRepository.categorias.map { it.nombre }

/**
 * Alta y edición de eventos (v3).
 * - RF-08: fecha y hora con selectores (calendario y hora en 24 h; nunca texto libre),
 *   ubicación exacta en mapa; publicar exige coordenadas, un borrador no.
 * - RF-09: asientos numerados.
 * - RF-13: edición con política de aforo (CP-15) y confirmación si cambia la fecha
 *   de un evento con boletos emitidos.
 */
@Composable
fun EventFormScreen(
    eventoId: Long?,           // null = evento nuevo
    onVolver: () -> Unit,
    onGuardado: () -> Unit,
) {
    val existente = eventoId?.let { id -> MockRepository.eventos.find { it.id == id } }
    val sinBoletos = eventoId == null || MockRepository.puedeCambiarAsientos(eventoId)
    val hoy = FechaEvento.ahora().toLocalDate()

    var nombre by rememberSaveable { mutableStateOf(existente?.nombre ?: "") }
    var descripcion by rememberSaveable { mutableStateOf(existente?.descripcion ?: "") }
    var lugar by rememberSaveable { mutableStateOf(existente?.lugar ?: "") }
    var direccion by rememberSaveable { mutableStateOf(existente?.direccion ?: "") }
    var categoria by rememberSaveable { mutableStateOf(existente?.categoria ?: CATEGORIAS.first()) }
    var esDePago by rememberSaveable { mutableStateOf(existente?.esDePago ?: false) }
    var precio by rememberSaveable { mutableStateOf(existente?.precioDesde?.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "") }
    var aforo by rememberSaveable { mutableStateOf(existente?.aforo?.toString() ?: "") }

    // --- Fecha y hora (cambios 4 y 5): valores temporales, nunca texto ---
    var fechaInicio by rememberSaveable { mutableStateOf(existente?.fecha?.toLocalDate()) }
    var horaInicio by rememberSaveable { mutableStateOf(existente?.fecha?.toLocalTime()) }
    var fechaFin by rememberSaveable { mutableStateOf(existente?.fechaFin?.toLocalDate()) }
    var horaFin by rememberSaveable { mutableStateOf(existente?.fechaFin?.toLocalTime()) }
    val inicio: LocalDateTime? = if (fechaInicio != null && horaInicio != null) FechaHoraEvento.combinar(fechaInicio!!, horaInicio!!) else null
    val fin: LocalDateTime? = when {
        fechaFin != null && horaFin != null -> FechaHoraEvento.combinar(fechaFin!!, horaFin!!)
        fechaFin != null -> FechaHoraEvento.combinar(fechaFin!!, LocalTime.of(23, 59))
        else -> null
    }
    val errorFecha = FechaHoraEvento.validar(inicio, fin, hoy)

    // --- Ubicación exacta (cambio 3): juntas o ninguna, nunca 0/0 ---
    var latitud by rememberSaveable { mutableStateOf(existente?.latitud) }
    var longitud by rememberSaveable { mutableStateOf(existente?.longitud) }

    // --- Asientos numerados (RF-09) ---
    val mapaActual = eventoId?.let { MockRepository.asientosDe(it) } ?: emptyList()
    var requiereAsiento by rememberSaveable { mutableStateOf(existente?.requiereAsiento ?: false) }
    var seccion by rememberSaveable { mutableStateOf(mapaActual.firstOrNull()?.seccion ?: "Luneta") }
    var filas by rememberSaveable { mutableStateOf(mapaActual.map { it.fila }.distinct().size.takeIf { it > 0 }?.toString() ?: "") }
    var porFila by rememberSaveable { mutableStateOf(mapaActual.groupBy { it.fila }.values.firstOrNull()?.size?.toString() ?: "") }

    var error by remember { mutableStateOf<String?>(null) }
    var confirmarCambioFecha by rememberSaveable { mutableStateOf(false) }

    val filasNum = filas.toIntOrNull() ?: 0
    val porFilaNum = porFila.toIntOrNull() ?: 0
    val aforoPorAsientos = filasNum * porFilaNum
    val aforoNum = if (requiereAsiento) aforoPorAsientos else (aforo.toIntOrNull() ?: 0)
    val precioNum = precio.toDoubleOrNull() ?: 0.0
    val asientosValidos = !requiereAsiento || (filasNum in 1..26 && porFilaNum in 1..40 && seccion.isNotBlank())
    val datosValidos = nombre.isNotBlank() && lugar.isNotBlank() && errorFecha == null &&
        aforoNum > 0 && (!esDePago || precioNum > 0) && asientosValidos
    val tieneUbicacion = latitud != null && longitud != null
    val puedePublicar = datosValidos && tieneUbicacion

    fun crear(publicar: Boolean) {
        runCatching {
            MockRepository.crearEvento(
                organizadorId = SessionManager.usuarioId,
                nombre = nombre.trim(), descripcion = descripcion.trim(), lugar = lugar.trim(), direccion = direccion.trim(),
                categoria = categoria, fecha = inicio!!, fechaFin = fin,
                esDePago = esDePago, precio = precioNum, aforo = aforoNum, publicar = publicar,
                requiereAsiento = requiereAsiento, filas = filasNum, asientosPorFila = porFilaNum, seccion = seccion.trim(),
                latitud = latitud, longitud = longitud,
            )
        }.onSuccess { onGuardado() }.onFailure { error = it.message }
    }

    fun guardarCambios() {
        val ev = existente ?: return
        MockRepository.modificarEvento(
            ev.id, nombre.trim(), descripcion.trim(), lugar.trim(), direccion.trim(), categoria, inicio!!, aforoNum,
            fechaFin = fin, latitud = latitud, longitud = longitud,
        ).onSuccess {
            if (sinBoletos) {
                if (requiereAsiento) MockRepository.activarAsientos(ev.id, listOf(LayoutSeccion(seccion.trim(), filasNum, porFilaNum)))
                else if (ev.requiereAsiento) MockRepository.desactivarAsientos(ev.id)
            }
            onGuardado()
        }.onFailure { error = it.message }
    }

    val fechaCambio = existente != null && inicio != null && inicio != existente.fecha
    if (confirmarCambioFecha && existente != null) {
        AlertDialog(
            onDismissRequest = { confirmarCambioFecha = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("¿Cambiar la fecha del evento?") },
            text = {
                Text(
                    "Este evento ya tiene ${existente.vendidos} boletos emitidos. La nueva fecha " +
                        "(${inicio?.let { FechaEvento.largo(it) } ?: ""}) afecta a esos asistentes; se les avisará del cambio (RF-13).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { confirmarCambioFecha = false; guardarCambios() }) { Text("Cambiar fecha y guardar") } },
            dismissButton = { TextButton(onClick = { confirmarCambioFecha = false }) { Text("Revisar") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = if (existente == null) "Nuevo evento" else "Editar evento", onVolver = onVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margen),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            CampoEnZona(nombre, { nombre = it; error = null }, "Nombre del evento")
            CampoEnZona(descripcion, { descripcion = it }, "Descripción", lineas = 3)

            // ------------------ Fecha y hora ------------------
            TarjetaEnZona {
                Text("Fecha y hora", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Espacio.s))
                SelectorFechaHora(
                    etiquetaFecha = "Fecha de inicio", etiquetaHora = "Hora",
                    fecha = fechaInicio, hora = horaInicio, minima = hoy,
                    onFecha = { fechaInicio = it; error = null }, onHora = { horaInicio = it; error = null },
                    error = if (inicio != null || fechaInicio != null || horaInicio != null) errorFecha?.takeIf { fin == null || !it.contains("fin") } else null,
                )
                Spacer(Modifier.height(Espacio.m))
                SelectorFechaHora(
                    etiquetaFecha = "Fecha de fin", etiquetaHora = "Hora de fin",
                    fecha = fechaFin, hora = horaFin, minima = fechaInicio ?: hoy,
                    onFecha = { fechaFin = it; error = null }, onHora = { horaFin = it; error = null },
                    opcional = true,
                    error = errorFecha?.takeIf { it.contains("fin") },
                )
                if (fechaCambio && existente.vendidos > 0) {
                    Spacer(Modifier.height(Espacio.s))
                    Aviso(texto = "Cambiar la fecha afecta a ${existente.vendidos} asistentes con boleto; se pedirá confirmación al guardar.", tono = Tono.Aviso)
                }
            }

            // ------------------ Ubicación ------------------
            TarjetaEnZona {
                Text("Ubicación", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Espacio.s))
                CampoEnZona(lugar, { lugar = it }, "Lugar (nombre del recinto)")
                Spacer(Modifier.height(Espacio.s))
                CampoEnZona(direccion, { direccion = it }, "Dirección", ayuda = "Puedes editar la dirección sugerida por el mapa.")
                Spacer(Modifier.height(Espacio.m))
                SelectorUbicacionMapa(
                    latitud = latitud, longitud = longitud,
                    onCambio = { lat, lon -> latitud = lat; longitud = lon; error = null },
                    onSugerenciaDireccion = { sugerida -> if (direccion.isBlank()) direccion = sugerida },
                )
                Spacer(Modifier.height(Espacio.s))
                Text(
                    if (tieneUbicacion) "Ubicación fijada. Los asistentes verán el mapa y «Cómo llegar»."
                    else "Para publicar hace falta fijar la ubicación en el mapa. Un borrador puede guardarse sin ella.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tieneUbicacion) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }

            // ------------------ Categoría ------------------
            Text("Categoría", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                CATEGORIAS.forEach { cat ->
                    FilterChip(selected = categoria == cat, onClick = { categoria = cat }, label = { Text(cat) })
                }
            }

            // ------------------ Cobro ------------------
            TarjetaEnZona {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Evento de pago", color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (esDePago) "Se venderán boletos" else "Solo confirmación de asistencia",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = esDePago, onCheckedChange = { esDePago = it }, enabled = existente == null)
                }
                if (esDePago) {
                    Spacer(Modifier.height(Espacio.m))
                    CampoEnZona(
                        precio, { precio = it }, "Precio del boleto (MXN)",
                        teclado = KeyboardType.Number, ayuda = "Precio final, sin cargos ocultos (Profeco).",
                    )
                }
            }

            // ------------------ Asientos numerados (RF-09) ------------------
            TarjetaEnZona {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Filled.EventSeat, contentDescription = null,
                            tint = if (requiereAsiento) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.padding(start = Espacio.m)) {
                            Text("¿Este evento requiere asientos?", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (requiereAsiento) "El asistente elegirá sus butacas en el mapa" else "Entrada libre: solo se cuenta el aforo",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Switch(checked = requiereAsiento, onCheckedChange = { requiereAsiento = it; error = null }, enabled = sinBoletos)
                }
                if (!sinBoletos) {
                    Spacer(Modifier.height(Espacio.s))
                    Aviso(texto = "No se puede cambiar el plano: este evento ya tiene boletos emitidos.", tono = Tono.Aviso)
                }
                if (requiereAsiento) {
                    Spacer(Modifier.height(Espacio.m))
                    CampoEnZona(seccion, { seccion = it }, "Nombre de la sección", ayuda = "Ejemplo: Luneta, Planta baja, Balcón")
                    Spacer(Modifier.height(Espacio.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                        Column(Modifier.weight(1f)) { CampoEnZona(filas, { filas = it }, "Filas", teclado = KeyboardType.Number) }
                        Column(Modifier.weight(1f)) { CampoEnZona(porFila, { porFila = it }, "Asientos por fila", teclado = KeyboardType.Number) }
                    }
                    Spacer(Modifier.height(Espacio.s))
                    Text(
                        when {
                            filasNum > 26 -> "Máximo 26 filas (A–Z)."
                            aforoPorAsientos > 0 -> "Se generarán $aforoPorAsientos butacas (filas A–${('A' + (filasNum - 1).coerceIn(0, 25))}). El aforo se ajusta a ese número."
                            else -> "Indica cuántas filas y cuántos asientos tiene cada fila."
                        },
                        color = if (filasNum > 26) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!requiereAsiento) {
                CampoEnZona(
                    aforo, { aforo = it; error = null }, "Aforo (lugares totales)",
                    teclado = KeyboardType.Number,
                    ayuda = existente?.let { "Ya hay ${it.vendidos} boletos entregados; el aforo no puede ser menor." },
                )
            }

            if (error != null) Aviso(texto = error!!, tono = Tono.Error)

            Spacer(Modifier.height(Espacio.s))
            if (existente == null) {
                BotonEnZona(texto = "Crear y publicar", habilitado = puedePublicar, destacado = true, onClick = { crear(true) })
                if (datosValidos && !tieneUbicacion) {
                    Text(
                        "Publicar está bloqueado hasta fijar la ubicación en el mapa.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                BotonSecundario(texto = "Guardar como borrador", habilitado = datosValidos, onClick = { crear(false) })
            } else {
                BotonEnZona(
                    texto = "Guardar cambios",
                    habilitado = datosValidos && (existente.estado.name != "PUBLICADO" || tieneUbicacion),
                    destacado = true,
                    onClick = { if (fechaCambio && existente.vendidos > 0) confirmarCambioFecha = true else guardarCambios() },
                )
            }
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}
