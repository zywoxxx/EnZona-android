package com.uv.enzona.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Genera el Bitmap del código QR de un boleto usando ZXing.
 * El contenido es "codigo.firma" (ver Boleto.contenidoQr).
 */
object QrGenerator {

    fun generar(contenido: String, tamanoPx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matriz = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, tamanoPx, tamanoPx, hints)
        val pixeles = IntArray(tamanoPx * tamanoPx)
        for (y in 0 until tamanoPx) {
            for (x in 0 until tamanoPx) {
                pixeles[y * tamanoPx + x] = if (matriz[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(tamanoPx, tamanoPx, Bitmap.Config.RGB_565).apply {
            setPixels(pixeles, 0, tamanoPx, 0, 0, tamanoPx, tamanoPx)
        }
    }
}
