package com.uv.enzona

import com.uv.enzona.data.MockRepository
import com.uv.enzona.data.SessionManager
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.Usuario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * CP-ROL-*: adquirir y dejar el rol de organizador (v3, cambio 2). La parte
 * de interfaz (diálogo, pestaña Mis eventos, Snackbar) se cubre en las pruebas
 * de Compose (androidTest); aquí se prueba la regla del repositorio y la sesión.
 */
class RolOrganizadorTest {

    /** JUnit crea una instancia por prueba: el id debe salir del repositorio, no de un contador de instancia. */
    private fun usuarioAsistente(): Usuario {
        val n = (MockRepository.usuarios.maxOf { it.id } + 1)
        val u = Usuario(
            id = n, nombre = "Prueba $n", correo = "rol$n@uv.mx", curp = "PRUE0000${n}HVZRRN0",
            contrasenaHash = "hash-de-prueba", roles = setOf(Rol.ASISTENTE), estado = EstadoUsuario.ACTIVO, correoVerificado = true,
        )
        MockRepository.usuarios += u
        return u
    }

    @Test
    fun `CP-ROL-01 sin confirmar, el usuario sigue siendo solo asistente`() {
        val u = usuarioAsistente()
        // La pantalla no llama a activarRolOrganizador hasta confirmar: el estado no cambia
        assertEquals(setOf(Rol.ASISTENTE), MockRepository.usuarios.first { it.id == u.id }.roles)
        assertFalse(MockRepository.usuarios.first { it.id == u.id }.esPersonal)
    }

    @Test
    fun `CP-ROL-02 al confirmar se agrega el rol y la sesion lo refleja`() {
        val u = usuarioAsistente()
        SessionManager.iniciarSesion(u)
        MockRepository.activarRolOrganizador(u.id)
        SessionManager.refrescar()
        assertTrue(Rol.ORGANIZADOR in SessionManager.usuario!!.roles)
        assertTrue(Rol.ASISTENTE in SessionManager.usuario!!.roles)   // conserva asistente
        SessionManager.cerrarSesion()
    }

    @Test
    fun `CP-ROL-03 dejar de ser organizador sin eventos vigentes quita el rol y vuelve al acceso normal`() {
        val u = usuarioAsistente()
        MockRepository.activarRolOrganizador(u.id)
        SessionManager.iniciarSesion(MockRepository.usuarios.first { it.id == u.id }, comoPersonal = true)
        assertTrue(SessionManager.accesoPersonal)

        assertNull(MockRepository.motivoNoPuedeDejarOrganizador(u.id))
        val r = MockRepository.dejarRolOrganizador(u.id)
        assertTrue(r.isSuccess)
        SessionManager.refrescar()
        assertEquals(setOf(Rol.ASISTENTE), SessionManager.usuario!!.roles)
        assertFalse(SessionManager.accesoPersonal)   // ya no es personal: vuelve a Inicio
        SessionManager.cerrarSesion()
    }

    @Test
    fun `CP-ROL-04 con un evento publicado con boletos vigentes la opcion queda bloqueada`() {
        val u = usuarioAsistente()
        MockRepository.activarRolOrganizador(u.id)
        val e = MockRepository.crearEvento(
            organizadorId = u.id, nombre = "Evento con ventas", descripcion = "", lugar = "Sede", direccion = "",
            categoria = "Música", fecha = LocalDateTime.of(2026, 12, 1, 19, 0), esDePago = true, precio = 50.0, aforo = 10,
            publicar = true, latitud = 18.851, longitud = -97.1,
        )
        MockRepository.comprar(4L, SolicitudCompra(e.id, MockRepository.tiposDe(e.id).first().id, 1)).getOrThrow()

        val motivo = MockRepository.motivoNoPuedeDejarOrganizador(u.id)
        assertNotNull(motivo)
        assertTrue(motivo!!.contains("boletos vigentes"))
        assertTrue(MockRepository.dejarRolOrganizador(u.id).isFailure)
        assertTrue(Rol.ORGANIZADOR in MockRepository.usuarios.first { it.id == u.id }.roles)

        // Tras cancelar el evento (anula los boletos) ya puede dejar el rol
        MockRepository.cancelarEvento(e.id, ejecutadoPor = u.id, aceptaComision = true, ahora = LocalDateTime.of(2026, 9, 17, 12, 0)).getOrThrow()
        assertNull(MockRepository.motivoNoPuedeDejarOrganizador(u.id))
    }

    @Test
    fun `no se puede dejar el rol si es el unico o si la cuenta es administrador`() {
        val soloOrganizador = usuarioAsistente().let { MockRepository.usuarios[MockRepository.usuarios.indexOfFirst { x -> x.id == it.id }] = it.copy(roles = setOf(Rol.ORGANIZADOR)); it }
        assertTrue(MockRepository.motivoNoPuedeDejarOrganizador(soloOrganizador.id)!!.contains("único rol"))

        val admin = usuarioAsistente().let { MockRepository.usuarios[MockRepository.usuarios.indexOfFirst { x -> x.id == it.id }] = it.copy(roles = setOf(Rol.ADMIN, Rol.ORGANIZADOR, Rol.ASISTENTE)); it }
        assertTrue(MockRepository.motivoNoPuedeDejarOrganizador(admin.id)!!.contains("administración"))
        assertTrue(MockRepository.dejarRolOrganizador(admin.id).isFailure)
    }
}
