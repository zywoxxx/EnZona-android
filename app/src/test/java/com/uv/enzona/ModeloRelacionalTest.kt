package com.uv.enzona

import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.SolicitudCompra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El repositorio en memoria respeta el modelo relacional del diagrama ER (v4):
 * catálogos `rol` y `categoria`, tabla puente `usuario_rol`, claves foráneas de
 * evento, orden, boleto y validacion_acceso, y `contrasena_hash` en usuario.
 * Casos CP-MODELO-01..05.
 */
class ModeloRelacionalTest {

    @Test
    fun cpModelo01_catalogos_rol_y_categoria_conIdsYNombresUnicos() {
        assertEquals(listOf(1, 2, 3, 4), Rol.entries.map { it.id })
        assertEquals(Rol.entries.size, Rol.entries.map { it.nombreBd }.distinct().size)

        val categorias = MockRepository.categorias
        assertEquals(categorias.size, categorias.map { it.id }.distinct().size)
        assertEquals(categorias.size, categorias.map { it.nombre.lowercase() }.distinct().size)
        assertEquals(2, MockRepository.categoriaId("música"))
        assertEquals(null, MockRepository.categoriaId("Inexistente"))
    }

    @Test
    fun cpModelo02_usuarioRol_tieneUnaFilaPorUsuarioYRol() {
        val filas = MockRepository.usuarioRoles()
        val esperadas = MockRepository.usuarios.sumOf { it.roles.size }
        assertEquals(esperadas, filas.size)
        assertEquals(filas.size, filas.distinct().size)                       // PK (usuario_id, rol_id)
        val organizador = MockRepository.usuarios.first { it.correo == "organizador@uv.mx" }
        assertTrue(filas.contains(com.uv.enzona.data.model.UsuarioRol(organizador.id, Rol.ORGANIZADOR.id)))
        assertTrue(filas.contains(com.uv.enzona.data.model.UsuarioRol(organizador.id, Rol.ASISTENTE.id)))
    }

    @Test
    fun cpModelo03_evento_resuelveCategoriaId_yGuardaFechaCreacion() {
        // Eventos de semilla (ids 1..5); otras pruebas crean eventos con categoría vacía o en el pasado
        MockRepository.eventos.filter { it.id in 1..5 }.forEach { e ->
            assertNotNull("El evento «${e.nombre}» debe tener categoria_id", e.categoriaId)
            assertEquals(MockRepository.categorias.first { it.id == e.categoriaId }.nombre, e.categoria)
            assertTrue(e.fechaCreacion.isBefore(e.fecha))
        }
        MockRepository.eventos.filter { MockRepository.categoriaId(it.categoria) != null }.forEach { e ->
            assertEquals(MockRepository.categoriaId(e.categoria), e.categoriaId)
        }
        val nuevo = MockRepository.crearEvento(
            organizadorId = 1, nombre = "Con categoría", descripcion = "", lugar = "x", direccion = "",
            categoria = "Deportes", fecha = java.time.LocalDateTime.of(2026, 12, 1, 10, 0),
            esDePago = false, precio = 0.0, aforo = 5, publicar = false,
        )
        assertEquals(5, nuevo.categoriaId)
    }

    @Test
    fun cpModelo04_orden_boleto_y_validacion_respetanLasClavesForaneas() {
        val evento = MockRepository.crearEvento(
            organizadorId = 1, nombre = "FK", descripcion = "", lugar = "x", direccion = "",
            categoria = "Teatro", fecha = java.time.LocalDateTime.of(2026, 11, 20, 20, 0),
            esDePago = true, precio = 80.0, aforo = 10, publicar = true, latitud = 18.85, longitud = -97.1,
        )
        val tipo = MockRepository.tiposDe(evento.id).single()
        val compra = MockRepository.comprar(4, SolicitudCompra(evento.id, tipo.id, 2)).getOrThrow()

        // orden → usuario y evento; pago → orden; boleto → orden y tipo_boleto
        assertEquals(4L, compra.orden.asistenteId)
        assertEquals(evento.id, compra.orden.eventoId)
        assertEquals(compra.orden.id, compra.pago!!.ordenId)
        compra.boletos.forEach { b ->
            assertEquals(compra.orden.id, b.boleto.ordenId)
            assertEquals(tipo.id, b.boleto.tipoBoletoId)
            assertTrue(MockRepository.ordenes.any { it.id == b.boleto.ordenId })
        }

        // validacion_acceso → boleto (FK) y → usuario validador
        val antes = MockRepository.validaciones.size
        val resultado = MockRepository.validarQr(compra.boletos[0].contenidoQr, validadorId = 2)
        assertTrue(resultado.permitido)
        val registro = MockRepository.validaciones.last()
        assertEquals(antes + 1, MockRepository.validaciones.size)
        assertEquals(compra.boletos[0].id, registro.boletoId)
        assertEquals(2L, registro.validadorId)

        // Un código inexistente queda en bitácora sin boleto_id
        MockRepository.validarQr("EZ-0000-0000-0000.firmainvalida", validadorId = 2)
        assertEquals(null, MockRepository.validaciones.last().boletoId)
    }

    @Test
    fun cpModelo05_usuario_guardaContrasenaHash_noLaContrasena() {
        val (usuario, _) = MockRepository.registrar("Prueba Hash", "hash@uv.mx", "2721234567", "HASH000101HVZXXX01", "Secreta123")
        assertNotEquals("Secreta123", usuario.contrasenaHash)
        assertEquals(64, usuario.contrasenaHash.length)                      // SHA-256 en la demo
        assertFalse(usuario.telefonoVerificado)
        assertNotNull(usuario.fechaRegistro)

        // Autenticación compara hashes: la correcta pasa, la incorrecta no
        assertTrue(MockRepository.autenticar("organizador@uv.mx", "enzona123").isSuccess)
        assertTrue(MockRepository.autenticar("organizador@uv.mx", "otra").isFailure)
    }
}
