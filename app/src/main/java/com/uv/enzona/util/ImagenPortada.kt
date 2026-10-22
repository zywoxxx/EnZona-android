package com.uv.enzona.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.ImagenParaSubir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Portada de un evento elegida en el teléfono.
 *
 * 1. [cargar] la endereza según su orientación EXIF (las fotos de la cámara se
 *    guardan giradas) y la reduce a un tamaño razonable para la red (lado mayor
 *    de 1600 px).
 * 2. La persona la encuadra en la vista previa 16:9: la arrastra, la acerca con
 *    dos dedos o el control deslizante y la gira de 90 en 90 grados ([girar]).
 * 3. [recortar] corta exactamente ese encuadre, la convierte a JPEG y la deja
 *    lista para `POST /api/eventos/{id}/imagen`. La copia recortada se guarda en
 *    la caché de la app para previsualizarla y para los datos de prueba.
 */
object ImagenPortada {

    /** Misma proporción con la que se muestran las portadas (16:9). */
    const val PROPORCION = 16f / 9f

    /** Acercamiento máximo: el recorte puede ser hasta cuatro veces más pequeño que la imagen. */
    const val ZOOM_MAXIMO = 4f

    private const val LADO_MAXIMO = 1600
    private const val CALIDAD_JPEG = 85

    /** Zona de la imagen original que queda dentro del encuadre, en píxeles. */
    data class Recorte(val x: Int, val y: Int, val ancho: Int, val alto: Int)

    /**
     * Encuadre elegido por la persona. `biasX`/`biasY` van de -1 (borde izquierdo o
     * superior) a 1 (borde derecho o inferior), como en `BiasAlignment` de Compose;
     * `zoom` es 1 cuando el recorte es el mayor posible y hasta [ZOOM_MAXIMO].
     */
    data class Encuadre(val biasX: Float = 0f, val biasY: Float = 0f, val zoom: Float = 1f)

    suspend fun cargar(contexto: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val resolver = contexto.contentResolver
        // Primero solo las dimensiones, para decidir cuánto reducir sin cargar la imagen completa.
        // Con inJustDecodeBounds el resultado es siempre null: lo que importa son las dimensiones leídas.
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val entrada = resolver.openInputStream(uri) ?: throw ErrorEnZona(0, "IMAGEN_INVALIDA", "No se pudo abrir la imagen elegida.")
        entrada.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            throw ErrorEnZona(0, "IMAGEN_INVALIDA", "El archivo elegido no es una imagen.")
        }
        val opciones = BitmapFactory.Options().apply { inSampleSize = factorDeReduccion(limites.outWidth, limites.outHeight) }
        val mapa = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: throw ErrorEnZona(0, "IMAGEN_INVALIDA", "No se pudo leer la imagen elegida.")
        // BitmapFactory ignora la orientación EXIF: una foto vertical de la cámara llega acostada
        val orientacion = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        ajustar(enderezar(mapa, orientacion))
    }

    /** Gira la imagen 90° en el sentido de las manecillas del reloj (botón «Girar»). */
    suspend fun girar(mapa: Bitmap): Bitmap = withContext(Dispatchers.Default) { transformar(mapa, 90, espejo = false) }

    /**
     * Recorta el encuadre elegido, para que lo que se ve en la vista previa sea
     * exactamente lo que se guarda.
     */
    suspend fun recortar(contexto: Context, mapa: Bitmap, encuadre: Encuadre): ImagenParaSubir = withContext(Dispatchers.Default) {
        val r = rectanguloRecorte(mapa.width, mapa.height, PROPORCION, encuadre.biasX, encuadre.biasY, encuadre.zoom)
        val recortado = if (r.ancho == mapa.width && r.alto == mapa.height) mapa else Bitmap.createBitmap(mapa, r.x, r.y, r.ancho, r.alto)
        val salida = ByteArrayOutputStream()
        recortado.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
        if (recortado !== mapa) recortado.recycle()
        val bytes = salida.toByteArray()
        val copia = File(contexto.cacheDir, "portada_${System.currentTimeMillis()}.jpg").apply { writeBytes(bytes) }
        ImagenParaSubir(bytes, "image/jpeg", Uri.fromFile(copia).toString())
    }

    suspend fun recortar(contexto: Context, mapa: Bitmap, biasX: Float, biasY: Float): ImagenParaSubir =
        recortar(contexto, mapa, Encuadre(biasX, biasY))

    /** Carga y recorta centrada, para quien no quiera encuadrar. */
    suspend fun preparar(contexto: Context, uri: Uri): ImagenParaSubir = recortar(contexto, cargar(contexto, uri), Encuadre())

    /**
     * Rectángulo de `proporcion` que cabe en la imagen (el mayor posible dividido
     * entre `zoom`), desplazado según el bias. Función pura para poder probarla.
     */
    fun rectanguloRecorte(ancho: Int, alto: Int, proporcion: Float, biasX: Float, biasY: Float, zoom: Float = 1f): Recorte {
        val bx = biasX.coerceIn(-1f, 1f)
        val by = biasY.coerceIn(-1f, 1f)
        val z = zoom.coerceIn(1f, ZOOM_MAXIMO)
        val anchoBase: Float
        val altoBase: Float
        if (ancho.toFloat() / alto > proporcion) {
            // Más ancha que el encuadre: se recorta a los lados
            altoBase = alto.toFloat()
            anchoBase = alto * proporcion
        } else {
            // Más alta que el encuadre: se recorta arriba y abajo
            anchoBase = ancho.toFloat()
            altoBase = ancho / proporcion
        }
        val anchoRecorte = (anchoBase / z).roundToInt().coerceIn(1, ancho)
        val altoRecorte = (altoBase / z).roundToInt().coerceIn(1, alto)
        val sobranteX = ancho - anchoRecorte
        val sobranteY = alto - altoRecorte
        val x = ((bx + 1f) / 2f * sobranteX).roundToInt().coerceIn(0, sobranteX)
        val y = ((by + 1f) / 2f * sobranteY).roundToInt().coerceIn(0, sobranteY)
        return Recorte(x, y, anchoRecorte, altoRecorte)
    }

    /** Potencia de 2 que deja el lado mayor por debajo del doble del máximo (el ajuste fino lo hace [ajustar]). */
    internal fun factorDeReduccion(ancho: Int, alto: Int): Int {
        var factor = 1
        while (maxOf(ancho, alto) / (factor * 2) >= LADO_MAXIMO) factor *= 2
        return factor
    }

    /** Giro (grados en sentido horario) y espejo horizontal que deja derecha una imagen con esa orientación EXIF. */
    internal fun transformacionExif(orientacion: Int): Pair<Int, Boolean> = when (orientacion) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90 to false
        ExifInterface.ORIENTATION_ROTATE_180 -> 180 to false
        ExifInterface.ORIENTATION_ROTATE_270 -> 270 to false
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 to true
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180 to true
        ExifInterface.ORIENTATION_TRANSPOSE -> 90 to true
        ExifInterface.ORIENTATION_TRANSVERSE -> 270 to true
        else -> 0 to false
    }

    private fun enderezar(mapa: Bitmap, orientacion: Int): Bitmap {
        val (grados, espejo) = transformacionExif(orientacion)
        return if (grados == 0 && !espejo) mapa else transformar(mapa, grados, espejo)
    }

    private fun transformar(mapa: Bitmap, grados: Int, espejo: Boolean): Bitmap {
        val matriz = Matrix()
        if (espejo) matriz.preScale(-1f, 1f)
        if (grados != 0) matriz.postRotate(grados.toFloat())
        val resultado = Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, matriz, true)
        if (resultado !== mapa) mapa.recycle()
        return resultado
    }

    private fun ajustar(mapa: Bitmap): Bitmap {
        val mayor = maxOf(mapa.width, mapa.height)
        if (mayor <= LADO_MAXIMO) return mapa
        val escala = LADO_MAXIMO.toFloat() / mayor
        val reducido = Bitmap.createScaledBitmap(mapa, (mapa.width * escala).toInt().coerceAtLeast(1), (mapa.height * escala).toInt().coerceAtLeast(1), true)
        if (reducido !== mapa) mapa.recycle()
        return reducido
    }
}
