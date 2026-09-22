package com.uv.enzona

import com.uv.enzona.data.FiltroFecha
import com.uv.enzona.data.FiltroPrecio
import com.uv.enzona.data.FiltrosEventos
import com.uv.enzona.data.model.Evento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * CP-07: el filtrado por lugar y fecha devuelve solo los eventos coincidentes.
 * Se prueba la lógica pura de `FiltrosEventos`, que es la que usa Inicio.
 */
class FiltrosEventosTest {

    private val hoy: LocalDate = LocalDate.of(2026, 9, 11)

    private fun evento(id: Long, nombre: String, lugar: String, categoria: String, fecha: LocalDateTime, pago: Boolean) = Evento(
        id = id, organizadorId = 1, nombre = nombre, descripcion = "", lugar = lugar, direccion = "",
        categoria = categoria, fecha = fecha, esDePago = pago, precioDesde = if (pago) 100.0 else 0.0,
        aforo = 100, disponibles = 50,
    )

    private val eventos = listOf(
        evento(1, "Feria", "Facultad de Negocios", "Académico", LocalDateTime.of(2026, 9, 25, 10, 0), pago = false),
        evento(2, "Concierto", "Teatro de la Ciudad", "Música", LocalDateTime.of(2026, 10, 3, 19, 0), pago = true),
        evento(3, "Hackathon", "Centro de Cómputo", "Tecnología", LocalDateTime.of(2026, 10, 9, 17, 0), pago = false),
        evento(4, "Obra", "Teatro de la Ciudad", "Teatro", LocalDateTime.of(2026, 10, 15, 18, 30), pago = true),
        evento(5, "Charla", "Teatro de la Ciudad", "Académico", LocalDateTime.of(2026, 9, 12, 11, 0), pago = false),
    )

    @Test
    fun `sin filtros devuelve todo`() {
        assertEquals(5, FiltrosEventos().aplicar(eventos, hoy).size)
        assertEquals(0, FiltrosEventos().activos)
    }

    @Test
    fun `filtra por lugar y fecha a la vez (CP-07)`() {
        val filtros = FiltrosEventos(lugar = "Teatro de la Ciudad", fecha = FiltroFecha.PROXIMO_MES)
        val resultado = filtros.aplicar(eventos, hoy).map { it.id }
        assertEquals(listOf(2L, 4L), resultado)
        assertEquals(2, filtros.activos)
    }

    @Test
    fun `esta semana usa lunes a domingo de la fecha actual`() {
        // 11/09/2026 es viernes; la semana va del 7 al 13 de septiembre
        val resultado = FiltrosEventos(fecha = FiltroFecha.ESTA_SEMANA).aplicar(eventos, hoy).map { it.id }
        assertEquals(listOf(5L), resultado)
    }

    @Test
    fun `este mes incluye el evento del 25 de septiembre`() {
        val resultado = FiltrosEventos(fecha = FiltroFecha.ESTE_MES).aplicar(eventos, hoy).map { it.id }
        assertEquals(listOf(1L, 5L), resultado)
    }

    @Test
    fun `gratis y de pago son excluyentes`() {
        val gratis = FiltrosEventos(precio = FiltroPrecio.GRATIS).aplicar(eventos, hoy)
        val pago = FiltrosEventos(precio = FiltroPrecio.DE_PAGO).aplicar(eventos, hoy)
        assertTrue(gratis.none { it.esDePago })
        assertTrue(pago.all { it.esDePago })
        assertEquals(5, gratis.size + pago.size)
    }

    @Test
    fun `la busqueda por texto cubre nombre y lugar sin distinguir mayusculas`() {
        assertEquals(listOf(3L), FiltrosEventos(busqueda = "hack").aplicar(eventos, hoy).map { it.id })
        assertEquals(3, FiltrosEventos(busqueda = "teatro de la").aplicar(eventos, hoy).size)
    }

    @Test
    fun `limpiar deja el estado inicial`() {
        val sucio = FiltrosEventos(busqueda = "x", categoria = "Música", lugar = "Y", precio = FiltroPrecio.GRATIS, fecha = FiltroFecha.HOY)
        assertTrue(sucio.hayAlgo)
        assertEquals(FiltrosEventos(), sucio.limpiar())
    }
}
