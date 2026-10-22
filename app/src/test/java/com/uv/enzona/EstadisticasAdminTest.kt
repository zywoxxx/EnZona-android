package com.uv.enzona

import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.RepositorioMock
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.ReporteEvento
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.Usuario
import com.uv.enzona.data.model.calcularEstadisticas
import com.uv.enzona.data.model.etiquetaMes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

/** Estadísticas del panel de administración (RF-18): agregación pura y su cálculo con los datos de prueba. */
class EstadisticasAdminTest {

    private fun usuario(id: Long, estado: EstadoUsuario, vararg roles: Rol) = Usuario(
        id = id, nombre = "U$id", correo = "u$id@uv.mx", curp = "CURP", contrasenaHash = "",
        roles = roles.toSet().ifEmpty { setOf(Rol.ASISTENTE) }, estado = estado,
    )

    private fun evento(id: Long, estado: EstadoEvento, categoria: String, fecha: LocalDateTime, aforo: Int, disponibles: Int, dePago: Boolean = true) = Evento(
        id = id, organizadorId = 1, nombre = "Evento $id", descripcion = "", lugar = "Foro", direccion = "", categoria = categoria,
        fecha = fecha, esDePago = dePago, precioDesde = if (dePago) 100.0 else 0.0, aforo = aforo, disponibles = disponibles, estado = estado,
    )

    @Test
    fun cpAdmin01_agregaCuentasEventosYReportes() {
        val usuarios = listOf(
            usuario(1, EstadoUsuario.ACTIVO, Rol.ADMIN),
            usuario(2, EstadoUsuario.ACTIVO, Rol.ORGANIZADOR),
            usuario(3, EstadoUsuario.ACTIVO, Rol.ORGANIZADOR, Rol.VALIDADOR),
            usuario(4, EstadoUsuario.PENDIENTE),
            usuario(5, EstadoUsuario.BLOQUEADO),
        )
        val octubre = LocalDateTime.of(2026, 10, 27, 20, 0)
        val noviembre = LocalDateTime.of(2026, 11, 5, 10, 0)
        val eventos = listOf(
            evento(1, EstadoEvento.PUBLICADO, "Música", octubre, aforo = 50, disponibles = 48),
            evento(2, EstadoEvento.PUBLICADO, "Cultural", octubre, aforo = 120, disponibles = 100, dePago = false),
            evento(3, EstadoEvento.BORRADOR, "Música", noviembre, aforo = 20, disponibles = 20),
            evento(4, EstadoEvento.CANCELADO, "Taller", noviembre, aforo = 10, disponibles = 10),
            evento(5, EstadoEvento.REALIZADO, "", LocalDateTime.of(2026, 9, 1, 18, 0), aforo = 30, disponibles = 0),
        )
        val reportes = listOf(
            ReporteEvento(1, boletosEmitidos = 2, accesosValidados = 2, boletosCancelados = 0, ingresos = 360.0, reembolsado = 0.0),
            ReporteEvento(2, boletosEmitidos = 20, accesosValidados = 5, boletosCancelados = 0, ingresos = 0.0, reembolsado = 0.0),
            ReporteEvento(4, boletosEmitidos = 0, accesosValidados = 0, boletosCancelados = 3, ingresos = 300.0, reembolsado = 300.0),
            ReporteEvento(5, boletosEmitidos = 30, accesosValidados = 28, boletosCancelados = 0, ingresos = 3000.0, reembolsado = 0.0),
        )

        val e = calcularEstadisticas(usuarios, eventos, reportes)

        assertEquals(5, e.usuariosTotal)
        assertEquals(3, e.usuariosActivos)
        assertEquals(1, e.usuariosPendientes)
        assertEquals(1, e.usuariosBloqueados)
        assertEquals(2, e.organizadores)
        assertEquals(1, e.validadores)
        assertEquals(1, e.administradores)

        assertEquals(5, e.eventosTotal)
        assertEquals(2, e.eventosPublicados)
        assertEquals(1, e.eventosBorrador)
        assertEquals(1, e.eventosRealizados)
        assertEquals(1, e.eventosCancelados)
        assertEquals(1, e.eventosGratuitos)
        assertEquals(4, e.eventosDePago)
        assertEquals("de mayor a menor y por nombre", listOf("Música", "Cultural", "Sin categoría", "Taller"), e.eventosPorCategoria.map { it.etiqueta })
        assertEquals(listOf(2.0, 1.0, 1.0, 1.0), e.eventosPorCategoria.map { it.valor })
        assertEquals("en orden cronológico", listOf("sep 26", "oct 26", "nov 26"), e.eventosPorMes.map { it.etiqueta })
        assertEquals(listOf(1.0, 2.0, 2.0), e.eventosPorMes.map { it.valor })

        assertEquals(52, e.boletosEmitidos)
        assertEquals(35, e.accesosValidados)
        assertEquals(3, e.boletosCancelados)
        assertEquals("aforo de publicados y realizados", 200, e.aforoTotal)
        assertEquals(2 + 20 + 30, e.lugaresOcupados)
        assertEquals(26.0, e.ocupacionGlobal, 0.0001)
        assertEquals(3660.0, e.ingresosTotales, 0.0001)
        assertEquals(300.0, e.reembolsado, 0.0001)
        assertEquals(3360.0, e.ingresosNetos, 0.0001)
        assertEquals("solo eventos con ingresos, de mayor a menor", listOf("Evento 5", "Evento 1", "Evento 4"), e.ingresosPorEvento.map { it.etiqueta })
        assertEquals("solo publicados", listOf("Evento 2", "Evento 1"), e.ocupacionPorEvento.map { it.etiqueta })
        assertEquals(100.0 * 20 / 120, e.ocupacionPorEvento[0].valor, 0.0001)
        assertEquals(35 * 100.0 / 52, e.asistencia, 0.0001)
    }

    @Test
    fun cpAdmin02_sinDatosNoDivideEntreCero() {
        val e = calcularEstadisticas(emptyList(), emptyList(), emptyList())
        assertEquals(0, e.usuariosTotal)
        assertEquals(0.0, e.ocupacionGlobal, 0.0)
        assertEquals(0.0, e.asistencia, 0.0)
        assertTrue(e.eventosPorMes.isEmpty() && e.ingresosPorEvento.isEmpty())
        assertEquals("ene 27", etiquetaMes(YearMonth.of(2027, 1)))
    }

    @Test
    fun cpAdmin03_conLosDatosDePrueba_losIngresosCoincidenConLosBoletosVigentes() = runBlocking {
        val e = RepositorioMock.estadisticasAdmin()
        assertEquals(MockRepository.usuarios.size, e.usuariosTotal)
        assertEquals(MockRepository.eventos.size, e.eventosTotal)
        val esperado = MockRepository.eventos.sumOf { ev ->
            MockRepository.boletosDeEvento(ev.id)
                .filter { it.estado == EstadoBoleto.VALIDO || it.estado == EstadoBoleto.USADO }
                .sumOf { it.tipoBoleto?.precio ?: 0.0 }
        }
        assertEquals(esperado, e.ingresosTotales, 0.0001)
        assertTrue("hay ventas en los datos de prueba", e.ingresosTotales > 0)
        assertTrue(e.boletosEmitidos >= e.accesosValidados)
    }
}
