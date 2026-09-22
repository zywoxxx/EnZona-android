package com.uv.enzona.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.uv.enzona.data.model.PORCENTAJE_COMISION_CANCELACION
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.data.model.TipoCancelacion
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.Formato
import java.math.BigDecimal

/**
 * Diálogo de cancelación de evento (RF-14, v3). Muestra la política calculada
 * por `politicaCancelacion` (única fuente de verdad); no vuelve a calcular nada.
 *
 * - Gratuito / borrador / de pago sin ventas: "Se cancelará el evento y se anularán N confirmaciones".
 * - De pago con ventas: reembolso a los asistentes + comisión del 10 % a cargo del organizador;
 *   el botón dice "Cancelar y aceptar comisión".
 * - Administrativa: motivo obligatorio; sin comisión al organizador.
 * - Bloqueada: el botón se deshabilita y se muestra el motivo.
 */
@Composable
fun DialogoCancelarEvento(
    nombreEvento: String,
    politica: PoliticaCancelacion,
    administrativa: Boolean,
    procesando: Boolean,
    error: String?,
    onCancelar: () -> Unit,
    onConfirmar: (motivo: String?) -> Unit,
) {
    var motivo by rememberSaveable { mutableStateOf("") }
    val motivoValido = !administrativa || motivo.trim().length >= 5
    val puedeConfirmar = politica.permitida && motivoValido && !procesando

    val textoBoton = when {
        !politica.permitida -> "No se puede cancelar"
        administrativa -> "Cancelar evento"
        politica.cobraComision -> "Cancelar y aceptar comisión"
        else -> "Cancelar evento"
    }

    AlertDialog(
        onDismissRequest = { if (!procesando) onCancelar() },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (administrativa) "Cancelar evento (administración)" else "¿Cancelar el evento?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.m), modifier = Modifier.testTag("dialogo_cancelar")) {
                Text(nombreEvento, style = MaterialTheme.typography.titleMedium)

                if (!politica.permitida) {
                    Aviso(titulo = "Cancelación bloqueada", texto = politica.motivoBloqueo ?: "", tono = Tono.Error)
                } else {
                    Text(
                        textoPolitica(politica, administrativa),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    when (politica.tipo) {
                        TipoCancelacion.DE_PAGO_CON_COMISION -> Aviso(
                            titulo = "Comisión de cancelación: ${Formato.importeMxn(politica.comision.toDouble())}",
                            texto = "Es el $PORCENTAJE_COMISION_CANCELACION % de ${Formato.importeMxn(politica.importeCobrado.toDouble())} cobrados. " +
                                "Los asistentes reciben el 100 % de reembolso; la comisión se carga a tu cuenta de organizador.",
                            tono = Tono.Aviso,
                        )
                        TipoCancelacion.DE_PAGO_SIN_VENTAS -> Aviso(
                            texto = "Este evento de pago no tiene ventas: no se aplica comisión y la cancelación procede como en un evento gratuito.",
                            tono = Tono.Neutro,
                        )
                        TipoCancelacion.ADMINISTRATIVA -> Aviso(
                            texto = "Cancelación por moderación: los asistentes reciben el 100 % de reembolso y no se cobra comisión al organizador. Se registrará quién canceló y por qué.",
                            tono = Tono.Neutro,
                        )
                        else -> Unit
                    }
                    if (administrativa) {
                        CampoEnZona(
                            valor = motivo,
                            onCambio = { motivo = it },
                            etiqueta = "Motivo de la cancelación",
                            lineas = 2,
                            ayuda = if (motivoValido) null else "Escribe el motivo (mínimo 5 caracteres).",
                            habilitado = !procesando,
                            modifier = Modifier.testTag("campo_motivo"),
                        )
                    }
                    Text(
                        "Esta acción no se puede deshacer. Se avisará a los asistentes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (error != null) Aviso(texto = error, tono = Tono.Error)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmar(motivo.trim().ifBlank { null }) },
                enabled = puedeConfirmar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag("boton_confirmar_cancelacion"),
            ) { Text(if (procesando) "Cancelando…" else textoBoton) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !procesando, modifier = Modifier.testTag("boton_volver")) {
                Text(if (politica.permitida) "Conservar evento" else "Cerrar")
            }
        },
    )
}

private fun textoPolitica(p: PoliticaCancelacion, administrativa: Boolean): String {
    val n = p.boletosAfectados
    val confirmaciones = when (n) { 1 -> "1 boleto"; else -> "$n boletos" }
    return when {
        administrativa && n == 0 -> "Se cancelará el evento. No hay confirmaciones ni boletos que anular."
        administrativa && p.reembolso.signum() > 0 ->
            "Se cancelará el evento, se anularán $confirmaciones y se reembolsarán ${Formato.importeMxn(p.reembolso.toDouble())} a los asistentes."
        administrativa -> "Se cancelará el evento y se anularán $confirmaciones."
        p.tipo == TipoCancelacion.DE_PAGO_CON_COMISION ->
            "Se reembolsarán ${Formato.importeMxn(p.reembolso.toDouble())} por ${if (n == 1) "1 boleto" else "$n boletos"} a sus asistentes. " +
                "Se aplicará una comisión de cancelación del $PORCENTAJE_COMISION_CANCELACION % (${Formato.importeMxn(p.comision.toDouble())}) a tu cuenta."
        p.tipo == TipoCancelacion.BORRADOR -> "El evento está en borrador: se cancelará sin comisión."
        n == 0 -> "Se cancelará el evento. Todavía no hay confirmaciones que anular."
        else -> "Se cancelará el evento y se anularán ${if (n == 1) "1 confirmación" else "$n confirmaciones"}."
    }
}

// ==================================================================
//  Previews
// ==================================================================

private fun politicaPreview(tipo: TipoCancelacion, cobrado: Double, n: Int) = PoliticaCancelacion(
    permitida = true, tipo = tipo,
    importeCobrado = BigDecimal.valueOf(cobrado), comision = BigDecimal.valueOf(cobrado * 0.10), reembolso = BigDecimal.valueOf(cobrado),
    boletosAfectados = n, motivoBloqueo = null,
)

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewCancelarPago() {
    EnZonaTheme {
        DialogoCancelarEvento("Concierto: Orquesta Universitaria", politicaPreview(TipoCancelacion.DE_PAGO_CON_COMISION, 1500.0, 12), false, false, null, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewCancelarBloqueado() {
    EnZonaTheme {
        DialogoCancelarEvento(
            "Feria", PoliticaCancelacion(false, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 3, "El evento ya comenzó. No se puede cancelar; márcalo como finalizado."),
            false, false, null, {}, {},
        )
    }
}
