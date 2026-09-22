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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.CompraRealizada
import com.uv.enzona.data.model.ErrorCompra
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.ui.components.Aviso
import com.uv.enzona.ui.components.BarraSuperiorEnZona
import com.uv.enzona.ui.components.FilaDato
import com.uv.enzona.ui.components.FilaImporte
import com.uv.enzona.ui.components.TarjetaEnZona
import com.uv.enzona.ui.components.Tono
import com.uv.enzona.ui.components.ZonaDemostracion
import com.uv.enzona.ui.preview.Fixtures
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.Formato
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import com.uv.enzona.util.FechaEvento

/**
 * Pago de la orden (RF-11, ampliación de compra múltiple).
 *
 * Recibe la compra completa (evento, tipo, cantidad y asientos), muestra el
 * desglose por unidad y el total final (RNF-09) y ejecuta UNA operación de
 * compra que emite los N boletos o ninguno. La clave de operación se crea una
 * sola vez por pantalla: un doble toque o una recomposición no duplican la
 * emisión (en el backend debe aplicarse con `Idempotency-Key`).
 *
 * Estados: listo → procesando → aprobado (se navega al resumen) | rechazado
 * (aviso recuperable; la selección se conserva). El contrato del repositorio no
 * tiene PENDIENTE: OXXO y SPEI se dan por pagados al instante en la demo y se
 * rotula como simulación.
 */

private const val METODO_OXXO = -1L
private const val METODO_SPEI = -2L

private sealed interface EstadoCobro {
    data object Listo : EstadoCobro
    data object Procesando : EstadoCobro
    data class Rechazado(val mensaje: String, val recuperable: Boolean) : EstadoCobro
}

@Composable
fun CheckoutScreen(
    eventoId: Long,
    tipoBoletoId: Long?,
    cantidad: Int,
    asientosIds: List<Long>,
    onVolver: () -> Unit,
    onIrAMetodos: () -> Unit,
    onCompraRealizada: (CompraRealizada) -> Unit,
) {
    val evento = MockRepository.eventos.find { it.id == eventoId } ?: return
    val tipo = tipoBoletoId?.let { id -> MockRepository.tiposBoleto.find { it.id == id } }
    val asientos = asientosIds.mapNotNull { id -> MockRepository.asientos.find { it.id == id } }
    val usuarioId = SessionManager.usuarioId
    val tarjetas = MockRepository.metodosDe(usuarioId)
    val n = cantidad.coerceAtLeast(1)

    val precioUnitario = tipo?.precio ?: evento.precioDesde
    val comision = 0.0                       // sin comisión al asistente durante el piloto
    val total = precioUnitario * n + comision

    // Comprobación previa de disponibilidad (el repositorio la repite al cobrar)
    val cupo = MockRepository.cupoDisponible(eventoId, tipo?.id)
    val asientosOcupados = asientos.filter { !it.libre }
    val bloqueoPrevio: String? = when {
        evento.requiereAsiento && asientos.size != n -> "La selección de asientos no coincide con la cantidad ($n). Vuelve al plano para corregirla."
        asientosOcupados.isNotEmpty() -> "Se ocuparon ${asientosOcupados.joinToString { it.etiquetaCorta }}. Vuelve al plano y elige otros."
        cupo < n -> if (cupo <= 0) "Ya no quedan lugares para este evento." else "Solo quedan $cupo lugares y pediste $n. Vuelve y ajusta la cantidad."
        else -> null
    }

    var seleccion by rememberSaveable {
        mutableStateOf(MockRepository.metodoPredeterminado(usuarioId)?.id ?: METODO_OXXO)
    }
    var simularRechazo by rememberSaveable { mutableStateOf(false) }
    var estado by remember { mutableStateOf<EstadoCobro>(EstadoCobro.Listo) }
    val claveOperacion = rememberSaveable { UUID.randomUUID().toString() }
    val alcance = rememberCoroutineScope()
    val procesando = estado is EstadoCobro.Procesando

    // Si se borró la tarjeta seleccionada al volver de "Administrar", se cae a OXXO
    if (seleccion > 0 && tarjetas.none { it.id == seleccion }) seleccion = METODO_OXXO

    fun pagar() {
        if (estado is EstadoCobro.Procesando || bloqueoPrevio != null) return   // evita el doble envío
        estado = EstadoCobro.Procesando
        alcance.launch {
            delay(1400) // simula la ida y vuelta con la pasarela
            val metodo = tarjetas.find { it.id == seleccion }
            MockRepository.comprar(
                usuarioId = usuarioId,
                solicitud = SolicitudCompra(
                    eventoId = eventoId, tipoBoletoId = tipo?.id, cantidad = n,
                    asientosIds = asientosIds, claveOperacion = claveOperacion,
                ),
                metodoPago = metodo,
                pagoAprobado = !simularRechazo,
            ).onSuccess { compra ->
                estado = EstadoCobro.Listo
                onCompraRealizada(compra)
            }.onFailure { fallo ->
                estado = EstadoCobro.Rechazado(
                    mensaje = fallo.message ?: "No se pudo completar el pago.",
                    recuperable = fallo is ErrorCompra.PagoRechazado,
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BarraSuperiorEnZona(titulo = "Pago", onVolver = onVolver, volverHabilitado = !procesando) },
        bottomBar = {
            BarraAccion(
                textoBoton = when {
                    procesando -> "Procesando el pago"
                    bloqueoPrevio != null -> "No se puede completar"
                    else -> "Pagar ${Formato.importeMxn(total)}"
                },
                resumen = "Total final: ${Formato.importeMxn(total)} · ${if (n == 1) "1 boleto" else "$n boletos"}",
                habilitado = !procesando && bloqueoPrevio == null,
                cargando = procesando,
                onClick = ::pagar,
                aviso = when {
                    bloqueoPrevio != null -> {
                        { Aviso(titulo = "Revisa tu pedido", texto = bloqueoPrevio, tono = Tono.Aviso, textoAccion = "Volver", onAccion = onVolver) }
                    }
                    estado is EstadoCobro.Rechazado -> {
                        val rechazo = estado as EstadoCobro.Rechazado
                        {
                            Aviso(
                                titulo = if (rechazo.recuperable) "Pago rechazado" else "No se pudo completar la compra",
                                texto = rechazo.mensaje,
                                tono = Tono.Error,
                                textoAccion = if (rechazo.recuperable) "Reintentar con el mismo método" else "Volver a revisar",
                                onAccion = if (rechazo.recuperable) ::pagar else onVolver,
                            )
                        }
                    }
                    else -> null
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
            ResumenOrden(evento = evento, tipo = tipo, cantidad = n, asientos = asientos)

            Desglose(precioUnitario = precioUnitario, cantidad = n, comision = comision, total = total)

            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Método de pago", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    TextButton(onClick = onIrAMetodos, enabled = !procesando) {
                        Text(if (tarjetas.isEmpty()) "Agregar tarjeta" else "Administrar")
                    }
                }
                if (tarjetas.isEmpty()) {
                    Aviso(
                        texto = "No tienes tarjetas guardadas. Puedes pagar con OXXO o SPEI, o agregar una tarjeta.",
                        tono = Tono.Neutro,
                    )
                }
                tarjetas.forEach { tarjeta ->
                    OpcionPago(
                        icono = Icons.Filled.CreditCard,
                        titulo = tarjeta.etiqueta,
                        detalle = "Vence ${tarjeta.vencimiento}" + if (tarjeta.predeterminado) " · Predeterminada" else "",
                        seleccionado = seleccion == tarjeta.id,
                        habilitado = !procesando,
                        onClick = { seleccion = tarjeta.id },
                    )
                }
                OpcionPago(
                    icono = Icons.Filled.Storefront,
                    titulo = "Efectivo en OXXO",
                    detalle = "Recibes una referencia con vigencia de 24 horas",
                    seleccionado = seleccion == METODO_OXXO,
                    habilitado = !procesando,
                    onClick = { seleccion = METODO_OXXO },
                )
                OpcionPago(
                    icono = Icons.Filled.AccountBalance,
                    titulo = "Transferencia SPEI",
                    detalle = "Recibes una CLABE para transferir desde tu banco",
                    seleccionado = seleccion == METODO_SPEI,
                    habilitado = !procesando,
                    onClick = { seleccion = METODO_SPEI },
                )
            }

            ZonaDemostracion {
                Text(
                    "La pasarela está simulada: no se realiza ningún cargo real. En la demo, OXXO y SPEI se dan por " +
                        "pagados al instante; en producción el boleto se emite cuando la pasarela confirme el pago.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Espacio.s))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Simular pago rechazado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Reproduce el caso de prueba CP-10.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = simularRechazo,
                        onCheckedChange = { simularRechazo = it; estado = EstadoCobro.Listo },
                        enabled = !procesando,
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lock, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(Espacio.s))
                Text(
                    "El cobro lo procesa una pasarela externa. EnZona no almacena el número de tu tarjeta ni el CVV.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(Espacio.s))
        }
    }
}

// ==================================================================
//  Piezas
// ==================================================================

@Composable
private fun ResumenOrden(evento: Evento, tipo: TipoBoleto?, cantidad: Int, asientos: List<Asiento>) {
    TarjetaEnZona {
        Text("Tu orden", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Espacio.xs))
        Text(evento.nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Espacio.m))
        FilaDato(Icons.Filled.CalendarMonth, FechaEvento.corto(evento.fecha), descripcionIcono = "Fecha")
        Spacer(Modifier.height(Espacio.s))
        FilaDato(Icons.Filled.LocationOn, evento.lugar, descripcionIcono = "Lugar")
        Spacer(Modifier.height(Espacio.s))
        FilaDato(
            Icons.Filled.ConfirmationNumber,
            tipo?.nombre ?: "Entrada general",
            apoyo = if (cantidad == 1) "1 boleto" else "$cantidad boletos · un código QR por boleto",
            descripcionIcono = "Tipo de boleto",
        )
        if (asientos.isNotEmpty()) {
            Spacer(Modifier.height(Espacio.s))
            FilaDato(
                Icons.Filled.EventSeat,
                if (asientos.size == 1) asientos[0].etiquetaLarga
                else asientos.joinToString(", ") { "${it.seccion} ${it.etiquetaCorta}" },
                apoyo = if (asientos.size > 1) "${asientos.size} asientos" else null,
                descripcionIcono = "Asientos",
            )
        }
    }
}

@Composable
private fun Desglose(precioUnitario: Double, cantidad: Int, comision: Double, total: Double) {
    TarjetaEnZona {
        Text("Desglose", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Espacio.s))
        FilaImporte("Boleto × $cantidad (${Formato.importe(precioUnitario)} c/u)", Formato.importe(precioUnitario * cantidad))
        FilaImporte("Cargo por servicio", Formato.importe(comision))
        Spacer(Modifier.height(Espacio.s))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Espacio.xs))
        FilaImporte("Total a pagar", Formato.importeMxn(total), destacada = true)
        Text(
            "Este es el importe final. No hay cargos adicionales ni tarifas que cambien según la demanda.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OpcionPago(
    icono: ImageVector,
    titulo: String,
    detalle: String,
    seleccionado: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = seleccionado, enabled = habilitado, role = Role.RadioButton, onClick = onClick),
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
            RadioButton(selected = seleccionado, onClick = null, enabled = habilitado)
            Icon(
                icono, contentDescription = null,
                tint = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                Modifier
                    .padding(start = Espacio.m, top = Espacio.s, bottom = Espacio.s)
                    .weight(1f)
            ) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ==================================================================
//  Previews
// ==================================================================

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewPago() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.l)) {
            ResumenOrden(Fixtures.concierto, Fixtures.plantaBaja, 3, listOf(Fixtures.asientoC7, Fixtures.asientoC7.copy(numero = 8), Fixtures.asientoC7.copy(numero = 9)))
            Desglose(120.0, 3, 0.0, 360.0)
            OpcionPago(Icons.Filled.CreditCard, "Visa •••• 4242", "Vence 12/29 · Predeterminada", seleccionado = true, habilitado = true, onClick = {})
            OpcionPago(Icons.Filled.Storefront, "Efectivo en OXXO", "Recibes una referencia con vigencia de 24 horas", seleccionado = false, habilitado = true, onClick = {})
            BarraAccion(
                textoBoton = "Pagar $360.00 MXN", resumen = "Total final: $360.00 MXN · 3 boletos", habilitado = true, onClick = {},
                aviso = { Aviso(titulo = "Pago rechazado", texto = "La pasarela rechazó el pago. No se realizó ningún cargo.", tono = Tono.Error, textoAccion = "Reintentar", onAccion = {}) },
            )
        }
    }
}
