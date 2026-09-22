package com.uv.enzona.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.util.FechaEvento
import com.uv.enzona.util.FechaHoraEvento
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/*
 * Selectores de fecha y hora del evento (v3, cambios 4 y 5).
 *
 * La fecha se elige en el calendario de Material 3 (el campo es de solo
 * lectura: no acepta texto). La hora se captura con TimeInput en 24 h (solo
 * dígitos). Los valores viven en el estado del formulario; aquí no se formatea
 * nada por cuenta propia: se usa FechaEvento.
 */

/** Campo de solo lectura que abre un selector al tocarlo (48 dp, con semántica de botón). */
@Composable
fun CampoSelector(
    valor: String,
    etiqueta: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    error: String? = null,
    ayuda: String? = null,
    testTag: String = "",
) {
    val colores = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Box {
            OutlinedTextField(
                value = valor,
                onValueChange = {},           // solo lectura: nunca se escribe a mano
                readOnly = true,
                enabled = habilitado,
                label = { Text(etiqueta) },
                leadingIcon = { Icon(icono, contentDescription = null) },
                isError = error != null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colores.primary,
                    unfocusedBorderColor = colores.outline,
                    errorBorderColor = colores.error,
                    unfocusedContainerColor = colores.surface,
                    focusedContainerColor = colores.surface,
                    disabledContainerColor = colores.surfaceContainerLow,
                    focusedTextColor = colores.onSurface,
                    unfocusedTextColor = colores.onSurface,
                    disabledTextColor = colores.onSurfaceVariant,
                    unfocusedLeadingIconColor = colores.onSurfaceVariant,
                    focusedLeadingIconColor = colores.primary,
                ),
            )
            // Capa que captura el toque: abre el selector y evita el teclado
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(enabled = habilitado, onClick = onClick)
                    .semantics { role = Role.Button; contentDescription = "$etiqueta: ${valor.ifBlank { "sin elegir" }}. Abrir selector" },
            )
        }
        val pie = error ?: ayuda
        if (pie != null) {
            Text(
                pie,
                color = if (error != null) colores.error else colores.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Espacio.xs, top = Espacio.xs),
            )
        }
    }
}

/**
 * Diálogo de calendario. `minima` deshabilita todos los días anteriores
 * (CP-FECHA-02). El picker trabaja en UTC a medianoche; se convierte a LocalDate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoFecha(
    inicial: LocalDate?,
    minima: LocalDate,
    onCerrar: () -> Unit,
    onElegir: (LocalDate) -> Unit,
) {
    val minimaMillis = minima.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = (inicial ?: minima).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minimaMillis
            override fun isSelectableYear(year: Int): Boolean = year >= minima.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = estado.selectedDateMillis ?: return@TextButton
                    onElegir(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                enabled = estado.selectedDateMillis != null,
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado, showModeToggle = false)
    }
}

/** Diálogo de hora en 24 h con entrada numérica (TimeInput): imposible escribir letras (CP-HORA-01). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoHora(
    inicial: LocalTime?,
    titulo: String,
    onCerrar: () -> Unit,
    onElegir: (LocalTime) -> Unit,
) {
    val estado = rememberTimePickerState(
        initialHour = inicial?.hour ?: 19,
        initialMinute = inicial?.minute ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onCerrar,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(titulo) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TimeInput(state = estado)
                Text(
                    "Formato de 24 horas (00–23 : 00–59).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onElegir(LocalTime.of(estado.hour, estado.minute)) }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/**
 * Par de selectores fecha + hora con su resumen. Devuelve los valores por
 * separado; el formulario los combina con `FechaHoraEvento.combinar`.
 */
@Composable
fun SelectorFechaHora(
    etiquetaFecha: String,
    etiquetaHora: String,
    fecha: LocalDate?,
    hora: LocalTime?,
    minima: LocalDate,
    onFecha: (LocalDate?) -> Unit,
    onHora: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    opcional: Boolean = false,
    error: String? = null,
    habilitado: Boolean = true,
) {
    var abrirFecha by rememberSaveable { mutableStateOf(false) }
    var abrirHora by rememberSaveable { mutableStateOf(false) }

    if (abrirFecha) {
        DialogoFecha(inicial = fecha, minima = minima, onCerrar = { abrirFecha = false }, onElegir = { onFecha(it); abrirFecha = false })
    }
    if (abrirHora) {
        DialogoHora(inicial = hora, titulo = etiquetaHora, onCerrar = { abrirHora = false }, onElegir = { onHora(it); abrirHora = false })
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            CampoSelector(
                valor = fecha?.let { FechaEvento.soloFecha(it) } ?: "",
                etiqueta = etiquetaFecha,
                icono = Icons.Filled.CalendarMonth,
                onClick = { abrirFecha = true },
                habilitado = habilitado,
                modifier = Modifier.weight(1.4f),
                testTag = "campo_$etiquetaFecha",
            )
            Spacer(Modifier.width(Espacio.s))
            CampoSelector(
                valor = hora?.let { FechaEvento.hora(it) } ?: "",
                etiqueta = etiquetaHora,
                icono = Icons.Filled.Schedule,
                onClick = { abrirHora = true },
                habilitado = habilitado,
                modifier = Modifier.weight(1f),
                testTag = "campo_$etiquetaHora",
            )
        }
        val resumen = if (fecha != null && hora != null) FechaEvento.largo(FechaHoraEvento.combinar(fecha, hora)) else null
        Text(
            when {
                error != null -> error
                resumen != null -> resumen
                opcional -> "Opcional"
                else -> "Elige la fecha en el calendario y la hora en formato 24 h."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Espacio.xs, top = Espacio.xs),
        )
        if (opcional && (fecha != null || hora != null)) {
            TextButton(onClick = { onFecha(null); onHora(null) }, enabled = habilitado) { Text("Quitar fecha de fin") }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF1F6, widthDp = 360)
@Composable
private fun PreviewSelectorFechaHora() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l)) {
            SelectorFechaHora(
                etiquetaFecha = "Fecha de inicio", etiquetaHora = "Hora",
                fecha = LocalDate.of(2026, 9, 25), hora = LocalTime.of(19, 0), minima = LocalDate.of(2026, 9, 17),
                onFecha = {}, onHora = {},
            )
            Spacer(Modifier.height(Espacio.l))
            SelectorFechaHora(
                etiquetaFecha = "Fecha de fin", etiquetaHora = "Hora de fin",
                fecha = null, hora = null, minima = LocalDate.of(2026, 9, 17), onFecha = {}, onHora = {}, opcional = true,
            )
        }
    }
}
