package com.uv.enzona.ui.screens.validator

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Vista previa de la cámara que detecta códigos QR con CameraX + ML Kit.
 * Llama a [onCodigo] cuando lee un código, mientras [activo] sea verdadero.
 */
@Composable
fun CamaraQr(
    modifier: Modifier = Modifier,
    activo: Boolean,
    onCodigo: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    val lector: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    // El analizador se crea una sola vez: estas referencias mantienen
    // actualizados los valores que consulta en cada fotograma.
    val activoActual by rememberUpdatedState(activo)
    val onCodigoActual by rememberUpdatedState(onCodigo)
    val proveedorRef = remember { AtomicReference<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // 1) dejar de recibir fotogramas, 2) esperar al que esté en curso,
            // 3) recién entonces cerrar el lector (si no, ML Kit lanza excepción).
            proveedorRef.get()?.unbindAll()
            ejecutor.shutdown()
            runCatching { ejecutor.awaitTermination(500, TimeUnit.MILLISECONDS) }
            runCatching { lector.close() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { contexto ->
            val vista = PreviewView(contexto).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val futuro = ProcessCameraProvider.getInstance(contexto)
            futuro.addListener({
                val proveedor = futuro.get()
                proveedorRef.set(proveedor)

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(vista.surfaceProvider)
                }
                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analizador ->
                        analizador.setAnalyzer(ejecutor) { imagen ->
                            procesar(imagen, lector, { activoActual }, { onCodigoActual(it) })
                        }
                    }

                try {
                    proveedor.unbindAll()
                    proveedor.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(contexto))
            vista
        }
    )
}

private fun procesar(
    imagen: ImageProxy,
    lector: BarcodeScanner,
    estaActivo: () -> Boolean,
    onCodigo: (String) -> Unit,
) {
    if (!estaActivo()) {
        imagen.close()
        return
    }
    // toBitmap() evita la API experimental de acceso directo a la imagen
    val entrada = try {
        InputImage.fromBitmap(imagen.toBitmap(), imagen.imageInfo.rotationDegrees)
    } catch (e: Exception) {
        imagen.close()
        return
    }
    try {
        lector.process(entrada)
            .addOnSuccessListener { codigos ->
                if (estaActivo()) codigos.firstOrNull()?.rawValue?.let(onCodigo)
            }
            .addOnCompleteListener { imagen.close() }
    } catch (e: IllegalStateException) {
        // El lector ya se cerró porque la pantalla se está destruyendo
        imagen.close()
    }
}
