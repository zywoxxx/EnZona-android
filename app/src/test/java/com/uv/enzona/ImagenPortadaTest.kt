package com.uv.enzona

import android.media.ExifInterface
import com.uv.enzona.util.ImagenPortada
import org.junit.Assert.assertEquals
import org.junit.Test

/** Encuadre de la portada: el recorte 16:9 sigue al bias y al zoom igual que la vista previa. */
class ImagenPortadaTest {

    private val p = ImagenPortada.PROPORCION

    @Test
    fun unaFotoVerticalSeRecortaArribaYAbajoSegunElBias() {
        // 900 x 1600: el recorte 16:9 mide 900 x 506 y sobran 1094 px de alto
        assertEquals(ImagenPortada.Recorte(0, 0, 900, 506), ImagenPortada.rectanguloRecorte(900, 1600, p, 0f, -1f))
        assertEquals(ImagenPortada.Recorte(0, 547, 900, 506), ImagenPortada.rectanguloRecorte(900, 1600, p, 0f, 0f))
        assertEquals(ImagenPortada.Recorte(0, 1094, 900, 506), ImagenPortada.rectanguloRecorte(900, 1600, p, 0f, 1f))
        // En el eje sin sobrante el bias no mueve nada
        assertEquals(0, ImagenPortada.rectanguloRecorte(900, 1600, p, 1f, 0f).x)
    }

    @Test
    fun unaFotoPanoramicaSeRecortaALosLados() {
        // 1600 x 400: el recorte mide 711 x 400 y sobran 889 px de ancho
        assertEquals(ImagenPortada.Recorte(0, 0, 711, 400), ImagenPortada.rectanguloRecorte(1600, 400, p, -1f, 0f))
        assertEquals(ImagenPortada.Recorte(889, 0, 711, 400), ImagenPortada.rectanguloRecorte(1600, 400, p, 1f, 0f))
        assertEquals(ImagenPortada.Recorte(445, 0, 711, 400), ImagenPortada.rectanguloRecorte(1600, 400, p, 0f, 5f))   // el bias se acota
    }

    @Test
    fun unaImagenYa16a9NoSeRecorta() {
        assertEquals(ImagenPortada.Recorte(0, 0, 1600, 900), ImagenPortada.rectanguloRecorte(1600, 900, p, 0.7f, -0.3f))
        assertEquals(1, ImagenPortada.factorDeReduccion(1600, 900))
        assertEquals(2, ImagenPortada.factorDeReduccion(4000, 3000))
        assertEquals(4, ImagenPortada.factorDeReduccion(8000, 6000))
    }

    @Test
    fun elZoomReduceElRecorteYPermiteMoverloEnAmbosEjes() {
        // 1600 x 900 con zoom 2: el recorte mide 800 x 450 y ahora sobra imagen en los dos ejes
        assertEquals(ImagenPortada.Recorte(400, 225, 800, 450), ImagenPortada.rectanguloRecorte(1600, 900, p, 0f, 0f, 2f))
        assertEquals(ImagenPortada.Recorte(0, 0, 800, 450), ImagenPortada.rectanguloRecorte(1600, 900, p, -1f, -1f, 2f))
        assertEquals(ImagenPortada.Recorte(800, 450, 800, 450), ImagenPortada.rectanguloRecorte(1600, 900, p, 1f, 1f, 2f))
        // El zoom se acota entre 1 y el máximo
        assertEquals(ImagenPortada.Recorte(0, 0, 1600, 900), ImagenPortada.rectanguloRecorte(1600, 900, p, 0f, 0f, 0.5f))
        assertEquals(ImagenPortada.Recorte(600, 338, 400, 225), ImagenPortada.rectanguloRecorte(1600, 900, p, 0f, 0f, 99f))
    }

    @Test
    fun laOrientacionExifSeTraduceAGiroYEspejo() {
        assertEquals(0 to false, ImagenPortada.transformacionExif(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0 to false, ImagenPortada.transformacionExif(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(90 to false, ImagenPortada.transformacionExif(ExifInterface.ORIENTATION_ROTATE_90))     // foto vertical típica de la cámara
        assertEquals(180 to false, ImagenPortada.transformacionExif(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270 to false, ImagenPortada.transformacionExif(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(0 to true, ImagenPortada.transformacionExif(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(90 to true, ImagenPortada.transformacionExif(ExifInterface.ORIENTATION_TRANSPOSE))
    }
}
