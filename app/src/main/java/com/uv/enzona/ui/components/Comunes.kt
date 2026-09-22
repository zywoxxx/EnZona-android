package com.uv.enzona.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.EnZonaTheme
import com.uv.enzona.ui.theme.Espacio
import com.uv.enzona.ui.theme.Tamanos

/*
 * Componentes compartidos de la marca. Todos leen del tema (MaterialTheme) en
 * lugar de colores sueltos, para que un ajuste en ui/theme se refleje en toda
 * la app. Las firmas públicas existentes se conservan: las pantallas de las
 * etapas posteriores siguen compilando sin cambios.
 */

// ==================================================================
//  Campos
// ==================================================================

/** Campo de texto con el estilo de la marca y soporte de mensaje de error. */
@Composable
fun CampoEnZona(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    ayuda: String? = null,
    esContrasena: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    lineas: Int = 1,
    mayusculas: Boolean = false,
    habilitado: Boolean = true,
    iconoInicial: ImageVector? = null,
) {
    val colores = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = { onCambio(if (mayusculas) it.uppercase() else it) },
            label = { Text(etiqueta) },
            modifier = Modifier.fillMaxWidth(),
            enabled = habilitado,
            isError = error != null,
            singleLine = lineas == 1,
            minLines = lineas,
            shape = MaterialTheme.shapes.medium,
            leadingIcon = iconoInicial?.let { { Icon(it, contentDescription = null) } },
            visualTransformation = if (esContrasena) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = teclado),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colores.primary,
                unfocusedBorderColor = colores.outline,
                errorBorderColor = colores.error,
                focusedLabelColor = colores.primary,
                unfocusedLabelColor = colores.onSurfaceVariant,
                errorLabelColor = colores.error,
                focusedTextColor = colores.onSurface,
                unfocusedTextColor = colores.onSurface,
                cursorColor = colores.primary,
                focusedContainerColor = colores.surfaceContainer,
                unfocusedContainerColor = colores.surfaceContainer,
                errorContainerColor = colores.surfaceContainer,
                disabledContainerColor = colores.surfaceContainerLow,
                focusedLeadingIconColor = colores.primary,
                unfocusedLeadingIconColor = colores.onSurfaceVariant,
            )
        )
        val pie = error ?: ayuda
        if (pie != null) {
            Text(
                pie,
                color = if (error != null) colores.error else colores.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(start = Espacio.xs, top = Espacio.xs)
                    .semantics { if (error != null) contentDescription = "Error: $error" }
            )
        }
    }
}

// ==================================================================
//  Ajustes
// ==================================================================

/** Encabezado de sección en las pantallas de ajustes. */
@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier.padding(start = Espacio.xs, top = 22.dp, bottom = Espacio.s),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
    )
}

/** Fila navegable de ajustes: icono, texto, valor opcional y flecha. */
@Composable
fun FilaAjuste(
    icono: ImageVector,
    texto: String,
    modifier: Modifier = Modifier,
    valor: String? = null,
    descripcion: String? = null,
    colorTexto: Color = MaterialTheme.colorScheme.onSurface,
    colorIcono: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    mostrarFlecha: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Tamanos.control)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(Tamanos.iconoFila))
        Column(
            Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(texto, color = colorTexto, style = MaterialTheme.typography.bodyLarge)
            if (descripcion != null) {
                Text(
                    descripcion,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (valor != null) {
            Text(valor, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (mostrarFlecha) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Espacio.xs)
            )
        }
    }
}

/** Línea divisoria tenue entre filas de ajustes. */
@Composable
fun Separador(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 50.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ==================================================================
//  Botones
// ==================================================================

/**
 * Botón principal de la marca (píldora naranja, 52 dp).
 *
 * `cargando` muestra un indicador y bloquea el botón: evita dobles envíos
 * mientras se procesa una acción. Eso ayuda a la experiencia, pero no
 * sustituye la idempotencia del servidor.
 */
@Composable
fun BotonEnZona(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    icono: ImageVector? = null,
    destacado: Boolean = false,
) {
    val colores = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        modifier = modifier
            .fillMaxWidth()
            .height(Tamanos.botonPrincipal),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            // Destacado = coral con texto tinta (comprar, confirmar, publicar); normal = azul marino con blanco
            containerColor = if (destacado) colores.tertiary else colores.primary,
            contentColor = if (destacado) colores.onTertiary else colores.onPrimary,
            disabledContainerColor = colores.surfaceContainerHighest,
            disabledContentColor = colores.onSurfaceVariant,
        )
    ) {
        if (cargando) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = colores.onSurfaceVariant,
            )
            Spacer(Modifier.width(Espacio.m))
        } else if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Espacio.s))
        }
        Text(texto, style = MaterialTheme.typography.titleSmall)
    }
}

/** Botón secundario (contorno, píldora). Para acciones alternas a la principal. */
@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .height(Tamanos.control),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Espacio.s))
        }
        Text(texto, style = MaterialTheme.typography.titleSmall)
    }
}

// ==================================================================
//  Previews
// ==================================================================

@Preview(name = "Botones y campo", showBackground = true, backgroundColor = 0xFFEEF1F6)
@Composable
private fun PreviewComunes() {
    EnZonaTheme {
        Column(Modifier.padding(Espacio.l)) {
            CampoEnZona(valor = "", onCambio = {}, etiqueta = "Correo o CURP")
            Spacer(Modifier.height(Espacio.m))
            CampoEnZona(valor = "ABC", onCambio = {}, etiqueta = "CURP", error = "CURP inválido: deben ser 18 caracteres.")
            Spacer(Modifier.height(Espacio.l))
            BotonEnZona(texto = "Comprar boleto", onClick = {}, destacado = true)
            Spacer(Modifier.height(Espacio.s))
            BotonEnZona(texto = "Iniciar sesión", onClick = {})
            Spacer(Modifier.height(Espacio.s))
            BotonEnZona(texto = "Procesando", onClick = {}, cargando = true)
            Spacer(Modifier.height(Espacio.s))
            BotonEnZona(texto = "Aforo agotado", onClick = {}, habilitado = false)
            Spacer(Modifier.height(Espacio.s))
            BotonSecundario(texto = "Elegir por número", onClick = {})
            Box(Modifier.height(Espacio.l))
        }
    }
}
