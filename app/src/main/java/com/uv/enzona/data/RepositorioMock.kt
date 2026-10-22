package com.uv.enzona.data

import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.CargoOrganizador
import com.uv.enzona.data.model.Categoria
import com.uv.enzona.data.model.CompraRealizada
import com.uv.enzona.data.model.ErrorCompra
import com.uv.enzona.data.model.EstadisticasAdmin
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.ReporteEvento
import com.uv.enzona.data.model.calcularEstadisticas
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.LayoutSeccion
import com.uv.enzona.data.model.MetodoPago
import com.uv.enzona.data.model.NotificacionAsistente
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.data.model.ResultadoCancelacion
import com.uv.enzona.data.model.ResultadoValidacion
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.data.model.Usuario
import com.uv.enzona.data.model.ValidacionAcceso
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Implementación de [RepositorioEnZona] con los datos de prueba en memoria.
 *
 * Es un adaptador sobre [MockRepository] y no el propio objeto porque Kotlin no
 * admite una función `suspend` y otra normal con la misma firma: así
 * `MockRepository` conserva sus funciones síncronas, que son las que usan las
 * pruebas unitarias y los previews, y las pantallas hablan con esta interfaz.
 */
object RepositorioMock : RepositorioEnZona {

    override val esDemostracion: Boolean = true

    private val yo: Long get() = SessionManager.usuarioId

    /** Convierte los errores del repositorio en memoria al mismo tipo que usa la API. */
    private inline fun <T> local(bloque: () -> T): T = try {
        bloque()
    } catch (e: ErrorCompra) {
        throw e
    } catch (e: ErrorEnZona) {
        throw e
    } catch (e: RuntimeException) {
        throw ErrorEnZona(422, "REGLA", e.message ?: "No se pudo completar la operación.")
    }

    private fun exigirEvento(id: Long): Evento =
        MockRepository.obtenerEvento(id) ?: throw ErrorEnZona(404, "NO_ENCONTRADO", "El evento no existe.")

    /** Añade lo que la API entrega ya resuelto (nombres y si hay boletos). */
    private fun Evento.completo(distanciaKm: Double? = null): Evento = copy(
        organizadorNombre = MockRepository.usuarios.find { it.id == organizadorId }?.nombre,
        canceladoPorNombre = canceladoPor?.let { id -> MockRepository.usuarios.find { it.id == id }?.nombre },
        tieneBoletos = !MockRepository.puedeCambiarAsientos(id),
        distanciaKm = distanciaKm,
    )

    // ------------------------------------------------------------ identidad

    override suspend fun iniciarSesion(identificador: String, contrasena: String, accesoPersonal: Boolean): Usuario =
        local { MockRepository.autenticar(identificador, contrasena).getOrThrow() }

    override suspend fun restaurarSesion(): Usuario? = null

    override suspend fun cerrarSesion() = Unit

    override suspend fun registrar(
        nombre: String, correo: String, telefono: String, curp: String, contrasena: String, aceptaAvisoPrivacidad: Boolean,
    ): RegistroPendiente = local {
        if (!aceptaAvisoPrivacidad) throw ErrorEnZona(400, "VALIDACION", "Debes aceptar el aviso de privacidad.")
        if (MockRepository.correoRegistrado(correo)) throw ErrorEnZona(409, "CORREO_REGISTRADO", "Ya existe una cuenta con ese correo.")
        if (MockRepository.curpRegistrado(curp)) throw ErrorEnZona(409, "CURP_REGISTRADO", "Ya existe una cuenta registrada con esa CURP.")
        val (usuario, otp) = MockRepository.registrar(nombre, correo, telefono, curp, contrasena)
        RegistroPendiente(usuario.id, otp, correo = correo.trim().lowercase())
    }

    /** Los datos de prueba verifican con un solo código (el del correo). */
    override suspend fun verificarOtp(usuarioId: Long, codigo: String): EstadoVerificacion {
        if (!MockRepository.verificarOtp(usuarioId, codigo)) {
            throw ErrorEnZona(422, "OTP_INCORRECTO", "El código no es correcto. Revísalo e inténtalo de nuevo.")
        }
        return EstadoVerificacion(completa = true)
    }

    override suspend fun reenviarOtp(usuarioId: Long): String? = MockRepository.reenviarOtp(usuarioId)

    override suspend fun usuarioActual(): Usuario =
        MockRepository.usuarios.find { it.id == yo } ?: throw ErrorEnZona(401, "NO_AUTENTICADO", "Inicia sesión para continuar.")

    override suspend fun actualizarPerfil(nombre: String, telefono: String): Usuario =
        MockRepository.actualizarPerfil(yo, nombre, telefono) ?: throw ErrorEnZona(404, "NO_ENCONTRADO", "La cuenta no existe.")

    override suspend fun cambiarContrasena(actual: String, nueva: String) = local {
        MockRepository.cambiarContrasena(yo, actual, nueva).getOrThrow()
    }

    override suspend fun convertirseEnOrganizador(): Usuario =
        MockRepository.activarRolOrganizador(yo) ?: throw ErrorEnZona(404, "NO_ENCONTRADO", "La cuenta no existe.")

    override suspend fun dejarDeSerOrganizador(): Usuario = try {
        MockRepository.dejarRolOrganizador(yo).getOrThrow()
    } catch (e: IllegalStateException) {
        throw ErrorEnZona(409, "NO_PUEDE_DEJAR_ROL", e.message ?: "No puedes dejar el rol de organizador.")
    }

    override suspend fun motivoNoPuedeDejarOrganizador(): String? = MockRepository.motivoNoPuedeDejarOrganizador(yo)

    // ------------------------------------------------------------ eventos

    override suspend fun categorias(): List<Categoria> = MockRepository.categorias

    override suspend fun eventosPublicados(ciudad: String?, ubicacion: Ubicacion?): List<Evento> {
        val publicados = MockRepository.eventosPublicados()
        if (ubicacion != null) {
            // Mismo criterio que el servidor: por distancia, y sin coordenadas al final sin distancia inventada
            return publicados
                .map { e -> e.completo(if (e.tieneCoordenadas) kilometros(ubicacion, e.latitud!!, e.longitud!!) else null) }
                .sortedWith(compareBy<Evento, Double?>(nullsLast()) { it.distanciaKm }.thenBy { it.fecha })
        }
        return publicados
            .filter { ciudad == null || it.ciudad.equals(ciudad, ignoreCase = true) }
            .map { it.completo() }
    }

    /** Fórmula de Haversine, igual que la consulta del backend. */
    internal fun kilometros(desde: Ubicacion, latitud: Double, longitud: Double): Double {
        val dLat = Math.toRadians(latitud - desde.latitud)
        val dLon = Math.toRadians(longitud - desde.longitud)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(desde.latitud)) * cos(Math.toRadians(latitud)) * sin(dLon / 2).pow(2)
        return Math.round(6371.0088 * 2 * asin(sqrt(a.coerceAtMost(1.0))) * 100) / 100.0
    }

    override suspend fun evento(id: Long): Evento = exigirEvento(id).completo()

    override suspend fun tiposDe(eventoId: Long): List<TipoBoleto> = MockRepository.tiposDe(eventoId)

    override suspend fun asientosDe(eventoId: Long): List<Asiento> = MockRepository.asientosDe(eventoId)

    override suspend fun misEventos(): List<Evento> = MockRepository.eventosDe(yo).map { it.completo() }

    override suspend fun crearEvento(datos: DatosEvento, publicar: Boolean): Evento = local {
        val seccion = datos.secciones.firstOrNull()
        MockRepository.crearEvento(
            organizadorId = yo, nombre = datos.nombre, descripcion = datos.descripcion, lugar = datos.lugar,
            direccion = datos.direccion, categoria = datos.categoria, fecha = datos.fecha, esDePago = datos.esDePago,
            precio = datos.precio, aforo = datos.aforo, publicar = publicar,
            requiereAsiento = seccion != null, filas = seccion?.filas ?: 0,
            asientosPorFila = seccion?.asientosPorFila ?: 0, seccion = seccion?.nombre ?: "General",
            fechaFin = datos.fechaFin, latitud = datos.latitud, longitud = datos.longitud, ciudad = datos.ciudad,
        ).completo()
    }

    override suspend fun modificarEvento(eventoId: Long, datos: DatosEvento): Evento = local {
        MockRepository.modificarEvento(
            eventoId, datos.nombre, datos.descripcion, datos.lugar, datos.direccion, datos.categoria, datos.fecha,
            datos.aforo, fechaFin = datos.fechaFin, latitud = datos.latitud, longitud = datos.longitud,
        ).getOrThrow().completo()
    }

    override suspend fun publicarEvento(eventoId: Long): Evento = local {
        MockRepository.publicarEvento(eventoId).getOrThrow().completo()
    }

    override suspend fun marcarRealizado(eventoId: Long): Evento {
        MockRepository.marcarRealizado(eventoId)
        return exigirEvento(eventoId).completo()
    }

    override suspend fun cambiarAsientos(eventoId: Long, secciones: List<LayoutSeccion>): Evento {
        if (!MockRepository.puedeCambiarAsientos(eventoId)) {
            throw ErrorEnZona(409, "PLANO_CON_BOLETOS", "No se puede cambiar el plano: este evento ya tiene boletos emitidos.")
        }
        if (secciones.isEmpty()) MockRepository.desactivarAsientos(eventoId) else MockRepository.activarAsientos(eventoId, secciones)
        return exigirEvento(eventoId).completo()
    }

    /** Con datos de prueba no hay almacén: la portada es la copia local de la imagen elegida. */
    override suspend fun subirImagenEvento(eventoId: Long, imagen: ImagenParaSubir): Evento = cambiarPortada(eventoId, imagen.uriLocal)

    override suspend fun quitarImagenEvento(eventoId: Long): Evento = cambiarPortada(eventoId, null)

    private fun cambiarPortada(eventoId: Long, url: String?): Evento {
        val indice = MockRepository.eventos.indexOfFirst { it.id == eventoId }
        if (indice < 0) throw ErrorEnZona(404, "NO_ENCONTRADO", "El evento no existe.")
        MockRepository.eventos[indice] = MockRepository.eventos[indice].copy(imagenUrl = url)
        return MockRepository.eventos[indice].completo()
    }

    override suspend fun politicaCancelacion(eventoId: Long, administrativa: Boolean): PoliticaCancelacion =
        MockRepository.politicaCancelacionDe(eventoId, administrativa)
            ?: throw ErrorEnZona(404, "NO_ENCONTRADO", "El evento no existe.")

    override suspend fun cancelarEvento(eventoId: Long, motivo: String?, aceptaComision: Boolean, administrativa: Boolean): ResultadoCancelacion = try {
        MockRepository.cancelarEvento(eventoId, ejecutadoPor = yo, motivo = motivo, aceptaComision = aceptaComision, administrativa = administrativa).getOrThrow()
    } catch (e: IllegalStateException) {
        throw ErrorEnZona(409, "CANCELACION_BLOQUEADA", e.message ?: "No se puede cancelar el evento.")
    } catch (e: IllegalArgumentException) {
        throw ErrorEnZona(422, "MOTIVO_OBLIGATORIO", e.message ?: "Falta el motivo de la cancelación.")
    }

    override suspend fun boletosDeEvento(eventoId: Long): List<BoletoDetalle> = MockRepository.boletosDeEvento(eventoId)

    override suspend fun cargosDeOrganizador(): List<CargoOrganizador> =
        MockRepository.cargosOrganizador.filter { it.organizadorId == yo }

    private fun personal(): List<PersonalAcceso> =
        MockRepository.personalDeAcceso().map { PersonalAcceso(it.id, it.nombre, it.correo) }

    /** En los datos de prueba el personal de acceso es global, no por evento. */
    override suspend fun personalDeAcceso(eventoId: Long): List<PersonalAcceso> = personal()

    override suspend fun designarValidador(eventoId: Long, correo: String): List<PersonalAcceso> = try {
        MockRepository.designarValidador(correo).getOrThrow()
        personal()
    } catch (e: IllegalStateException) {
        throw ErrorEnZona(409, "YA_DESIGNADO", e.message ?: "Esa cuenta ya es personal de acceso.")
    } catch (e: IllegalArgumentException) {
        throw ErrorEnZona(404, "NO_ENCONTRADO", e.message ?: "No hay ninguna cuenta registrada con ese correo.")
    }

    override suspend fun quitarValidador(eventoId: Long, usuarioId: Long): List<PersonalAcceso> {
        MockRepository.quitarValidador(usuarioId)
        return personal()
    }

    // ------------------------------------------------------------ compra y boletos

    override suspend fun comprar(solicitud: SolicitudCompra, pago: PagoElegido?): CompraRealizada =
        MockRepository.comprar(
            usuarioId = yo,
            solicitud = solicitud,
            metodoPago = pago?.metodoPagoId?.let { id -> MockRepository.metodosPago.find { it.id == id } },
            pagoAprobado = pago?.simularRechazo != true,
        ).getOrThrow()

    override suspend fun orden(ordenId: Long): CompraRealizada {
        val orden = MockRepository.ordenes.find { it.id == ordenId }
            ?: throw ErrorEnZona(404, "NO_ENCONTRADO", "La orden no existe.")
        return CompraRealizada(orden, MockRepository.pagos.lastOrNull { it.ordenId == ordenId }, MockRepository.boletosDeOrden(ordenId))
    }

    /** En los datos de prueba OXXO y SPEI se dan por pagados al instante: nunca hay pagos pendientes. */
    override suspend fun confirmarPagoPendiente(pagoId: Long): CompraRealizada =
        throw ErrorEnZona(409, "PAGO_NO_PENDIENTE", "Este pago no está pendiente.")

    override suspend fun misBoletos(): List<BoletoDetalle> = MockRepository.boletosDe(yo)

    override suspend fun boleto(boletoId: Long): BoletoDetalle =
        MockRepository.detalleDeBoleto(boletoId) ?: throw ErrorEnZona(404, "NO_ENCONTRADO", "El boleto no existe.")

    override suspend fun metodosDePago(): List<MetodoPago> = MockRepository.metodosDe(yo)

    override suspend fun agregarTarjeta(numero: String, vencimiento: String, titular: String): MetodoPago = try {
        MockRepository.agregarTarjeta(yo, numero, vencimiento, titular).getOrThrow()
    } catch (e: IllegalArgumentException) {
        throw ErrorEnZona(400, "VALIDACION", e.message ?: "Revisa los datos de la tarjeta.")
    }

    override suspend fun eliminarMetodo(metodoId: Long) = MockRepository.eliminarMetodo(metodoId)

    override suspend fun marcarPredeterminado(metodoId: Long) = MockRepository.marcarPredeterminado(metodoId)

    // ------------------------------------------------------------ validación de acceso

    override suspend fun eventosParaValidar(): List<Evento> = MockRepository.eventosPublicados().map { it.completo() }

    override suspend fun validarQr(contenido: String, eventoId: Long): ResultadoValidacion {
        MockRepository.modoOffline.value = false
        return MockRepository.validarQr(contenido, yo)
    }

    override suspend fun ultimasValidaciones(eventoId: Long?): List<ValidacionAcceso> =
        MockRepository.validaciones.filter { it.validadorId == yo }.takeLast(6).reversed()

    override suspend fun prepararModoSinConexion(eventoId: Long): Int =
        MockRepository.boletosDeEvento(eventoId).count { it.estado == EstadoBoleto.VALIDO }

    override suspend fun validarSinConexion(contenido: String, eventoId: Long): ResultadoValidacion {
        MockRepository.modoOffline.value = true
        return try {
            MockRepository.validarQr(contenido, yo)
        } finally {
            MockRepository.modoOffline.value = false
        }
    }

    override fun pendientesDeSincronizar(): Int = MockRepository.pendientesDeSincronizar()

    override suspend fun sincronizarValidaciones(): ResumenSincronizacion {
        val pendientes = MockRepository.validaciones.filter { !it.sincronizado }
        val permitidas = pendientes.count { it.resultado == com.uv.enzona.data.model.ResultadoAcceso.PERMITIDO }
        val n = MockRepository.sincronizarValidaciones()
        return ResumenSincronizacion(enviadas = n, permitidas = permitidas, rechazadas = n - permitidas, duplicadas = 0)
    }

    // ------------------------------------------------------------ notificaciones

    override suspend fun notificaciones(): List<NotificacionAsistente> = MockRepository.notificacionesDe(yo)

    override suspend fun notificacionesNoLeidas(): Int {
        MockRepository.generarRecordatorios(yo)
        return MockRepository.notificacionesNoLeidas(yo)
    }

    override suspend fun marcarNotificacionLeida(id: Long) = MockRepository.marcarNotificacionLeida(id)

    override suspend fun marcarNotificacionesLeidas() = MockRepository.marcarNotificacionesLeidas(yo)

    // ------------------------------------------------------------ administración

    override suspend fun usuarios(): List<Usuario> = MockRepository.usuarios.toList()

    override suspend fun asignarRol(usuarioId: Long, rol: Rol, asignar: Boolean): Usuario {
        MockRepository.asignarRol(usuarioId, rol, asignar)
        return MockRepository.usuarios.first { it.id == usuarioId }
    }

    override suspend fun cambiarEstadoUsuario(usuarioId: Long, estado: EstadoUsuario): Usuario {
        MockRepository.cambiarEstadoUsuario(usuarioId, estado)
        return MockRepository.usuarios.first { it.id == usuarioId }
    }

    override suspend fun todosLosEventos(): List<Evento> = MockRepository.eventos.map { it.completo() }

    /** El reporte de cada evento se calcula con sus boletos y órdenes, igual que lo haría el servidor. */
    override suspend fun estadisticasAdmin(): EstadisticasAdmin {
        val eventos = MockRepository.eventos.map { it.completo() }
        val reportes = eventos.map { evento ->
            val boletos = MockRepository.boletosDeEvento(evento.id)
            val vigentes = boletos.filter { it.estado == EstadoBoleto.VALIDO || it.estado == EstadoBoleto.USADO }
            ReporteEvento(
                eventoId = evento.id,
                boletosEmitidos = vigentes.size,
                accesosValidados = boletos.count { it.estado == EstadoBoleto.USADO },
                boletosCancelados = boletos.count { it.estado == EstadoBoleto.CANCELADO },
                ingresos = vigentes.sumOf { it.tipoBoleto?.precio ?: 0.0 },
                reembolsado = MockRepository.ordenes.filter { it.eventoId == evento.id && it.estado == EstadoOrden.REEMBOLSADA }.sumOf { it.total },
            )
        }
        return calcularEstadisticas(MockRepository.usuarios.toList(), eventos, reportes)
    }
}
