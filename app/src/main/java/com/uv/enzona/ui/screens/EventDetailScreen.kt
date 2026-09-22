package com.uv.enzona.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.ui.components.Aviso
import com.uv.enzona.ui.components.BarraSuperiorEnZona
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.Etiqueta
import com.uv.enzona.ui.components.EtiquetaAgotado
import com.uv.enzona.ui.components.FilaDato
import com.uv.enzona.ui.components.PortadaEvento
import com.uv.enzona.ui.components.SelectorCantidad
import com.uv.enzona.ui.components.TarjetaEnZona
import com.uv.enzona.ui.components.Tono
import com.uv.enzona.ui.preview.Fixtures
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.Formato
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import com.uv.enzona.util.FechaEvento

/**
 * Detalle del evento (RF-09 a RF-11, ampliación de compra múltiple).
 *
 * En eventos de pago se elige el tipo y la CANTIDAD (hasta la disponibilidad
 * real); la compra continúa al plano de asientos o directamente al pago con
 * esa cantidad. En eventos gratuitos se conserva la política de una
 * confirmación por persona, con confirmación explícita (RF-10, CP-08).
 */
@Composable
fun EventDetailScreen(
    eventoId: Long,
    onVolver: () -> Unit,
    onBoletoEmitido: (Long) -> Unit,
    onElegirAsientos: (eventoId: Long, tipoId: Long?, cantidad: Int) -> Unit,
    onIrAPago: (eventoId: Long, tipoId: Long?, cantidad: Int) -> Unit,
    onVerBoletos: (() -> Unit)? = null,
) {
    // Se lee de la lista observable para que el aforo se refresque al comprar
    val evento = MockRepository.eventos.find { it.id == eventoId } ?: return
    val tipos = MockRepository.tiposDe(eventoId)
    val organizador = MockRepository.usuarios.find { it.id == evento.organizadorId }?.nombre

    var tipoSeleccionadoId by rememberSaveable { mutableStateOf(tipos.firstOrNull { it.cantidadDisponible > 0 }?.id) }
    val tipoSeleccionado = tipos.find { it.id == tipoSeleccionadoId } ?: tipos.firstOrNull()

    val usuarioId = SessionManager.usuarioId
    val boletosPropios = MockRepository.boletosDe(usuarioId).count { it.evento.id == eventoId && it.estado.name != "CANCELADO" }
    val yaConfirmoGratis = !evento.esDePago && boletosPropios > 0
    val asientosLibres = if (evento.requiereAsiento) MockRepository.asientosLibres(eventoId) else 0

    // Cantidad: en eventos de pago hasta la disponibilidad real; en gratuitos siempre 1
    val maximo = if (evento.esDePago) MockRepository.cupoDisponible(eventoId, tipoSeleccionado?.id) else 1
    var cantidadElegida by rememberSaveable { mutableIntStateOf(1) }
    val cantidad = cantidadElegida.coerceIn(1, maximo.coerceAtLeast(1))
    // Si al cambiar de tipo la cantidad excede el nuevo máximo, se revalida (sin selecciones ocultas)
    if (cantidadElegida != cantidad) cantidadElegida = cantidad

    var confirmando by rememberSaveable { mutableStateOf(false) }
    var procesando by remember { mutableStateOf(false) }
    var errorConfirmacion by remember { mutableStateOf<String?>(null) }
    val claveOperacion = rememberSaveable { UUID.randomUUID().toString() }
    val alcance = rememberCoroutineScope()

    val cancelado = evento.estado == EstadoEvento.CANCELADO
    val realizado = evento.estado == EstadoEvento.REALIZADO
    val puedeActuar = !yaConfirmoGratis && !cancelado && !realizado && maximo > 0 &&
        (tipoSeleccionado == null || tipoSeleccionado.cantidadDisponible > 0)

    val precioUnitario = if (evento.esDePago) (tipoSeleccionado?.precio ?: evento.precioDesde) else 0.0
    val total = precioUnitario * cantidad

    if (confirmando) {
        DialogoConfirmarAsistencia(
            evento = evento,
            procesando = procesando,
            error = errorConfirmacion,
            onCancelar = { if (!procesando) { confirmando = false; errorConfirmacion = null } },
            onConfirmar = {
                if (procesando) return@DialogoConfirmarAsistencia
                procesando = true
                errorConfirmacion = null
                alcance.launch {
                    delay(500) // simula la ida y vuelta al servidor; la regla de aforo vive en el repositorio
                    MockRepository.comprar(
                        usuarioId,
                        SolicitudCompra(eventoId = evento.id, tipoBoletoId = null, cantidad = 1, claveOperacion = claveOperacion),
                    ).onSuccess { compra ->
                        procesando = false
                        confirmando = false
                        onBoletoEmitido(compra.boletos.first().id)
                    }.onFailure {
                        procesando = false
                        errorConfirmacion = it.message ?: "No se pudo registrar la confirmación."
                    }
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = "", onVolver = onVolver) },
        bottomBar = {
            BarraAccion(
                textoBoton = when {
                    cancelado -> "Evento cancelado"
                    realizado -> "Evento finalizado"
                    yaConfirmoGratis -> "Ya confirmaste tu lugar"
                    maximo <= 0 -> "Aforo agotado"
                    evento.requiereAsiento -> if (cantidad == 1) "Elegir asiento" else "Elegir $cantidad asientos"
                    evento.esDePago -> if (cantidad == 1) "Comprar boleto" else "Comprar $cantidad boletos"
                    else -> "Confirmar asistencia"
                },
                resumen = when {
                    cancelado || realizado || yaConfirmoGratis -> null
                    maximo <= 0 -> "No quedan lugares"
                    evento.esDePago -> "${Formato.importeMxn(total)} · ${if (cantidad == 1) "1 boleto" else "$cantidad boletos"} · total final"
                    else -> "Gratis · sin cobro"
                },
                habilitado = puedeActuar,
                destacado = true,
                onClick = {
                    when {
                        evento.requiereAsiento -> onElegirAsientos(evento.id, tipoSeleccionado?.id, cantidad)
                        evento.esDePago -> onIrAPago(evento.id, tipoSeleccionado?.id, cantidad)
                        else -> { errorConfirmacion = null; confirmando = true }
                    }
                },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margen),
            verticalArrangement = Arrangement.spacedBy(Espacio.l),
        ) {
            PortadaEvento(
                nombre = evento.nombre,
                categoria = evento.categoria,
                fecha = evento.fecha,
                mostrarFecha = false,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                Text(
                    evento.nombre,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
                    Etiqueta(evento.categoria)
                    if (!evento.esDePago) Etiqueta("Gratis", tono = Tono.Destacado)
                    if (evento.requiereAsiento) Etiqueta("Asientos numerados", tono = Tono.Marca, icono = Icons.Filled.EventSeat)
                    if (evento.agotado && !cancelado) EtiquetaAgotado()
                }
            }

            when {
                cancelado -> Aviso(
                    titulo = "Este evento fue cancelado",
                    texto = "El organizador lo canceló. Si tenías boletos, quedaron cancelados y, en eventos de pago, el reembolso se gestiona con la pasarela.",
                    tono = Tono.Error,
                )
                realizado -> Aviso(
                    titulo = "Este evento ya se realizó",
                    texto = "Ya no admite confirmaciones ni compras.",
                    tono = Tono.Neutro,
                )
                yaConfirmoGratis -> Aviso(
                    titulo = "Ya confirmaste tu asistencia",
                    texto = "En eventos gratuitos se aparta un lugar por persona. Tu boleto está en Mis boletos.",
                    tono = Tono.Exito,
                    textoAccion = if (onVerBoletos != null) "Ver mis boletos" else null,
                    onAccion = onVerBoletos,
                )
                boletosPropios > 0 -> Aviso(
                    texto = if (boletosPropios == 1) "Ya tienes 1 boleto para este evento. Puedes comprar más si hay cupo."
                    else "Ya tienes $boletosPropios boletos para este evento. Puedes comprar más si hay cupo.",
                    tono = Tono.Neutro,
                    textoAccion = if (onVerBoletos != null) "Ver mis boletos" else null,
                    onAccion = onVerBoletos,
                )
            }

            TarjetaEnZona {
                FilaDato(Icons.Filled.CalendarMonth, FechaEvento.intervalo(evento.fecha, evento.fechaFin), apoyo = "Hora local del evento (Orizaba)", descripcionIcono = "Fecha")
                Spacer(Modifier.height(Espacio.m))
                FilaDato(
                    Icons.Filled.LocationOn, evento.lugar,
                    apoyo = listOf(evento.direccion, evento.ciudad).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                    descripcionIcono = "Lugar",
                )
                if (organizador != null) {
                    Spacer(Modifier.height(Espacio.m))
                    FilaDato(Icons.Filled.Person, organizador, apoyo = "Organiza", descripcionIcono = "Organizador")
                }
                Spacer(Modifier.height(Espacio.m))
                FilaDato(
                    Icons.Filled.Groups,
                    if (evento.agotado) "Sin lugares disponibles" else "${Formato.lugares(evento.disponibles)} disponibles",
                    apoyo = if (evento.requiereAsiento) "$asientosLibres asientos libres de ${evento.aforo}" else "Aforo de ${evento.aforo} personas",
                    descripcionIcono = "Cupo",
                )
            }

            // Ubicación exacta (RF-19): miniatura con marcador y «Cómo llegar»; sin coordenadas, solo la dirección
            if (evento.tieneCoordenadas) {
                com.uv.enzona.ui.components.MiniMapaEvento(
                    latitud = evento.latitud!!, longitud = evento.longitud!!, nombre = evento.lugar,
                )
            }

            if (evento.descripcion.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    Text("Acerca del evento", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        evento.descripcion,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (evento.esDePago && tipos.isNotEmpty() && !cancelado && !realizado) {
                Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    Text("Elige tu boleto", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    // Los tipos se deslizan en horizontal: así caben varios sin alargar la pantalla
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = Espacio.l),
                    ) {
                        items(tipos, key = { it.id }) { tipo ->
                            TarjetaTipoBoleto(
                                tipo = tipo,
                                seleccionado = tipoSeleccionado?.id == tipo.id,
                                habilitado = true,
                                onClick = { tipoSeleccionadoId = tipo.id },
                            )
                        }
                    }
                    if (tipos.size > 1) {
                        Text(
                            "Desliza para ver los ${tipos.size} tipos de boleto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TarjetaEnZona {
                    SelectorCantidad(
                        cantidad = cantidad,
                        maximo = maximo,
                        onCambio = { cantidadElegida = it },
                        habilitado = puedeActuar,
                    )
                    if (cantidad > 1) {
                        Spacer(Modifier.height(Espacio.s))
                        Text(
                            "${Formato.precio(precioUnitario)} × $cantidad = ${Formato.importeMxn(total)}. Cada boleto tendrá su propio código QR.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                when {
                    !puedeActuar -> ""
                    evento.requiereAsiento -> "En el siguiente paso elegirás ${if (cantidad == 1) "tu asiento" else "los $cantidad asientos"} en el plano del recinto."
                    evento.esDePago -> "Antes de pagar verás el desglose completo. El precio mostrado es el total final."
                    else -> "Confirmar asistencia aparta un lugar a tu nombre y genera tu boleto QR. No hay ningún cobro."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Espacio.s))
        }
    }
}

// ==================================================================
//  Piezas
// ==================================================================

/** Tarjeta de tipo de boleto para el carrusel horizontal (ancho fijo, alto igual entre tarjetas). */
@Composable
private fun TarjetaTipoBoleto(tipo: TipoBoleto, seleccionado: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    val agotado = tipo.cantidadDisponible <= 0
    val activo = habilitado && !agotado
    Surface(
        modifier = Modifier
            .width(168.dp)
            .selectable(selected = seleccionado, enabled = activo, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (seleccionado) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (seleccionado) 1.5.dp else 1.dp,
            if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(start = Espacio.xs, end = Espacio.m, top = Espacio.xs, bottom = Espacio.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = seleccionado, onClick = null, enabled = activo)
                Text(
                    tipo.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                Formato.precio(tipo.precio),
                style = MaterialTheme.typography.titleMedium,
                color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Espacio.m),
            )
            Text(
                if (agotado) "Agotado" else "${tipo.cantidadDisponible} disponibles",
                style = MaterialTheme.typography.bodySmall,
                color = if (agotado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Espacio.m),
            )
        }
    }
}

@Composable
private fun FilaTipoBoleto(tipo: TipoBoleto, seleccionado: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    val agotado = tipo.cantidadDisponible <= 0
    val activo = habilitado && !agotado
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = seleccionado, enabled = activo, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (seleccionado) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (seleccionado) 1.5.dp else 1.dp,
            if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = Espacio.xs, end = Espacio.l, top = Espacio.xs, bottom = Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = seleccionado, onClick = null, enabled = activo)
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = Espacio.s)
            ) {
                Text(
                    tipo.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (agotado) "Agotado" else "${tipo.cantidadDisponible} disponibles",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (agotado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                Formato.precio(tipo.precio),
                style = MaterialTheme.typography.titleMedium,
                color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Barra inferior fija con el resumen del precio y la única acción principal. */
@Composable
fun BarraAccion(
    textoBoton: String,
    resumen: String?,
    habilitado: Boolean,
    onClick: () -> Unit,
    cargando: Boolean = false,
    destacado: Boolean = true,
    aviso: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = Espacio.margen, vertical = Espacio.m)) {
            if (aviso != null) {
                aviso()
                Spacer(Modifier.height(Espacio.m))
            }
            if (resumen != null) {
                Text(
                    resumen,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = Espacio.s),
                )
            }
            BotonEnZona(texto = textoBoton, onClick = onClick, habilitado = habilitado, cargando = cargando, destacado = destacado)
        }
    }
}

/** Confirmación explícita de asistencia a un evento gratuito (RF-10). */
@Composable
private fun DialogoConfirmarAsistencia(
    evento: Evento,
    procesando: Boolean,
    error: String?,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Confirmar asistencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
                Text(evento.nombre, style = MaterialTheme.typography.titleMedium)
                FilaDato(Icons.Filled.CalendarMonth, FechaEvento.corto(evento.fecha))
                FilaDato(Icons.Filled.LocationOn, evento.lugar)
                FilaDato(Icons.Filled.Groups, "${Formato.lugares(evento.disponibles)} disponibles ahora")
                Text(
                    "Se apartará 1 lugar a tu nombre y recibirás un boleto QR. Este evento es gratuito: no se realiza ningún cobro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Aviso(texto = error, tono = Tono.Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar, enabled = !procesando) {
                Text(if (procesando) "Confirmando…" else "Confirmar asistencia")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !procesando) { Text("Cancelar") }
        },
    )
}

// ==================================================================
//  Previews
// ==================================================================

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewTiposYBarra() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                TarjetaTipoBoleto(Fixtures.plantaBaja, seleccionado = true, habilitado = true, onClick = {})
                TarjetaTipoBoleto(Fixtures.balcon, seleccionado = false, habilitado = true, onClick = {})
            }
            FilaTipoBoleto(Fixtures.agotado, seleccionado = false, habilitado = true, onClick = {})
            Spacer(Modifier.height(Espacio.l))
            BarraAccion(textoBoton = "Comprar 3 boletos", resumen = "$360.00 MXN · 3 boletos · total final", habilitado = true, onClick = {})
            Spacer(Modifier.height(Espacio.s))
            BarraAccion(textoBoton = "Aforo agotado", resumen = "No quedan lugares", habilitado = false, onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewDialogoGratis() {
    EnZonaTheme {
        DialogoConfirmarAsistencia(Fixtures.feriaGratis, procesando = false, error = null, onCancelar = {}, onConfirmar = {})
    }
}
