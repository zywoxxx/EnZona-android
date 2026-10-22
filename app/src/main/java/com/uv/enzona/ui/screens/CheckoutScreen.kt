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
import com.uv.enzona.data.PagoElegido
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.model.MetodoPago
import com.uv.enzona.data.model.TipoMetodoPago
import com.uv.enzona.ui.components.EsperaDeCarga
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.components.rememberOperacion
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
 * Estados: listo → procesando → aprobado (se navega al boleto o al resumen) |
 * pendiente (OXXO y SPEI: el resumen muestra la referencia y los boletos se
 * emiten cuando la pasarela confirma) | rechazado (aviso recuperable; la
 * selección se conserva).
 *
 * La pasarela del backend es una SIMULACIÓN: «Simular pago rechazado» paga con
 * el token `-rechazo`, que la pasarela simulada rechaza siempre (CP-10).
 */

private const val METODO_OXXO = -1L
private const val METODO_SPEI = -2L
/** Tarjeta sin guardar: la pasarela entrega un token de un solo uso (la API de EnZona no guarda métodos). */
private const val METODO_TARJETA_PASARELA = -3L

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
    val carga = rememberCarga(eventoId, tipoBoletoId, asientosIds) {
        val repositorio = Repositorio.actual
        val evento = repositorio.evento(eventoId)
        DatosPago(
            evento = evento,
            tipo = tipoBoletoId?.let { id -> repositorio.tiposDe(eventoId).find { it.id == id } },
            asientos = if (asientosIds.isEmpty()) emptyList() else repositorio.asientosDe(eventoId).filter { it.id in asientosIds },
            tarjetas = if (repositorio.capacidades.metodosGuardados) repositorio.metodosDePago() else emptyList(),
        )
    }
    val datos = carga.valor
    if (datos == null) {
        EsperaDeCarga(carga, titulo = "Pago", onVolver = onVolver, textoCargando = "Preparando tu orden…")
        return
    }
    val evento = datos.evento
    val tipo = datos.tipo
    val asientos = datos.asientos
    val tarjetas = datos.tarjetas
    val capacidades = Repositorio.actual.capacidades
    // Sin métodos guardados (API de EnZona) se paga con tarjeta directa: el token lo genera la pasarela
    val ofreceTarjetaDirecta = !capacidades.metodosGuardados || (tarjetas.isEmpty() && !capacidades.pagosDiferidos)
    val metodoPorDefecto = if (ofreceTarjetaDirecta) METODO_TARJETA_PASARELA else METODO_OXXO
    val n = cantidad.coerceAtLeast(1)

    val precioUnitario = tipo?.precio ?: evento.precioDesde
    val comision = 0.0                       // sin comisión al asistente durante el piloto
    val total = precioUnitario * n + comision

    // Comprobación previa de disponibilidad (el servidor la repite al cobrar)
    val cupo = minOf(evento.disponibles, tipo?.cantidadDisponible ?: evento.disponibles).coerceAtLeast(0)
    val asientosOcupados = asientos.filter { !it.libre }
    val bloqueoPrevio: String? = when {
        evento.requiereAsiento && asientos.size != n -> "La selección de asientos no coincide con la cantidad ($n). Vuelve al plano para corregirla."
        asientosOcupados.isNotEmpty() -> "Se ocuparon ${asientosOcupados.joinToString { it.etiquetaCorta }}. Vuelve al plano y elige otros."
        cupo < n -> if (cupo <= 0) "Ya no quedan lugares para este evento." else "Solo quedan $cupo lugares y pediste $n. Vuelve y ajusta la cantidad."
        else -> null
    }

    var seleccion by rememberSaveable {
        mutableStateOf((tarjetas.firstOrNull { it.predeterminado } ?: tarjetas.firstOrNull())?.id ?: metodoPorDefecto)
    }
    var simularRechazo by rememberSaveable { mutableStateOf(false) }
    var estado by remember { mutableStateOf<EstadoCobro>(EstadoCobro.Listo) }
    var claveOperacion by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val operacion = rememberOperacion()
    val procesando = operacion.enCurso

    // Si se borró la tarjeta seleccionada al volver de "Administrar", o el método ya no se ofrece, se cae al predeterminado
    if (seleccion > 0 && tarjetas.none { it.id == seleccion }) seleccion = metodoPorDefecto
    if ((seleccion == METODO_OXXO || seleccion == METODO_SPEI) && !capacidades.pagosDiferidos) seleccion = metodoPorDefecto

    fun pagar() {
        if (procesando || bloqueoPrevio != null) return   // evita el doble envío; la idempotencia real es del servidor
        estado = EstadoCobro.Procesando
        val pago = when (seleccion) {
            METODO_OXXO -> PagoElegido(TipoMetodoPago.OXXO, simularRechazo = simularRechazo)
            METODO_SPEI -> PagoElegido(TipoMetodoPago.SPEI, simularRechazo = simularRechazo)
            METODO_TARJETA_PASARELA -> PagoElegido(TipoMetodoPago.TARJETA, simularRechazo = simularRechazo)
            else -> PagoElegido(TipoMetodoPago.TARJETA, metodoPagoId = seleccion, simularRechazo = simularRechazo)
        }
        operacion.ejecutar(
            alTerminar = { compra: CompraRealizada ->
                estado = EstadoCobro.Listo
                onCompraRealizada(compra)
            },
            alFallar = { fallo ->
                estado = EstadoCobro.Rechazado(
                    mensaje = fallo.message ?: "No se pudo completar el pago.",
                    recuperable = fallo is ErrorCompra.PagoRechazado,
                )
                // Un pago rechazado deja una orden CANCELADA ligada a la clave: el reintento necesita una clave nueva
                if (fallo is ErrorCompra.PagoRechazado) claveOperacion = UUID.randomUUID().toString()
                carga.recargar()      // la disponibilidad pudo cambiar: se refresca el resumen
            },
        ) {
            Repositorio.actual.comprar(
                SolicitudCompra(
                    eventoId = eventoId, tipoBoletoId = tipo?.id, cantidad = n,
                    asientosIds = asientosIds, claveOperacion = claveOperacion,
                ),
                pago = pago,
            )
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
                    if (capacidades.metodosGuardados) {
                        TextButton(onClick = onIrAMetodos, enabled = !procesando) {
                            Text(if (tarjetas.isEmpty()) "Agregar tarjeta" else "Administrar")
                        }
                    }
                }
                if (tarjetas.isEmpty() && capacidades.metodosGuardados && capacidades.pagosDiferidos) {
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
                if (ofreceTarjetaDirecta) {
                    OpcionPago(
                        icono = Icons.Filled.CreditCard,
                        titulo = "Tarjeta de crédito o débito",
                        detalle = "La pasarela genera un token de un solo uso; EnZona no recibe el número",
                        seleccionado = seleccion == METODO_TARJETA_PASARELA,
                        habilitado = !procesando,
                        onClick = { seleccion = METODO_TARJETA_PASARELA },
                    )
                }
                if (capacidades.pagosDiferidos) {
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
            }

            ZonaDemostracion {
                Text(
                    "La pasarela está simulada: no se realiza ningún cargo real." +
                        if (capacidades.pagosDiferidos) " Con OXXO o SPEI la orden queda pendiente con su referencia y los boletos se emiten cuando se confirma el pago." else "",
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
                        Text("Paga con el token de rechazo de la pasarela simulada (CP-10).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private data class DatosPago(val evento: Evento, val tipo: TipoBoleto?, val asientos: List<Asiento>, val tarjetas: List<MetodoPago>)

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
