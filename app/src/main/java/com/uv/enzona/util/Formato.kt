package com.uv.enzona.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Formato de importes en pesos mexicanos (español de México).
 *
 * Toda la app pasa por aquí para que un mismo precio se lea igual en la
 * tarjeta, el detalle, el pago y el boleto.
 */
object Formato {

    private val localeMx = Locale("es", "MX")

    private fun moneda(decimales: Int): NumberFormat =
        NumberFormat.getCurrencyInstance(localeMx).apply {
            minimumFractionDigits = decimales
            maximumFractionDigits = decimales
        }

    /** "$120" o "$120.50" (solo muestra centavos si el importe los tiene). */
    fun precio(importe: Double): String {
        val tieneCentavos = importe % 1.0 != 0.0
        return moneda(if (tieneCentavos) 2 else 0).format(importe)
    }

    /** "$120.00", siempre con centavos: para desgloses y totales de cobro. */
    fun importe(importe: Double): String = moneda(2).format(importe)

    /** "$120 MXN". */
    fun precioMxn(importe: Double): String = "${precio(importe)} MXN"

    /** "$120.00 MXN". */
    fun importeMxn(importe: Double): String = "${importe(importe)} MXN"

    /** Etiqueta de precio para tarjetas: "Gratis" o "Desde $120". */
    fun etiquetaPrecio(esDePago: Boolean, precioDesde: Double): String =
        if (!esDePago || precioDesde <= 0.0) "Gratis" else "Desde ${precio(precioDesde)}"

    /** "142 lugares" / "1 lugar" / "Sin lugares". */
    fun lugares(disponibles: Int): String = when {
        disponibles <= 0 -> "Sin lugares"
        disponibles == 1 -> "1 lugar"
        else -> "$disponibles lugares"
    }
}
