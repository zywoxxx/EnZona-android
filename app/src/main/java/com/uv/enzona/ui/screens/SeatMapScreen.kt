package com.uv.enzona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.EstadoAsiento
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.ui.components.Aviso
import com.uv.enzona.ui.components.BarraSuperiorEnZona
import com.uv.enzona.ui.components.BotonSecundario
import com.uv.enzona.ui.components.SelectorDesplegable
import com.uv.enzona.ui.components.Tono
import com.uv.enzona.ui.preview.Fixtures
import com.uv.enzona.ui.theme.EnZonaTema
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.Tamanos
import com.uv.enzona.util.Formato
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import com.uv.enzona.util.FechaEvento

/**
 * Selección de asientos (RF-09, ampliación de compra múltiple).
 *
 * Dibuja el mapa de butacas del evento a partir de la tabla `asiento`
 * conservando el plano existente (escenario, secciones, curvatura y pasillo).
 * La selección es un CONJUNTO de N asientos distintos: pulsar una butaca libre
 * la añade, pulsar una seleccionada la quita; nunca se reemplazan en silencio.
 * No se puede continuar con menos o más asientos que la cantidad elegida.
 *
 * Elegir en pantalla NO reserva: la reserva ocurre al confirmar (gratuito) o
 * al aprobarse el pago, dentro de la misma operación que emite los boletos.
 *
 * Contrato pendiente: el esquema no asocia `tipo_boleto` con secciones, así
 * que cualquier asiento libre del evento es elegible para cualquier tarifa.
 */
private const val TAMANO_MIN = 22
private const val TAMANO_INICIAL = 30
private const val TAMANO_MAX = 46
private const val PASO_ZOOM = 6

/** Guarda la selección (lista de ids) al recrear la pantalla o volver desde el pago. */
private val saverSeleccion = Saver<List<Long>, String>(
    save = { it.joinToString("-") },
    restore = { s -> s.split("-").mapNotNull { it.toLongOrNull() } },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeatMapScreen(
    eventoId: Long,
    tipoBoletoId: Long?,
    cantidad: Int,
    onVolver: () -> Unit,
    onBoletoEmitido: (Long) -> Unit,
    onIrAPago: (eventoId: Long, tipoId: Long?, cantidad: Int, asientosIds: List<Long>) -> Unit,
) {
    val evento = MockRepository.eventos.find { it.id == eventoId } ?: return
    val asientos = MockRepository.asientos.filter { it.eventoId == eventoId }
    val tipo = tipoBoletoId?.let { id -> MockRepository.tiposBoleto.find { it.id == id } }
    val objetivo = cantidad.coerceAtLeast(1)

    var seleccionados by rememberSaveable(stateSaver = saverSeleccion) { mutableStateOf(emptyList<Long>()) }
    var avisoLimite by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tamano by rememberSaveable { mutableIntStateOf(TAMANO_INICIAL) }
    var eligiendoPorNumero by rememberSaveable { mutableStateOf(false) }
    var confirmando by rememberSaveable { mutableStateOf(false) }
    var procesando by remember { mutableStateOf(false) }
    val claveOperacion = rememberSaveable { UUID.randomUUID().toString() }
    val alcance = rememberCoroutineScope()

    // Revalidación: si un asiento elegido dejó de estar libre, se señala y no se continúa
    val elegidos = seleccionados.mapNotNull { id -> asientos.find { it.id == id } }
    val yaNoLibres = elegidos.filter { !it.libre }
    val listos = elegidos.size == objetivo && yaNoLibres.isEmpty()
    val faltan = objetivo - elegidos.size
    val libres = asientos.count { it.libre }
    val precioUnitario = if (evento.esDePago) (tipo?.precio ?: evento.precioDesde) else 0.0
    val total = precioUnitario * objetivo

    fun alternar(id: Long) {
        avisoLimite = null
        error = null
        seleccionados = when {
            id in seleccionados -> seleccionados - id
            seleccionados.size >= objetivo -> {
                avisoLimite = if (objetivo == 1) "Ya elegiste tu asiento. Quítalo para cambiarlo por otro."
                else "Ya elegiste $objetivo de $objetivo. Quita uno para cambiarlo."
                seleccionados
            }
            else -> seleccionados + id
        }
    }

    if (eligiendoPorNumero) {
        DialogoElegirPorNumero(
            asientos = asientos,
            excluidos = seleccionados,
            onCerrar = { eligiendoPorNumero = false },
            onElegir = { id -> alternar(id); eligiendoPorNumero = false },
        )
    }

    if (confirmando && listos) {
        AlertDialog(
            onDismissRequest = { if (!procesando) confirmando = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Confirmar asistencia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    Text(evento.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(FechaEvento.corto(evento.fecha), style = MaterialTheme.typography.bodyLarge)
                    elegidos.forEach { Text(it.etiquetaLarga, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                    Text(
                        "Se apartará este asiento a tu nombre y recibirás tu boleto QR. Este evento es gratuito: no hay ningún cobro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !procesando,
                    onClick = {
                        if (procesando) return@TextButton
                        procesando = true
                        alcance.launch {
                            delay(500)
                            MockRepository.comprar(
                                SessionManager.usuarioId,
                                SolicitudCompra(eventoId, tipo?.id, objetivo, seleccionados, claveOperacion),
                            ).onSuccess { compra ->
                                procesando = false
                                confirmando = false
                                onBoletoEmitido(compra.boletos.first().id)
                            }.onFailure { fallo ->
                                procesando = false
                                confirmando = false
                                error = fallo.message
                            }
                        }
                    },
                ) { Text(if (procesando) "Confirmando…" else "Confirmar asiento") }
            },
            dismissButton = { TextButton(onClick = { confirmando = false }, enabled = !procesando) { Text("Cancelar") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = if (objetivo == 1) "Elige tu asiento" else "Elige $objetivo asientos", onVolver = onVolver) },
        bottomBar = {
            BarraAccion(
                textoBoton = when {
                    !evento.esDePago -> "Confirmar asiento"
                    objetivo == 1 -> "Continuar al pago"
                    else -> "Continuar al pago · $objetivo boletos"
                },
                resumen = when {
                    yaNoLibres.isNotEmpty() -> "Quita ${if (yaNoLibres.size == 1) "el asiento ocupado" else "los asientos ocupados"} para continuar"
                    faltan > 0 && elegidos.isEmpty() -> if (objetivo == 1) "Toca un asiento libre para elegirlo" else "Toca $objetivo asientos libres para elegirlos"
                    faltan > 0 -> "Faltan $faltan de $objetivo · ${Formato.importeMxn(total)}"
                    evento.esDePago -> "${elegidos.size} de $objetivo · ${Formato.importeMxn(total)} · total final"
                    else -> "${elegidos.joinToString { it.etiquetaCorta }} · Gratis"
                },
                habilitado = listos && !procesando,
                aviso = (error ?: avisoLimite)?.let { mensaje ->
                    {
                        Aviso(
                            texto = mensaje,
                            tono = if (error != null) Tono.Error else Tono.Aviso,
                            titulo = if (error != null) "La disponibilidad cambió" else null,
                        )
                    }
                },
                onClick = {
                    if (!listos) return@BarraAccion
                    if (evento.esDePago) onIrAPago(eventoId, tipo?.id, objetivo, seleccionados)
                    else confirmando = true
                },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(horizontal = Espacio.margen)) {
                Text(evento.nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "$libres asientos libres de ${asientos.size}" + (tipo?.let { " · ${it.nombre}" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Espacio.m))

                // ---- Seleccionados X de N ----
                Text(
                    "Seleccionados ${elegidos.size} de $objetivo",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { contentDescription = "Seleccionados ${elegidos.size} de $objetivo asientos" },
                )
                if (elegidos.isNotEmpty()) {
                    Spacer(Modifier.height(Espacio.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                        elegidos.forEach { asiento ->
                            val ocupado = !asiento.libre
                            InputChip(
                                selected = !ocupado,
                                onClick = { alternar(asiento.id) },
                                label = { Text(if (ocupado) "${asiento.etiquetaCorta} · ocupado" else asiento.etiquetaLarga) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Quitar ${asiento.etiquetaLarga}", modifier = Modifier.size(InputChipDefaults.IconSize)) },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    trailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            )
                        }
                    }
                }
                if (yaNoLibres.isNotEmpty()) {
                    Spacer(Modifier.height(Espacio.s))
                    Aviso(
                        titulo = "Un asiento se ocupó mientras elegías",
                        texto = "${yaNoLibres.joinToString { it.etiquetaLarga }}: quítalo de tu selección y elige otro libre.",
                        tono = Tono.Aviso,
                    )
                }

                Spacer(Modifier.height(Espacio.m))
                Leyenda()
                Spacer(Modifier.height(Espacio.m))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BotonSecundario(
                        texto = "Elegir por número",
                        onClick = { eligiendoPorNumero = true },
                        icono = Icons.Filled.EventSeat,
                        habilitado = elegidos.size < objetivo,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Espacio.s))
                    ControlesZoom(
                        puedeAlejar = tamano > TAMANO_MIN,
                        puedeAcercar = tamano < TAMANO_MAX,
                        onAlejar = { tamano = (tamano - PASO_ZOOM).coerceAtLeast(TAMANO_MIN) },
                        onAcercar = { tamano = (tamano + PASO_ZOOM).coerceAtMost(TAMANO_MAX) },
                        onRestablecer = { tamano = TAMANO_INICIAL },
                    )
                }
            }

            Spacer(Modifier.height(Espacio.l))
            Escenario()
            Spacer(Modifier.height(Espacio.l))

            // Secciones en el orden en que fueron creadas
            asientos.map { it.seccion }.distinct().forEach { seccion ->
                val deLaSeccion = asientos.filter { it.seccion == seccion }
                SeccionAsientos(
                    nombre = seccion,
                    asientos = deLaSeccion,
                    tamano = tamano.dp,
                    seleccionados = seleccionados,
                    onSeleccionar = ::alternar,
                )
                Spacer(Modifier.height(Espacio.xl))
            }

            Text(
                "Elegir asientos no los aparta todavía: se reservan al confirmar. Si alguien toma uno antes, te avisaremos para que elijas otro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Espacio.margen),
            )
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}

// ==================================================================
//  Plano
// ==================================================================

/** Barra superior del recinto: de aquí "sale" el escenario. */
@Composable
private fun Escenario() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(36.dp)
                .clip(RoundedCornerShape(bottomStart = 60.dp, bottomEnd = 60.dp, topStart = 8.dp, topEnd = 8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Escenario",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SeccionAsientos(
    nombre: String,
    asientos: List<Asiento>,
    tamano: Dp,
    seleccionados: List<Long>,
    onSeleccionar: (Long) -> Unit,
) {
    val filas = asientos.groupBy { it.fila }.toSortedMap()
    // Un scroll por sección: todas sus filas se mueven juntas y con el mismo ancho
    val scrollH = rememberScrollState()

    Column(Modifier.fillMaxWidth()) {
        Text(
            nombre,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Espacio.margen, bottom = Espacio.s),
        )

        filas.forEach { (letraFila, deLaFila) ->
            val ordenados = deLaFila.sortedBy { it.numero }
            val centro = (ordenados.size - 1) / 2f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollH)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(Espacio.m))
                Text(
                    letraFila,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.Center,
                )
                ordenados.forEachIndexed { indice, asiento ->
                    // Curvatura: las butacas de los extremos bajan un poco,
                    // como en la platea curva de un teatro
                    val distancia = abs(indice - centro) / (centro.coerceAtLeast(1f))
                    val desplazamiento = (distancia * distancia * 7f).dp

                    Butaca(
                        asiento = asiento,
                        tamano = tamano,
                        seleccionado = asiento.id in seleccionados,
                        modifier = Modifier.offset(y = desplazamiento),
                        onClick = { onSeleccionar(asiento.id) },
                    )
                    // Pasillo central
                    if (indice == ordenados.size / 2 - 1) Spacer(Modifier.width(tamano / 2))
                }
                Spacer(Modifier.width(Espacio.l))
            }
        }
    }
}

/**
 * Una butaca. Tres señales, no solo color: libre muestra su número con borde;
 * ocupada va rellena, atenuada y con una «×»; la elegida va en coral con una
 * palomita. La semántica describe sección, fila, número y estado.
 */
@Composable
private fun Butaca(
    asiento: Asiento,
    tamano: Dp,
    seleccionado: Boolean,
    modifier: Modifier = Modifier,
    interactiva: Boolean = true,
    onClick: () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    val ocupado = asiento.estado == EstadoAsiento.OCUPADO
    val reservado = asiento.estado == EstadoAsiento.RESERVADO

    val fondo = when {
        seleccionado -> colores.tertiary
        ocupado -> colores.outlineVariant
        reservado -> EnZonaTema.semanticos.avisoContenedor
        else -> colores.surface
    }
    val borde = when {
        seleccionado -> colores.tertiary
        ocupado -> Color.Transparent
        else -> colores.secondary
    }
    val contenido = when {
        seleccionado -> colores.onTertiary
        ocupado -> colores.onSurfaceVariant
        else -> colores.onSurface
    }
    val estadoTexto = when {
        seleccionado -> "seleccionado"
        ocupado -> "ocupado"
        reservado -> "reservado"
        else -> "disponible"
    }
    val forma = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp)

    Box(
        modifier = modifier
            .padding(2.dp)
            .size(tamano)
            .clip(forma)
            .background(fondo)
            .border(width = if (seleccionado) 2.dp else 1.dp, color = borde, shape = forma)
            .then(
                if (interactiva) Modifier
                    .selectable(selected = seleccionado, enabled = !ocupado, role = Role.Checkbox, onClick = onClick)
                    .semantics { contentDescription = "${asiento.seccion}, fila ${asiento.fila}, asiento ${asiento.numero}, $estadoTexto" }
                else Modifier.clearAndSetSemantics { }
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            seleccionado -> Icon(
                Icons.Filled.Check, contentDescription = null, tint = contenido,
                modifier = Modifier.size(tamano * 0.6f)
            )
            ocupado -> Icon(
                Icons.Filled.Close, contentDescription = null, tint = contenido,
                modifier = Modifier.size(tamano * 0.5f)
            )
            else -> Text(
                asiento.numero.toString(),
                color = contenido,
                fontSize = (tamano.value * 0.38f).coerceAtLeast(9f).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Leyenda() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        ItemLeyenda(
            asiento = Fixtures.asientoC7.copy(estado = EstadoAsiento.DISPONIBLE, numero = 7),
            texto = "Libre", seleccionado = false,
        )
        ItemLeyenda(asiento = Fixtures.asientoC7, texto = "Ocupado", seleccionado = false)
        ItemLeyenda(
            asiento = Fixtures.asientoC7.copy(estado = EstadoAsiento.DISPONIBLE),
            texto = "Elegido", seleccionado = true,
        )
    }
}

@Composable
private fun ItemLeyenda(asiento: Asiento, texto: String, seleccionado: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Butaca(asiento = asiento, tamano = 22.dp, seleccionado = seleccionado, interactiva = false, onClick = {})
        Spacer(Modifier.width(Espacio.xs))
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botones de zoom de 48 dp: alejar, restablecer, acercar. */
@Composable
private fun ControlesZoom(
    puedeAlejar: Boolean,
    puedeAcercar: Boolean,
    onAlejar: () -> Unit,
    onAcercar: () -> Unit,
    onRestablecer: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        FilledTonalIconButton(onClick = onAlejar, enabled = puedeAlejar, modifier = Modifier.size(Tamanos.control)) {
            Icon(Icons.Filled.Remove, contentDescription = "Reducir el plano")
        }
        FilledTonalIconButton(onClick = onRestablecer, modifier = Modifier.size(Tamanos.control)) {
            Icon(Icons.Filled.CenterFocusWeak, contentDescription = "Restablecer la vista")
        }
        FilledTonalIconButton(onClick = onAcercar, enabled = puedeAcercar, modifier = Modifier.size(Tamanos.control)) {
            Icon(Icons.Filled.Add, contentDescription = "Ampliar el plano")
        }
    }
}

// ==================================================================
//  Selección accesible por sección, fila y número
// ==================================================================

@Composable
private fun DialogoElegirPorNumero(
    asientos: List<Asiento>,
    excluidos: List<Long>,
    onCerrar: () -> Unit,
    onElegir: (Long) -> Unit,
) {
    val secciones = asientos.map { it.seccion }.distinct()
    var seccion by remember { mutableStateOf(secciones.firstOrNull()) }
    val filas = asientos.filter { it.seccion == seccion }.map { it.fila }.distinct().sorted()
    var fila by remember(seccion) { mutableStateOf(filas.firstOrNull()) }
    val libresEnFila = asientos
        .filter { it.seccion == seccion && it.fila == fila && it.libre && it.id !in excluidos }
        .sortedBy { it.numero }
    var numero by remember(seccion, fila) { mutableStateOf<Int?>(null) }
    val elegido = libresEnFila.find { it.numero == numero }

    AlertDialog(
        onDismissRequest = onCerrar,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Elegir por número") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
                Text(
                    "Solo se muestran los asientos libres que aún no elegiste. Se añade a tu selección.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectorDesplegable("Sección", secciones, seccion, { seccion = it })
                SelectorDesplegable("Fila", filas, fila, { fila = it })
                SelectorDesplegable(
                    "Asiento",
                    libresEnFila.map { it.numero.toString() },
                    numero?.toString(),
                    { numero = it.toIntOrNull() },
                )
                if (fila != null && libresEnFila.isEmpty()) {
                    Aviso("No quedan asientos libres en la fila $fila.", tono = Tono.Aviso)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { elegido?.let { onElegir(it.id) } }, enabled = elegido != null) { Text("Añadir asiento") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ==================================================================
//  Previews
// ==================================================================

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewPlano() {
    EnZonaTheme {
        Column(Modifier.padding(vertical = Espacio.l)) {
            Column(Modifier.padding(horizontal = Espacio.l)) {
                Leyenda()
                Spacer(Modifier.height(Espacio.m))
                ControlesZoom(true, true, {}, {}, {})
            }
            Spacer(Modifier.height(Espacio.l))
            Escenario()
            Spacer(Modifier.height(Espacio.l))
            Fixtures.mapaPequeno.map { it.seccion }.distinct().forEach { s ->
                SeccionAsientos(s, Fixtures.mapaPequeno.filter { it.seccion == s }, 30.dp, seleccionados = listOf(12L, 14L), onSeleccionar = {})
                Spacer(Modifier.height(Espacio.l))
            }
        }
    }
}
