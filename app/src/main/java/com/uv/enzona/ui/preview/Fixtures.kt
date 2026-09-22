package com.uv.enzona.ui.preview

import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.Boleto
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.EstadoAsiento
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.Orden
import com.uv.enzona.data.model.TipoBoleto
import java.time.LocalDateTime

/**
 * FIXTURES DE PREVIEW. Datos inventados solo para renderizar @Preview en el
 * IDE; no se usan en la app ni describen disponibilidad real.
 */
object Fixtures {

    val concierto = Evento(
        id = 2, organizadorId = 1,
        nombre = "Concierto: Orquesta Universitaria",
        descripcion = "Gala de temporada con obras de Márquez y Revueltas. Una noche de música mexicana para toda la comunidad.",
        lugar = "Teatro Ignacio de la Llave", direccion = "Av. Oriente 6 esq. Sur 5, Centro",
        categoria = "Música", fecha = LocalDateTime.of(2026, 10, 3, 19, 0), fechaFin = LocalDateTime.of(2026, 10, 3, 21, 30),
        latitud = 18.85118, longitud = -97.10102,
        esDePago = true, precioDesde = 120.0, aforo = 250, disponibles = 165, requiereAsiento = true,
        colorSemilla = 1,
    )

    val feriaGratis = Evento(
        id = 1, organizadorId = 1,
        nombre = "Feria de Emprendimiento FCAS con un nombre deliberadamente largo para probar el corte",
        descripcion = "Muestra de proyectos y startups universitarias.",
        lugar = "Facultad de Negocios y Tecnologías", direccion = "Campus Orizaba, UV",
        categoria = "Académico", fecha = LocalDateTime.of(2026, 9, 25, 10, 0),
        latitud = 18.85030, longitud = -97.10360,
        esDePago = false, precioDesde = 0.0, aforo = 300, disponibles = 142,
    )

    val torneoAgotado = concierto.copy(
        id = 5, nombre = "Torneo de Fútbol Rápido Empresarial", categoria = "Deportes",
        lugar = "Unidad Deportiva Sur", direccion = "Col. Rancho Grande", fecha = LocalDateTime.of(2026, 10, 18, 9, 0), fechaFin = null,
        precioDesde = 35.0, aforo = 500, disponibles = 0, requiereAsiento = false,
    )

    val obraCancelada = concierto.copy(
        id = 4, nombre = "Obra: La Casa de Bernarda Alba", categoria = "Teatro",
        lugar = "Auditorio Principal", fecha = LocalDateTime.of(2026, 10, 15, 18, 30), fechaFin = null, precioDesde = 60.0,
        estado = EstadoEvento.CANCELADO, requiereAsiento = true,
    )

    val plantaBaja = TipoBoleto(1, concierto.id, "Planta baja", 120.0, 180, 121)
    val balcon = TipoBoleto(2, concierto.id, "Balcón", 220.0, 70, 44)
    val agotado = TipoBoleto(3, concierto.id, "Palco", 350.0, 10, 0)

    val asientoC7 = Asiento(
        id = 43, eventoId = concierto.id, seccion = "Planta baja", fila = "C", numero = 7,
        estado = EstadoAsiento.OCUPADO,
    )

    /** Mapa pequeño (2 secciones) para previsualizar el plano. */
    val mapaPequeno: List<Asiento> = buildList {
        var id = 1L
        listOf("Planta baja" to 3, "Balcón" to 2).forEach { (seccion, filas) ->
            repeat(filas) { f ->
                repeat(10) { n ->
                    add(
                        Asiento(
                            id = id++, eventoId = concierto.id, seccion = seccion,
                            fila = ('A' + f).toString(), numero = n + 1,
                            estado = if ((f + n) % 4 == 0) EstadoAsiento.OCUPADO else EstadoAsiento.DISPONIBLE,
                        )
                    )
                }
            }
        }
    }

    private val orden = Orden(
        id = 1, asistenteId = 4, eventoId = concierto.id, total = 120.0,
        estado = EstadoOrden.PAGADA, fechaCreacion = "11/09/2026 12:00:00",
    )

    private fun boleto(estado: EstadoBoleto, fechaUso: String? = null) = Boleto(
        id = 1, ordenId = orden.id, tipoBoletoId = plantaBaja.id, asientoId = asientoC7.id,
        codigo = "EZ-4821-1937-6650", qrFirma = "3f9a1c77d0b2e4a8",
        estado = estado, fechaEmision = "11/09/2026 12:00:00", fechaUso = fechaUso,
    )

    val boletoValido = BoletoDetalle(boleto(EstadoBoleto.VALIDO), orden, concierto, plantaBaja, asientoC7)
    val boletoUsado = BoletoDetalle(boleto(EstadoBoleto.USADO, "03/10/2026 18:42:10"), orden, concierto, plantaBaja, asientoC7)
    val boletoCancelado = BoletoDetalle(boleto(EstadoBoleto.CANCELADO), orden, obraCancelada, null, null)
    val boletoGratis = BoletoDetalle(
        boleto(EstadoBoleto.VALIDO).copy(tipoBoletoId = null, asientoId = null),
        orden.copy(total = 0.0, eventoId = feriaGratis.id), feriaGratis, null, null,
    )
}
