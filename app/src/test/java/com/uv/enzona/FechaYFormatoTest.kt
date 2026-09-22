package com.uv.enzona

import com.uv.enzona.util.FechaEvento
import com.uv.enzona.util.FechaHoraEvento
import com.uv.enzona.util.Formato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class FechaYFormatoTest {

    private val hoy: LocalDate = LocalDate.of(2026, 9, 17)

    // ---------------------------------------------------------------- formato único (v3)

    @Test
    fun `la fecha del evento se muestra como Vie 25 Sep 19 00 con locale es-MX`() {
        val f = LocalDateTime.of(2026, 9, 25, 19, 0)
        assertEquals("Vie 25 Sep · 19:00", FechaEvento.corto(f))
        assertEquals("Vie 25 Sep 2026 · 19:00", FechaEvento.largo(f))
        assertEquals("19:00", FechaEvento.hora(f))
        assertEquals("25", FechaEvento.dia(f))
        assertEquals("sep", FechaEvento.mesCorto(f))
        assertEquals("Sáb 3 Oct · 19:00", FechaEvento.corto(LocalDateTime.of(2026, 10, 3, 19, 0)))
    }

    @Test
    fun `CP-HORA-02 la fecha se serializa en ISO-8601 con zona y se lee de vuelta`() {
        val f = LocalDateTime.of(2026, 9, 25, 19, 0)
        assertEquals("2026-09-25T19:00:00-06:00", FechaEvento.iso(f))
        assertEquals(f, FechaEvento.desdeIso("2026-09-25T19:00:00-06:00"))
        // Una hora en UTC se convierte a la zona del evento
        assertEquals(LocalDateTime.of(2026, 9, 25, 13, 0), FechaEvento.desdeIso("2026-09-25T19:00:00Z"))
        assertNull(FechaEvento.desdeIso("no es una fecha"))
    }

    @Test
    fun `el intervalo muestra la hora de fin del mismo dia o la fecha completa`() {
        val ini = LocalDateTime.of(2026, 10, 3, 19, 0)
        assertEquals("Sáb 3 Oct · 19:00 – 21:30", FechaEvento.intervalo(ini, LocalDateTime.of(2026, 10, 3, 21, 30)))
        assertEquals("Sáb 3 Oct · 19:00 – Dom 4 Oct · 02:00", FechaEvento.intervalo(ini, LocalDateTime.of(2026, 10, 4, 2, 0)))
        assertEquals("Sáb 3 Oct · 19:00", FechaEvento.intervalo(ini, null))
    }

    // ---------------------------------------------------------------- validación de hora (CP-HORA-01)

    @Test
    fun `CP-HORA-01 la hora solo admite digitos en formato 24 h`() {
        assertNull(FechaHoraEvento.parsearHora("siete pm"))
        assertNull(FechaHoraEvento.parsearHora("7pm"))
        assertNull(FechaHoraEvento.parsearHora("24:00"))
        assertNull(FechaHoraEvento.parsearHora("19:60"))
        assertNull(FechaHoraEvento.parsearHora("19"))
        assertEquals(LocalTime.of(19, 0), FechaHoraEvento.parsearHora("19:00"))
        assertEquals(LocalTime.of(0, 0), FechaHoraEvento.parsearHora("0000"))
        assertEquals(LocalTime.of(23, 59), FechaHoraEvento.parsearHora("2359"))
        assertEquals("19:30", FechaHoraEvento.mascara("1930abc"))
        assertEquals("19", FechaHoraEvento.mascara("19"))
        assertEquals("1930", FechaHoraEvento.soloDigitos("19:30:45"))   // máximo 4 dígitos
    }

    // ---------------------------------------------------------------- validación de fecha (CP-FECHA-*)

    @Test
    fun `CP-FECHA-02 una fecha pasada no es seleccionable y se rechaza al validar`() {
        assertTrue(!FechaHoraEvento.fechaSeleccionable(hoy.minusDays(1), hoy))
        assertTrue(FechaHoraEvento.fechaSeleccionable(hoy, hoy))
        val error = FechaHoraEvento.validar(LocalDateTime.of(2026, 9, 16, 19, 0), null, hoy)
        assertNotNull(error)
        assertTrue(error!!.contains("anterior a hoy"))
        assertNull(FechaHoraEvento.validar(LocalDateTime.of(2026, 9, 25, 19, 0), null, hoy))
    }

    @Test
    fun `CP-FECHA-03 un fin anterior al inicio es un error de validacion`() {
        val inicio = LocalDateTime.of(2026, 9, 25, 19, 0)
        assertNotNull(FechaHoraEvento.validar(inicio, LocalDateTime.of(2026, 9, 24, 19, 0), hoy))
        assertNotNull(FechaHoraEvento.validar(inicio, LocalDateTime.of(2026, 9, 25, 18, 0), hoy))   // mismo día, hora anterior
        assertNotNull(FechaHoraEvento.validar(inicio, inicio, hoy))                                   // misma hora tampoco
        assertNull(FechaHoraEvento.validar(inicio, LocalDateTime.of(2026, 9, 25, 21, 0), hoy))
        assertNull(FechaHoraEvento.validar(inicio, LocalDateTime.of(2026, 9, 26, 1, 0), hoy))
        assertNotNull(FechaHoraEvento.validar(null, null, hoy))
    }

    // ---------------------------------------------------------------- formato de dinero (sin cambios)

    @Test
    fun `los precios se muestran en pesos mexicanos`() {
        assertEquals("Gratis", Formato.etiquetaPrecio(false, 0.0))
        assertEquals("Desde \$120", Formato.etiquetaPrecio(true, 120.0))
        assertEquals("\$120.00", Formato.importe(120.0))
        assertEquals("\$120.00 MXN", Formato.importeMxn(120.0))
        assertEquals("\$35.50", Formato.precio(35.5))
        assertEquals("\$1,250", Formato.precio(1250.0))
    }

    @Test
    fun `los lugares disponibles se leen en singular y plural`() {
        assertEquals("Sin lugares", Formato.lugares(0))
        assertEquals("1 lugar", Formato.lugares(1))
        assertEquals("142 lugares", Formato.lugares(142))
    }
}
