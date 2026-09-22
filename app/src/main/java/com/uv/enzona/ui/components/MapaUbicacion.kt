package com.uv.enzona.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.compose.ui.unit.dp
import com.uv.enzona.ui.theme.Espacio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import java.util.concurrent.Executors

/*
 * Ubicación exacta del evento en mapa (v3, cambio 3; RF-08 + RF-19).
 *
 * Biblioteca: osmdroid (OpenStreetMap). No hay clave de Google Maps en el
 * repositorio, así que no se usa maps-compose. osmdroid no requiere clave; los
 * teselas vienen de los servidores de OSM y se muestra la atribución
 * «© OpenStreetMap contributors» exigida por su licencia (ODbL). Compatible
 * con API 26.
 */

/** Centro del área de estudio: Orizaba, Veracruz. */
object Orizaba {
    const val LATITUD = 18.851
    const val LONGITUD = -97.100
    const val ZOOM_CIUDAD = 14.0

    /** ¿Está dentro del área de estudio (≈ 12 km alrededor del centro)? Solo informativo. */
    fun dentroDelArea(lat: Double, lon: Double): Boolean {
        val r = FloatArray(1)
        Location.distanceBetween(LATITUD, LONGITUD, lat, lon, r)
        return r[0] <= 12_000f
    }
}

/** Redondeo a 5 decimales (≈ 1 m) para almacenar coordenadas. */
fun redondear5(v: Double): Double = Math.round(v * 100_000.0) / 100_000.0

private fun configurarOsm(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName   // requisito de la política de teselas de OSM
        osmdroidBasePath = context.cacheDir
        osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid")
    }
}

/**
 * Selector interactivo: tocar o arrastrar el marcador fija la ubicación.
 * Muestra coordenadas con 5 decimales y una dirección aproximada (Geocoder)
 * como sugerencia editable. "Usar mi ubicación actual" pide el permiso solo al
 * pulsarlo; si se niega, la selección manual sigue funcionando (CP-18).
 */
@Composable
fun SelectorUbicacionMapa(
    latitud: Double?,
    longitud: Double?,
    onCambio: (lat: Double, lon: Double) -> Unit,
    onSugerenciaDireccion: (String) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var avisoPermiso by remember { mutableStateOf<String?>(null) }
    var buscandoUbicacion by remember { mutableStateOf(false) }
    var sugerencia by remember { mutableStateOf<String?>(null) }
    val colorMarcador = MaterialTheme.colorScheme.tertiary.toArgb()

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) {
            buscandoUbicacion = true
            obtenerUbicacion(contexto) { loc ->
                buscandoUbicacion = false
                if (loc == null) avisoPermiso = "No se pudo obtener la ubicación ahora. Fija el punto en el mapa."
                else onCambio(redondear5(loc.latitude), redondear5(loc.longitude))
            }
        } else {
            avisoPermiso = "Sin permiso de ubicación: elige el punto tocando el mapa."
        }
    }

    // Dirección aproximada cuando cambian las coordenadas (sugerencia, editable en el campo Dirección)
    LaunchedEffect(latitud, longitud) {
        if (latitud == null || longitud == null) { sugerencia = null; return@LaunchedEffect }
        sugerencia = withContext(Dispatchers.IO) { direccionAproximada(contexto, latitud, longitud) }
        sugerencia?.let(onSugerenciaDireccion)
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(MaterialTheme.shapes.medium)
                .semantics { contentDescription = "Mapa para fijar la ubicación del evento. Toca para colocar el marcador." },
        ) {
            AndroidView(
                factory = { ctx ->
                    configurarOsm(ctx)
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                        controller.setZoom(if (latitud != null) 16.0 else Orizaba.ZOOM_CIUDAD)
                        controller.setCenter(GeoPoint(latitud ?: Orizaba.LATITUD, longitud ?: Orizaba.LONGITUD))
                        overlays.add(CopyrightOverlay(ctx))
                        val marcador = Marker(this).apply {
                            id = "evento"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            isDraggable = true
                            title = "Ubicación del evento"
                            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                override fun onMarkerDrag(marker: Marker) {}
                                override fun onMarkerDragStart(marker: Marker) {}
                                override fun onMarkerDragEnd(marker: Marker) {
                                    onCambio(redondear5(marker.position.latitude), redondear5(marker.position.longitude))
                                }
                            })
                        }
                        if (latitud != null && longitud != null) {
                            marcador.position = GeoPoint(latitud, longitud)
                            overlays.add(marcador)
                        }
                        overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                if (!habilitado) return false
                                onCambio(redondear5(p.latitude), redondear5(p.longitude))
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        }))
                        tag = marcador
                    }
                },
                update = { mapa ->
                    val marcador = mapa.tag as? Marker ?: return@AndroidView
                    if (latitud != null && longitud != null) {
                        marcador.position = GeoPoint(latitud, longitud)
                        if (marcador !in mapa.overlays) mapa.overlays.add(marcador)
                        mapa.controller.animateTo(marcador.position)
                    } else {
                        mapa.overlays.remove(marcador)
                    }
                    mapa.invalidate()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(Espacio.s))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (latitud != null && longitud != null) "Lat ${"%.5f".format(Locale.US, latitud)}, Lon ${"%.5f".format(Locale.US, longitud)}"
                    else "Sin ubicación: toca el mapa para colocar el marcador.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sugerencia != null) {
                    Text("Aprox.: $sugerencia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (latitud != null && longitud != null && !Orizaba.dentroDelArea(latitud, longitud)) {
                    Text(
                        "El punto queda fuera del área de estudio (Orizaba). Se guardará de todos modos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(Espacio.s))
        BotonSecundario(
            texto = if (buscandoUbicacion) "Buscando tu ubicación…" else "Usar mi ubicación actual",
            icono = Icons.Filled.MyLocation,
            habilitado = habilitado && !buscandoUbicacion,
            onClick = {
                avisoPermiso = null
                val concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (concedido) {
                    buscandoUbicacion = true
                    alcance.launch {
                        obtenerUbicacion(contexto) { loc ->
                            buscandoUbicacion = false
                            if (loc == null) avisoPermiso = "No se pudo obtener la ubicación ahora. Fija el punto en el mapa."
                            else onCambio(redondear5(loc.latitude), redondear5(loc.longitude))
                        }
                    }
                } else {
                    pedirPermiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
        )
        if (avisoPermiso != null) {
            Spacer(Modifier.height(Espacio.s))
            Aviso(texto = avisoPermiso!!, tono = Tono.Aviso)
        }
        Spacer(Modifier.height(Espacio.xs))
        Text(
            "Mapa © OpenStreetMap contributors (ODbL).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Miniatura no interactiva con el marcador del evento (detalle) + botón "Cómo llegar". */
@Composable
fun MiniMapaEvento(
    latitud: Double,
    longitud: Double,
    nombre: String,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium)
                .semantics { contentDescription = "Mapa con la ubicación de $nombre" },
        ) {
            AndroidView(
                factory = { ctx ->
                    configurarOsm(ctx)
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        setOnTouchListener { _, _ -> true }   // estático: el detalle no se desplaza con el mapa
                        controller.setZoom(16.0)
                        controller.setCenter(GeoPoint(latitud, longitud))
                        overlays.add(CopyrightOverlay(ctx))
                        overlays.add(Marker(this).apply {
                            position = GeoPoint(latitud, longitud)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = nombre
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(Espacio.s))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            BotonSecundario(
                texto = "Cómo llegar",
                icono = Icons.Filled.Directions,
                modifier = Modifier.weight(1f),
                onClick = {
                    // Intent geo: genérico; el sistema ofrece las apps de mapas instaladas
                    val uri = Uri.parse("geo:$latitud,$longitud?q=$latitud,$longitud(${Uri.encode(nombre)})")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    runCatching { contexto.startActivity(Intent.createChooser(intent, "Cómo llegar")) }
                },
            )
            Spacer(Modifier.width(Espacio.s))
            Text(
                "Lat ${"%.5f".format(Locale.US, latitud)}\nLon ${"%.5f".format(Locale.US, longitud)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ utilidades

/** Última ubicación conocida o una lectura única (sin Play Services; API 26+). */
private fun obtenerUbicacion(contexto: Context, callback: (Location?) -> Unit) {
    val lm = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return callback(null)
    val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
    if (proveedores.isEmpty()) return callback(null)
    try {
        @Suppress("MissingPermission")
        val ultima = proveedores.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (ultima != null) return callback(ultima)
        @Suppress("MissingPermission")
        LocationManagerCompat.getCurrentLocation(
            lm, proveedores.first(), CancellationSignal(), Executors.newSingleThreadExecutor(),
        ) { loc -> callback(loc) }
    } catch (_: SecurityException) {
        callback(null)
    }
}

/** Dirección aproximada vía Geocoder (puede no estar disponible en el dispositivo). */
private fun direccionAproximada(contexto: Context, lat: Double, lon: Double): String? {
    if (!Geocoder.isPresent()) return null
    return try {
        val geocoder = Geocoder(contexto, Locale("es", "MX"))
        @Suppress("DEPRECATION")
        val r = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
        listOfNotNull(r.thoroughfare, r.subThoroughfare, r.subLocality, r.locality).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
    } catch (_: Exception) {
        null
    }
}

/** Solo para diagnósticos: versión mínima requerida por osmdroid. */
internal val soportaMapas: Boolean get() = Build.VERSION.SDK_INT >= 26
