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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.uv.enzona.ui.components.PortadaEvento
import com.uv.enzona.util.ImagenPortada
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.uv.enzona.data.Guias
import com.uv.enzona.ui.components.GuiaDePantalla
import com.uv.enzona.ui.components.PasoGuia
import com.uv.enzona.ui.components.objetivoDeGuia
import androidx.compose.ui.text.input.KeyboardType
import com.uv.enzona.data.DatosEvento
import com.uv.enzona.data.Repositorio
import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.Evento
import com.uv.enzona.ui.components.EsperaDeCarga
import com.uv.enzona.ui.components.rememberCarga
import com.uv.enzona.ui.components.rememberOperacion
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

private data class DatosFormulario(val existente: Evento?, val plano: List<Asiento>, val categorias: List<String>)

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
    // Primero se cargan el catálogo de categorías y, al editar, el evento con su plano;
    // el formulario se arma cuando ya están, para que sus campos nazcan con esos valores.
    val carga = rememberCarga(eventoId) {
        val repositorio = Repositorio.actual
        val existente = eventoId?.let { repositorio.evento(it) }
        DatosFormulario(
            existente = existente,
            plano = if (existente?.requiereAsiento == true) repositorio.asientosDe(existente.id) else emptyList(),
            categorias = repositorio.categorias().map { it.nombre },
        )
    }
    val datos = carga.valor
    if (datos == null) {
        EsperaDeCarga(carga, titulo = if (eventoId == null) "Nuevo evento" else "Editar evento", onVolver = onVolver)
        return
    }
    Formulario(eventoId, datos, onVolver, onGuardado)
}

@Composable
private fun Formulario(eventoId: Long?, datos: DatosFormulario, onVolver: () -> Unit, onGuardado: () -> Unit) {
    val existente = datos.existente
    val sinBoletos = existente == null || !existente.tieneBoletos
    val hoy = FechaEvento.ahora().toLocalDate()
    val CATEGORIAS = datos.categorias.ifEmpty { listOf("Cultural") }
    val operacion = rememberOperacion()

    var nombre by rememberSaveable { mutableStateOf(existente?.nombre ?: "") }
    var descripcion by rememberSaveable { mutableStateOf(existente?.descripcion ?: "") }
    var lugar by rememberSaveable { mutableStateOf(existente?.lugar ?: "") }
    var direccion by rememberSaveable { mutableStateOf(existente?.direccion ?: "") }
    // Última dirección propuesta por el mapa: permite distinguir texto automático de texto escrito por el usuario
    var direccionSugerida by rememberSaveable { mutableStateOf<String?>(null) }
    var categoria by rememberSaveable { mutableStateOf(existente?.categoria?.takeIf { it in CATEGORIAS } ?: CATEGORIAS.first()) }
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
    val mapaActual = datos.plano
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

    fun datosDelFormulario() = DatosEvento(
        nombre = nombre.trim(), descripcion = descripcion.trim(), lugar = lugar.trim(), direccion = direccion.trim(),
        categoria = categoria, fecha = inicio!!, fechaFin = fin,
        esDePago = esDePago, precio = precioNum, aforo = aforoNum,
        latitud = latitud, longitud = longitud,
        secciones = if (requiereAsiento) listOf(LayoutSeccion(seccion.trim(), filasNum, porFilaNum)) else emptyList(),
    )

    // --- Portada (v6): se elige en el teléfono y se sube a la API después de crear o guardar el evento ---
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    // La imagen elegida se conserva ya reducida; el encuadre (bias -1..1) se aplica al guardar
    var mapaPortada by remember { mutableStateOf<Bitmap?>(null) }
    var encuadre by remember { mutableStateOf(ImagenPortada.Encuadre()) }
    var quitarImagen by rememberSaveable { mutableStateOf(false) }
    var preparandoImagen by remember { mutableStateOf(false) }
    var avisoImagen by remember { mutableStateOf<String?>(null) }
    val portadaActual: String? = existente?.imagenUrl?.takeUnless { quitarImagen }
    val hayPortada = mapaPortada != null || portadaActual != null
    // El selector de fotos del sistema no necesita permisos: la persona elige la foto y solo esa se comparte con la app
    val elegirImagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            avisoImagen = "No se eligió ninguna imagen."
            return@rememberLauncherForActivityResult
        }
        preparandoImagen = true
        avisoImagen = null
        alcance.launch {
            try {
                mapaPortada = ImagenPortada.cargar(contexto, uri)
                encuadre = ImagenPortada.Encuadre()
                quitarImagen = false
                error = null
                avisoImagen = "Imagen lista. Muévela, acércala o gírala hasta que quede como quieres; se guardará al ${if (existente == null) "crear" else "guardar"} el evento."
            } catch (e: Exception) {
                error = e.message ?: "No se pudo preparar la imagen."
            } finally {
                preparandoImagen = false
            }
        }
    }

    /** Sube la portada elegida (recortada con el encuadre) o la quita, una vez que el evento existe en el servidor. */
    suspend fun aplicarPortada(evento: Evento): Evento {
        val repositorio = Repositorio.actual
        val mapa = mapaPortada
        return when {
            mapa != null -> repositorio.subirImagenEvento(evento.id, ImagenPortada.recortar(contexto, mapa, encuadre))
            quitarImagen && existente?.imagenUrl != null -> repositorio.quitarImagenEvento(evento.id)
            else -> evento
        }
    }

    // Qué impide crear o guardar: se muestra junto a los botones en lugar de dejarlos deshabilitados sin explicación
    val faltantes = buildList {
        if (nombre.isBlank()) add("el nombre")
        if (lugar.isBlank()) add("el lugar")
        if (inicio == null) add("la fecha y la hora de inicio") else errorFecha?.let { add(it.trimEnd('.').replaceFirstChar { c -> c.lowercase() }) }
        if (requiereAsiento && !asientosValidos) add("una sección válida (de 1 a 26 filas y de 1 a 40 asientos por fila)")
        else if (aforoNum <= 0) add(if (requiereAsiento) "las filas y los asientos por fila" else "el aforo")
        if (esDePago && precioNum <= 0) add("el precio del boleto")
    }

    fun crear(publicar: Boolean) {
        error = null
        operacion.ejecutar(
            alTerminar = { _: Evento -> onGuardado() },
            alFallar = { error = it.message },
        ) { aplicarPortada(Repositorio.actual.crearEvento(datosDelFormulario(), publicar)) }
    }

    fun guardarCambios() {
        val ev = existente ?: return
        error = null
        val planoCambio = sinBoletos && (requiereAsiento != ev.requiereAsiento ||
            (requiereAsiento && (filasNum * porFilaNum != mapaActual.size || seccion.trim() != mapaActual.firstOrNull()?.seccion)))
        operacion.ejecutar(
            alTerminar = { _: Evento -> onGuardado() },
            alFallar = { error = it.message },
        ) {
            val repositorio = Repositorio.actual
            val datosNuevos = datosDelFormulario()
            // El plano se cambia primero: ajusta el aforo, y después se guardan los demás datos
            if (planoCambio) repositorio.cambiarAsientos(ev.id, datosNuevos.secciones)
            aplicarPortada(repositorio.modificarEvento(ev.id, datosNuevos))
        }
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

            // Guía de la primera vez, solo al crear (al editar ya se conoce el formulario)
            GuiaDePantalla(
                clave = Guias.Clave.NUEVO_EVENTO,
                activa = existente == null,
                pasos = listOf(
                    PasoGuia("evento_portada", "Ponle una foto (si quieres)", "Elige una imagen de tu teléfono y acomódala: arrástrala, acércala o gírala. Si no pones foto, no pasa nada: se usa un diseño de la categoría."),
                    PasoGuia("evento_fecha", "¿Cuándo es?", "Toca para elegir el día en el calendario y la hora en el reloj. Solo se pueden elegir fechas que todavía no pasan."),
                    PasoGuia("evento_ubicacion", "¿Dónde es?", "Escribe el nombre del lugar y toca el mapa para poner el marcador justo donde es. Sin marcador puedes guardar el evento, pero no publicarlo."),
                    PasoGuia("evento_pago", "¿Es gratis o se cobra?", "Si vas a cobrar, activa esto y pon el precio del boleto. Piénsalo bien: después de crear el evento ya no se puede cambiar."),
                    PasoGuia("evento_asientos", "¿Hay asientos numerados?", "Si cada persona tendrá su asiento, activa esto y di cuántas filas hay y cuántos asientos por fila; el plano se arma solo. Si no, solo pon cuántas personas caben."),
                    PasoGuia("evento_acciones", "Para terminar", "«Crear y publicar» lo hace visible para todos desde ya. «Guardar como borrador» lo guarda solo para ti, por si quieres terminarlo después."),
                ),
            )

            // ------------------ Portada ------------------
            TarjetaEnZona(modifier = Modifier.objetivoDeGuia("evento_portada")) {
                Text("Portada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Espacio.s))
                val mapa = mapaPortada
                if (mapa != null) {
                    EncuadrePortada(mapa = mapa, encuadre = encuadre, onEncuadre = { encuadre = it })
                    Spacer(Modifier.height(Espacio.xs))
                    // Acercar: el mismo control que el gesto de pellizco, para quien prefiera un dedo
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                        Text("Acercar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = encuadre.zoom,
                            onValueChange = { encuadre = encuadre.copy(zoom = it) },
                            valueRange = 1f..ImagenPortada.ZOOM_MAXIMO,
                            enabled = !operacion.enCurso && !preparandoImagen,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Acercar la portada" },
                        )
                        TextButton(
                            enabled = !operacion.enCurso && !preparandoImagen,
                            onClick = {
                                preparandoImagen = true
                                alcance.launch {
                                    try {
                                        mapaPortada = ImagenPortada.girar(mapa)
                                        encuadre = ImagenPortada.Encuadre(zoom = encuadre.zoom)
                                    } finally {
                                        preparandoImagen = false
                                    }
                                }
                            },
                        ) { Text("Girar") }
                    }
                } else {
                    PortadaEvento(
                        nombre = nombre.ifBlank { "Evento" }, categoria = categoria,
                        fecha = inicio ?: FechaEvento.ahora(), imagenUrl = portadaActual,
                        cargando = preparandoImagen, mostrarFecha = false,
                    )
                }
                Spacer(Modifier.height(Espacio.s))
                BotonSecundario(
                    texto = if (!hayPortada) "Elegir imagen" else "Cambiar imagen",
                    habilitado = !operacion.enCurso && !preparandoImagen,
                    onClick = { elegirImagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                if (hayPortada) {
                    TextButton(
                        enabled = !operacion.enCurso,
                        onClick = { mapaPortada = null; quitarImagen = true; avisoImagen = "La portada se quitará al guardar." },
                    ) { Text("Quitar portada", color = MaterialTheme.colorScheme.error) }
                }
                avisoImagen?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                Text(
                    if (mapa != null) "Arrastra la imagen para moverla, junta o separa dos dedos para acercarla y usa «Girar» si está de lado. Se guarda en 16:9 tal como la ves aquí."
                    else "Opcional. JPEG, PNG o WebP; se reduce en el teléfono y la guarda el servidor. Sin imagen se muestra el panel de la categoría.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ------------------ Fecha y hora ------------------
            TarjetaEnZona(modifier = Modifier.objetivoDeGuia("evento_fecha")) {
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
            TarjetaEnZona(modifier = Modifier.objetivoDeGuia("evento_ubicacion")) {
                Text("Ubicación", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Espacio.s))
                CampoEnZona(lugar, { lugar = it }, "Lugar (nombre del recinto)")
                Spacer(Modifier.height(Espacio.s))
                CampoEnZona(direccion, { direccion = it }, "Dirección", ayuda = "Puedes editar la dirección sugerida por el mapa.")
                Spacer(Modifier.height(Espacio.m))
                SelectorUbicacionMapa(
                    latitud = latitud, longitud = longitud,
                    onCambio = { lat, lon -> latitud = lat; longitud = lon; error = null },
                    onSugerenciaDireccion = { sugerida ->
                        // Se actualiza al mover el marcador salvo que el usuario haya editado la dirección a mano:
                        // si el campo está vacío o aún contiene la sugerencia anterior, se reemplaza; si no, se respeta.
                        if (direccion.isBlank() || direccion == direccionSugerida) direccion = sugerida
                        direccionSugerida = sugerida
                    },
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
            TarjetaEnZona(modifier = Modifier.objetivoDeGuia("evento_pago")) {
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
            TarjetaEnZona(modifier = Modifier.objetivoDeGuia("evento_asientos")) {
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
            if (faltantes.isNotEmpty()) {
                Text(
                    "Para ${if (existente == null) "crear" else "guardar"} el evento falta: ${faltantes.joinToString(", ")}.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
            if (existente == null) {
                Column(Modifier.objetivoDeGuia("evento_acciones")) {
                BotonEnZona(texto = "Crear y publicar", habilitado = puedePublicar, cargando = operacion.enCurso, destacado = true, onClick = { crear(true) })
                if (datosValidos && !tieneUbicacion) {
                    Text(
                        "Publicar está bloqueado hasta fijar la ubicación en el mapa. Puedes guardarlo como borrador.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                BotonSecundario(texto = "Guardar como borrador", habilitado = datosValidos && !operacion.enCurso, onClick = { crear(false) })
                }
            } else {
                BotonEnZona(
                    texto = "Guardar cambios",
                    habilitado = datosValidos && (existente.estado.name != "PUBLICADO" || tieneUbicacion),
                    cargando = operacion.enCurso,
                    destacado = true,
                    onClick = { if (fechaCambio && existente.vendidos > 0) confirmarCambioFecha = true else guardarCambios() },
                )
            }
            Spacer(Modifier.height(Espacio.xl))
        }
    }
}

/**
 * Vista previa 16:9 de la portada elegida. Dibuja exactamente el rectángulo que
 * `ImagenPortada.recortar` guardará (mismo cálculo, `rectanguloRecorte`), así lo
 * que se ve es lo que se sube. Un dedo mueve la imagen; dos dedos la acercan.
 */
@Composable
private fun EncuadrePortada(mapa: Bitmap, encuadre: ImagenPortada.Encuadre, onEncuadre: (ImagenPortada.Encuadre) -> Unit) {
    val imagen = remember(mapa) { mapa.asImageBitmap() }
    // El bloque de gestos se crea una sola vez: lee el encuadre vigente a través de estos estados
    val encuadreActual by rememberUpdatedState(encuadre)
    val alCambiar by rememberUpdatedState(onEncuadre)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ImagenPortada.PROPORCION)
            .clip(MaterialTheme.shapes.large)
            .pointerInput(mapa) {
                detectTransformGestures { _, arrastre, pellizco, _ ->
                    if (size.width == 0) return@detectTransformGestures
                    val e = encuadreActual
                    val zoom = (e.zoom * pellizco).coerceIn(1f, ImagenPortada.ZOOM_MAXIMO)
                    val r = ImagenPortada.rectanguloRecorte(mapa.width, mapa.height, ImagenPortada.PROPORCION, e.biasX, e.biasY, zoom)
                    // Un píxel del marco equivale a (ancho del recorte / ancho del marco) píxeles de la imagen.
                    // Arrastrar hacia la derecha muestra la parte izquierda: el recorte se mueve al revés del dedo.
                    val factor = r.ancho.toFloat() / size.width
                    val sobranteX = mapa.width - r.ancho
                    val sobranteY = mapa.height - r.alto
                    val biasX = if (sobranteX > 0) (e.biasX - arrastre.x * factor * 2f / sobranteX).coerceIn(-1f, 1f) else 0f
                    val biasY = if (sobranteY > 0) (e.biasY - arrastre.y * factor * 2f / sobranteY).coerceIn(-1f, 1f) else 0f
                    alCambiar(ImagenPortada.Encuadre(biasX, biasY, zoom))
                }
            }
            .semantics { contentDescription = "Vista previa de la portada; arrastra para moverla y pellizca para acercarla" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = ImagenPortada.rectanguloRecorte(mapa.width, mapa.height, ImagenPortada.PROPORCION, encuadre.biasX, encuadre.biasY, encuadre.zoom)
            drawImage(
                image = imagen,
                srcOffset = IntOffset(r.x, r.y),
                srcSize = IntSize(r.ancho, r.alto),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
