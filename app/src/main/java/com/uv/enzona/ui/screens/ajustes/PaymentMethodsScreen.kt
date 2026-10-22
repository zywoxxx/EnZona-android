package com.uv.enzona.ui.screens.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uv.enzona.data.Repositorio
import com.uv.enzona.ui.components.Carga
import com.uv.enzona.ui.components.IndicadorCarga
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.components.rememberOperacion
import com.uv.enzona.ui.components.BotonEnZona
import com.uv.enzona.ui.components.CampoEnZona
import com.uv.enzona.ui.theme.MoradoClaro
import com.uv.enzona.ui.theme.MoradoFondo
import com.uv.enzona.ui.theme.MoradoSuperficie
import com.uv.enzona.ui.theme.NaranjaAcento
import com.uv.enzona.ui.theme.RojoError
import com.uv.enzona.ui.theme.TextoPrincipal
import com.uv.enzona.ui.theme.TextoSecundario
import com.uv.enzona.ui.theme.VerdeOk

/**
 * Métodos de pago guardados.
 *
 * Solo se conserva la marca, los últimos cuatro dígitos y el token de la
 * pasarela. El número de la tarjeta no se almacena en ningún momento, conforme
 * al Documento de Visión y Alcance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(onVolver: () -> Unit) {
    val carga = rememberCarga { Repositorio.actual.metodosDePago() }
    val tarjetas = carga.valor.orEmpty()
    val operacion = rememberOperacion()

    var agregando by remember { mutableStateOf(false) }
    var numero by remember { mutableStateOf("") }
    var vencimiento by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var titular by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MoradoFondo,
        topBar = {
            TopAppBar(
                title = { Text("Métodos de pago", color = TextoPrincipal) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextoPrincipal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MoradoFondo)
            )
        }
    ) { relleno ->
        Column(
            modifier = Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            when (val estado = carga.estado) {
                is Carga.Cargando -> IndicadorCarga("Cargando tus tarjetas…", Modifier.padding(vertical = 24.dp))
                is Carga.Fallo -> {
                    Text(estado.mensaje, color = RojoError, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = carga::recargar) { Text("Reintentar", color = NaranjaAcento) }
                }
                is Carga.Lista -> Unit
            }
            operacion.error?.let {
                Text(it, color = RojoError, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
            }
            if (carga.estado is Carga.Lista && tarjetas.isEmpty() && !agregando) {
                Text(
                    "Aún no tienes tarjetas guardadas. Puedes agregar una para pagar más rápido la próxima vez.",
                    color = TextoSecundario
                )
                Spacer(Modifier.height(16.dp))
            }

            tarjetas.forEach { tarjeta ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MoradoSuperficie)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CreditCard, contentDescription = null, tint = NaranjaAcento)
                        Column(
                            Modifier
                                .padding(start = 12.dp)
                                .weight(1f)
                        ) {
                            Text(tarjeta.etiqueta, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                            Text("Vence ${tarjeta.vencimiento}", color = TextoSecundario,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            enabled = !operacion.enCurso,
                            onClick = { operacion.ejecutar<Unit> { Repositorio.actual.eliminarMetodo(tarjeta.id) } },
                        ) {
                            Icon(Icons.Filled.Delete, "Eliminar", tint = RojoError)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tarjeta.predeterminado) {
                            Text("● Predeterminada", color = VerdeOk,
                                style = MaterialTheme.typography.labelMedium)
                        } else {
                            TextButton(
                                enabled = !operacion.enCurso,
                                onClick = { operacion.ejecutar<Unit> { Repositorio.actual.marcarPredeterminado(tarjeta.id) } },
                            ) {
                                Text("Usar como predeterminada", color = NaranjaAcento,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (agregando) {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MoradoSuperficie)
                        .padding(16.dp)
                ) {
                    Text("Nueva tarjeta", style = MaterialTheme.typography.titleMedium, color = TextoPrincipal)
                    Spacer(Modifier.height(12.dp))
                    CampoEnZona(
                        valor = numero,
                        onCambio = { if (it.filter { c -> c.isDigit() }.length <= 16) { numero = it; error = null } },
                        etiqueta = "Número de tarjeta",
                        teclado = KeyboardType.Number,
                        ayuda = "Prueba con 4242 4242 4242 4242"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            CampoEnZona(vencimiento, { vencimiento = it; error = null },
                                "Vencimiento", ayuda = "MM/AA")
                        }
                        Column(Modifier.weight(1f)) {
                            CampoEnZona(
                                cvv,
                                { if (it.length <= 4) cvv = it },
                                "CVV", teclado = KeyboardType.Number, esContrasena = true
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CampoEnZona(titular, { titular = it; error = null }, "Nombre del titular")

                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(error!!, color = RojoError, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(16.dp))
                    BotonEnZona(
                        texto = "Guardar tarjeta",
                        cargando = operacion.enCurso,
                        onClick = {
                            // El número y el CVV no salen del teléfono: solo viajan marca, últimos 4, vencimiento y token
                            operacion.ejecutar(
                                alTerminar = { _: com.uv.enzona.data.model.MetodoPago ->
                                    agregando = false
                                    numero = ""; vencimiento = ""; cvv = ""; titular = ""
                                    error = null
                                },
                                alFallar = { error = it.message },
                            ) { Repositorio.actual.agregarTarjeta(numero, vencimiento.trim(), titular.trim()) }
                        }
                    )
                    TextButton(onClick = { agregando = false; error = null }) {
                        Text("Cancelar", color = TextoSecundario)
                    }
                }
            } else {
                BotonEnZona(texto = "Agregar tarjeta", onClick = { agregando = true })
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MoradoClaro.copy(alpha = 0.35f))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = VerdeOk,
                    modifier = Modifier.size(20.dp))
                Text(
                    "  EnZona no guarda el número de tu tarjeta. Solo conservamos la marca, los últimos cuatro " +
                        "dígitos y una referencia cifrada que entrega la pasarela de pago, con la que nunca se " +
                        "puede reconstruir tu tarjeta.",
                    color = TextoSecundario, style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
