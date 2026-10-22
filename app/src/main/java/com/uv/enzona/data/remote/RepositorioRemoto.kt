package com.uv.enzona.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.uv.enzona.data.CanalVerificacion
import com.uv.enzona.data.Capacidades
import com.uv.enzona.data.DatosEvento
import com.uv.enzona.data.ErrorEnZona
import com.uv.enzona.data.EstadoVerificacion
import com.uv.enzona.data.ImagenParaSubir
import com.uv.enzona.data.PagoElegido
import com.uv.enzona.data.PersonalAcceso
import com.uv.enzona.data.RegistroPendiente
import com.uv.enzona.data.RepositorioEnZona
import com.uv.enzona.data.ResumenSincronizacion
import com.uv.enzona.data.Ubicacion
import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.Boleto
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.CargoOrganizador
import com.uv.enzona.data.model.Categoria
import com.uv.enzona.data.model.CompraRealizada
import com.uv.enzona.data.model.ErrorCompra
import com.uv.enzona.data.model.EstadisticasAdmin
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.calcularEstadisticas
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.LayoutSeccion
import com.uv.enzona.data.model.MetodoPago
import com.uv.enzona.data.model.NotificacionAsistente
import com.uv.enzona.data.model.Orden
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.data.model.ResultadoAcceso
import com.uv.enzona.data.model.ResultadoCancelacion
import com.uv.enzona.data.model.ResultadoValidacion
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.data.model.TipoMetodoPago
import com.uv.enzona.data.model.Usuario
import com.uv.enzona.data.model.ValidacionAcceso
import com.uv.enzona.util.FechaEvento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Implementación de [RepositorioEnZona] sobre la API REST de EnZona
 * (`EnZona_Api`, Retrofit + OkHttp).
 *
 * - Un interceptor añade `Authorization: Bearer <token>` a cada petición.
 * - El token se guarda en [AlmacenSesion] (cifrado en el dispositivo).
 * - Cualquier error HTTP se convierte en [ErrorEnZona] con el `detail` del
 *   ProblemDetail; la falta de red, en un error con `estado = 0`.
 *
 * Lo que la API no implementa (métodos de pago guardados, OXXO/SPEI, centro de
 * notificaciones, comisión al organizador, roles por administrador, marcar
 * realizado) se declara en [capacidades] y la interfaz lo oculta.
 *
 * No depende de clases de Android: se puede probar en la JVM con MockWebServer.
 */
class RepositorioRemoto(
    urlBase: String,
    private val almacen: AlmacenSesion,
    clienteBase: OkHttpClient? = null,
) : RepositorioEnZona {

    override val esDemostracion: Boolean = false

    /** Ver `docs/COHERENCIA_APP_MOVIL.md` del repositorio de la API. */
    override val capacidades: Capacidades = Capacidades(
        metodosGuardados = false,       // `metodoPagoId` responde 422; el cobro usa un token de un solo uso
        pagosDiferidos = false,         // OXXO y SPEI responden 422 METODO_PAGO_NO_SOPORTADO
        notificaciones = false,         // los avisos van por correo; no hay centro de notificaciones
        cargosOrganizador = false,      // la API reembolsa el 100 % y no cobra comisión
        rolesPorAdministrador = false,  // los roles se obtienen al registrarse, activarse o ser designado
        marcarRealizado = false,        // una tarea del servidor marca los eventos terminados
    )

    private val gson = Gson()

    /** Cuenta con sesión; la fija el inicio de sesión o la restauración. */
    @Volatile
    private var usuarioId: Long = -1L

    @Volatile
    private var usuarioActual: Usuario? = null

    @Volatile
    private var categoriasCache: List<CategoriaDto>? = null

    /** Nombres de los eventos vistos por el validador, para la bitácora local. */
    private val nombresEventos = ConcurrentHashMap<Long, String>()

    private val autorizacion = Interceptor { cadena ->
        val token = almacen.token
        val peticion = if (token.isNullOrBlank() || cadena.request().header("Authorization") != null) cadena.request()
        else cadena.request().newBuilder().header("Authorization", "Bearer $token").build()
        cadena.proceed(peticion)
    }

    private val cliente: OkHttpClient = (clienteBase?.newBuilder() ?: OkHttpClient.Builder())
        .addInterceptor(autorizacion)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)      // una compra hace varias operaciones contra la base
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: ApiService = Retrofit.Builder()
        .baseUrl(if (urlBase.endsWith("/")) urlBase else "$urlBase/")
        .client(cliente)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ApiService::class.java)

    // ==================================================================
    //  Errores
    // ==================================================================

    /** Ejecuta la llamada fuera del hilo principal y traduce los fallos. */
    private suspend fun <T> llamar(bloque: suspend ApiService.() -> T): T = withContext(Dispatchers.IO) {
        try {
            api.bloque()
        } catch (e: HttpException) {
            throw errorDe(e)
        } catch (e: IOException) {
            throw ErrorEnZona(0, "SIN_CONEXION", "No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.")
        } catch (e: JsonParseException) {
            throw ErrorEnZona(0, "RESPUESTA_INVALIDA", "El servidor respondió algo que la app no entiende. Actualiza la app.")
        }
    }

    private fun errorDe(e: HttpException): ErrorEnZona {
        val estado = e.code()
        val cuerpo = try { e.response()?.errorBody()?.string() } catch (_: IOException) { null }
        val problema = try { cuerpo?.takeIf { it.isNotBlank() }?.let { gson.fromJson(it, ProblemaDto::class.java) } } catch (_: Exception) { null }
        if (estado == 401 && problema?.codigo != "CREDENCIALES_INVALIDAS") {
            almacen.token = null        // la sesión expiró o el token dejó de ser válido
        }
        val detalle = problema?.detail?.takeIf { it.isNotBlank() }
        val campos = camposInvalidos(problema)
        val mensaje = when {
            campos.isNotEmpty() -> "${detalle ?: "Revisa los datos"}: ${campos.joinToString(" ")}"
            detalle != null -> detalle
            else -> when (estado) {
                401 -> "Tu sesión expiró. Inicia sesión de nuevo."
                403 -> "Tu cuenta no tiene permiso para realizar esta acción."
                404 -> "No se encontró lo que buscabas."
                429 -> "Demasiados intentos. Espera un momento e inténtalo de nuevo."
                in 500..599 -> "El servidor tuvo un problema. Inténtalo de nuevo en unos minutos."
                else -> "No se pudo completar la operación."
            }
        }
        return ErrorEnZona(
            estado = estado,
            codigo = problema?.codigo ?: "HTTP_$estado",
            mensaje = mensaje,
            propiedades = if (campos.isEmpty()) emptyMap() else mapOf("errores" to campos),
        )
    }

    /** Los 400 de validación traen `errores: [{campo, mensaje}]`; se convierten en texto legible. */
    private fun camposInvalidos(problema: ProblemaDto?): List<String> {
        val lista = problema?.errores?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return lista.mapNotNull { elemento -> elemento.takeIf { it.isJsonObject }?.asJsonObject }.map { objeto ->
            val campo = objeto.get("campo")?.takeIf { !it.isJsonNull }?.asString
            val texto = objeto.get("mensaje")?.takeIf { !it.isJsonNull }?.asString ?: "valor no válido"
            if (campo.isNullOrBlank()) "$texto." else "$campo: $texto."
        }
    }

    /** La compra conserva los tipos de error que usan las pantallas (CP-10, CP-11, CP-33, CP-34, CP-36). */
    private suspend fun errorDeCompra(e: ErrorEnZona, eventoId: Long): Exception {
        val mensaje = e.message ?: "No se pudo completar la compra."
        return when (e.codigo) {
            "AFORO_AGOTADO" -> {
                val disponibles = runCatching { llamar { obtenerEvento(eventoId) }.disponibles }.getOrDefault(0)
                ErrorCompra.SinCupo(disponibles, if (disponibles > 0) "$mensaje Quedan $disponibles." else mensaje)
            }
            "ASIENTO_NO_DISPONIBLE" -> ErrorCompra.AsientosOcupados(emptyList(), mensaje)
            "CONFIRMACION_DUPLICADA" -> ErrorCompra.PoliticaUsuario(mensaje)
            "ESTADO_INVALIDO", "NO_ENCONTRADO" -> ErrorCompra.EventoNoDisponible(mensaje)
            "REGLA_NEGOCIO" -> if (mensaje.contains("asiento", ignoreCase = true)) ErrorCompra.AsientosInvalidos(mensaje) else ErrorCompra.CantidadInvalida(mensaje)
            else -> e
        }
    }

    private fun noDisponible(motivo: String) = ErrorEnZona(501, "NO_DISPONIBLE", motivo)

    // ==================================================================
    //  Identidad
    // ==================================================================

    private fun recordar(u: Usuario): Usuario {
        usuarioId = u.id
        usuarioActual = u
        return u
    }

    override suspend fun iniciarSesion(identificador: String, contrasena: String, accesoPersonal: Boolean): Usuario {
        val sesion = try {
            llamar { iniciarSesion(SesionDto(identificador.trim(), contrasena)) }
        } catch (e: ErrorEnZona) {
            throw cuentaPendiente(e, identificador)
        }
        almacen.token = sesion.token
        almacen.guardar(ACCESO_PERSONAL, if (accesoPersonal) "1" else null)
        // El resumen de la sesión trae id, nombre, correo y roles; el perfil completo (teléfono, verificaciones) sale de /me
        val perfil = runCatching { llamar { yo() } }.getOrNull() ?: sesion.usuario
        return recordar(perfil.aDominio())
    }

    /**
     * 403 CUENTA_PENDIENTE: la app lleva a la pantalla del código. Para verificar
     * hace falta el correo, así que solo procede si se entró con él (no con la CURP).
     */
    private fun cuentaPendiente(e: ErrorEnZona, identificador: String): ErrorEnZona {
        if (e.codigo != "CUENTA_PENDIENTE") return e
        val correo = identificador.trim().lowercase().takeIf { it.contains("@") }
            ?: return ErrorEnZona(e.estado, e.codigo, "Tu cuenta aún no está verificada. Inicia sesión con tu correo para escribir el código de verificación.")
        guardarVerificacion(VerificacionPendiente(0L, correo, listOf(CanalVerificacion.CORREO.name, CanalVerificacion.TELEFONO.name)))
        return ErrorEnZona(
            e.estado, "CUENTA_NO_VERIFICADA",
            "Tu cuenta aún no está verificada. Escribe el código que enviamos a tu correo.",
            mapOf("usuarioId" to 0L),
        )
    }

    override fun sesionDePersonal(): Boolean = almacen.leer(ACCESO_PERSONAL) == "1"

    override suspend fun restaurarSesion(): Usuario? {
        if (almacen.token.isNullOrBlank()) return null
        return try {
            recordar(llamar { yo() }.aDominio())
        } catch (e: ErrorEnZona) {
            if (e.estado == 401 || e.estado == 403) {
                almacen.token = null
                null
            } else throw e
        }
    }

    override suspend fun cerrarSesion() {
        almacen.token = null
        almacen.guardar(ACCESO_PERSONAL, null)
        usuarioId = -1L
        usuarioActual = null
    }

    override suspend fun registrar(
        nombre: String, correo: String, telefono: String, curp: String, contrasena: String, aceptaAvisoPrivacidad: Boolean,
    ): RegistroPendiente {
        val correoLimpio = correo.trim().lowercase()
        // Si se da teléfono, la API también lo verifica con un segundo código; vacío = sin teléfono
        val digitos = telefono.filter { it.isDigit() }.ifBlank { null }
        val r = llamar {
            registrar(RegistroDto(nombre.trim(), correoLimpio, digitos, curp.trim().uppercase(), contrasena, organizador = false, aceptaAvisoPrivacidad = aceptaAvisoPrivacidad))
        }
        val canales = r.verificacionesPendientes.orEmpty().ifEmpty { listOf(CanalVerificacion.CORREO.name) }
        guardarVerificacion(VerificacionPendiente(r.usuarioId, r.correo ?: correoLimpio, canales))
        // La API nunca devuelve el código: en desarrollo sale en la consola del servidor (NOTIFICATIONS_MODE=log)
        return RegistroPendiente(r.usuarioId, otpSimulado = null, correo = correoLimpio, canalesPendientes = canales.mapNotNull(::canal))
    }

    private fun canal(nombre: String): CanalVerificacion? = CanalVerificacion.entries.firstOrNull { it.name == nombre }

    override suspend fun verificarOtp(usuarioId: Long, codigo: String): EstadoVerificacion {
        val pendiente = verificacionPendiente() ?: throw sinVerificacionPendiente()
        val canal = pendiente.pendientes.firstOrNull() ?: CanalVerificacion.CORREO.name
        val r = llamar { verificar(VerificacionDto(pendiente.correo, canal, codigo.trim())) }
        val restantes = r.verificacionesPendientes.orEmpty()
        if (restantes.isEmpty() || r.estado == "ACTIVO") {
            guardarVerificacion(null)
            return EstadoVerificacion(completa = true)
        }
        guardarVerificacion(pendiente.copy(pendientes = restantes))
        return EstadoVerificacion(completa = false, siguienteCanal = canal(restantes.first()))
    }

    override suspend fun reenviarOtp(usuarioId: Long): String? {
        val pendiente = verificacionPendiente() ?: throw sinVerificacionPendiente()
        llamar { reenviarCodigo(ReenvioDto(pendiente.correo, pendiente.pendientes.firstOrNull() ?: CanalVerificacion.CORREO.name)) }
        return null     // el código nunca viaja en la respuesta
    }

    private fun sinVerificacionPendiente() =
        ErrorEnZona(409, "SIN_VERIFICACION_PENDIENTE", "No hay una verificación pendiente en este dispositivo. Inicia sesión con tu correo para continuar.")

    /** Verificación en curso: la API identifica la cuenta por correo y canal, no por id. */
    internal data class VerificacionPendiente(val usuarioId: Long, val correo: String, val pendientes: List<String>)

    private fun verificacionPendiente(): VerificacionPendiente? =
        almacen.leer(VERIFICACION)?.let { runCatching { gson.fromJson(it, VerificacionPendiente::class.java) }.getOrNull() }

    private fun guardarVerificacion(v: VerificacionPendiente?) = almacen.guardar(VERIFICACION, v?.let { gson.toJson(it) })

    override suspend fun usuarioActual(): Usuario = recordar(llamar { yo() }.aDominio())

    override suspend fun actualizarPerfil(nombre: String, telefono: String): Usuario =
        recordar(llamar { actualizarPerfil(PerfilDto(nombre.trim(), telefono.filter { it.isDigit() }.ifBlank { null })) }.aDominio())

    override suspend fun cambiarContrasena(actual: String, nueva: String) {
        llamar { cambiarContrasena(CambiarContrasenaDto(actual, nueva)) }
    }

    /** La respuesta es un resumen (sin teléfono ni verificaciones): se vuelve a leer el perfil completo. */
    override suspend fun convertirseEnOrganizador(): Usuario {
        llamar { convertirseEnOrganizador() }
        return usuarioActual()
    }

    override suspend fun dejarDeSerOrganizador(): Usuario {
        llamar { dejarDeSerOrganizador() }
        return usuarioActual()
    }

    /** La API no tiene consulta previa: se aplica aquí la misma regla que el servidor (409 ROL_EN_USO). */
    override suspend fun motivoNoPuedeDejarOrganizador(): String? {
        val cuenta = usuarioActual ?: usuarioActual()
        if (cuenta.roles.size == 1) return "El rol de organizador es el único rol de la cuenta."
        val ahora = FechaEvento.ahora()
        val vigentes = llamar { misEventos() }.count { e ->
            e.estado == "PUBLICADO" && (fecha(e.fechaHoraFin) ?: fecha(e.fechaHoraInicio)?.plusHours(6))?.isAfter(ahora) == true
        }
        return if (vigentes > 0) "Tienes $vigentes evento(s) publicado(s) vigente(s); cancélalos o espera a que terminen." else null
    }

    // ==================================================================
    //  Eventos
    // ==================================================================

    private suspend fun categoriasDto(): List<CategoriaDto> = categoriasCache ?: llamar { categorias() }.also { categoriasCache = it }

    /** La app trabaja con el nombre de la categoría; la API pide su id. */
    private suspend fun categoriaId(nombre: String): Int? =
        nombre.trim().takeIf { it.isNotBlank() }?.let { n -> categoriasDto().firstOrNull { it.nombre.equals(n, ignoreCase = true) }?.id }

    override suspend fun categorias(): List<Categoria> = categoriasDto().map { it.aDominio() }

    override suspend fun eventosPublicados(ciudad: String?, ubicacion: Ubicacion?): List<Evento> =
        llamar {
            // Con ubicación (consentimiento dado) el servidor ordena por distancia dentro del radio; sin ella, filtra por ciudad
            if (ubicacion != null) listarEventos(lat = ubicacion.latitud, lng = ubicacion.longitud, radioKm = RADIO_KM)
            else listarEventos(ciudad = ciudad)
        }.map { it.aDominio() }

    override suspend fun evento(id: Long): Evento = llamar { obtenerEvento(id) }.aDominio()

    /** Los tipos vienen dentro del detalle del evento. */
    override suspend fun tiposDe(eventoId: Long): List<TipoBoleto> = llamar { obtenerEvento(eventoId) }.tipos()

    private fun EventoDto.tipos(): List<TipoBoleto> = tiposBoleto.orEmpty().map { it.aDominio(id) }

    override suspend fun asientosDe(eventoId: Long): List<Asiento> = llamar { asientosDe(eventoId) }.map { it.aDominio(eventoId) }

    override suspend fun misEventos(): List<Evento> = llamar { misEventos() }.map { it.aDominio() }

    override suspend fun crearEvento(datos: DatosEvento, publicar: Boolean): Evento {
        val solicitud = EventoSolicitudDto(
            nombre = datos.nombre.trim(), descripcion = datos.descripcion.trim().ifBlank { null },
            lugar = datos.lugar.trim(), direccion = datos.direccion.trim().ifBlank { null }, ciudad = datos.ciudad,
            latitud = datos.latitud, longitud = datos.longitud, categoriaId = categoriaId(datos.categoria),
            fechaHoraInicio = FechaEvento.iso(datos.fecha), fechaHoraFin = datos.fechaFin?.let { FechaEvento.iso(it) },
            aforo = datos.aforo, esDePago = datos.esDePago, requiereAsiento = datos.secciones.isNotEmpty(),
            // La app maneja un precio por evento: un tipo «General» con todo el aforo (el precio vive en tipo_boleto, RNF-09)
            tiposBoleto = if (datos.esDePago) listOf(TipoBoletoSolicitudDto(null, TIPO_GENERAL, datos.precio, datos.aforo)) else null,
            secciones = datos.secciones.takeIf { it.isNotEmpty() }?.map { SeccionDto(it.nombre, it.filas, it.asientosPorFila) },
            publicar = publicar,
        )
        return llamar { crearEvento(solicitud) }.aDominio()
    }

    override suspend fun modificarEvento(eventoId: Long, datos: DatosEvento): Evento {
        val actual = llamar { obtenerEvento(eventoId) }
        val solicitud = EventoSolicitudDto(
            nombre = datos.nombre.trim(), descripcion = datos.descripcion.trim().ifBlank { null },
            lugar = datos.lugar.trim(), direccion = datos.direccion.trim().ifBlank { null }, ciudad = datos.ciudad,
            latitud = datos.latitud, longitud = datos.longitud, categoriaId = categoriaId(datos.categoria),
            fechaHoraInicio = FechaEvento.iso(datos.fecha), fechaHoraFin = datos.fechaFin?.let { FechaEvento.iso(it) },
            aforo = datos.aforo, esDePago = datos.esDePago,
            requiereAsiento = actual.requiereAsiento,          // el plano se cambia con cambiarAsientos
            tiposBoleto = tiposConservados(actual, datos.esDePago, datos.precio, datos.aforo),
            secciones = null,                                  // null = el mapa de asientos no cambia
            publicar = false,
        )
        return llamar { modificarEvento(eventoId, solicitud) }.aDominio()
    }

    /**
     * Los tipos se reenvían con su id para conservarlos (uno omitido se borra, si no
     * tiene ventas). Con un solo tipo, el precio y la cantidad del formulario se
     * aplican a ese tipo; con varios (creados desde la web) se conservan tal cual.
     */
    private fun tiposConservados(actual: EventoDto, esDePago: Boolean, precio: Double, aforo: Int): List<TipoBoletoSolicitudDto>? {
        if (!esDePago) return null
        val actuales = actual.tiposBoleto.orEmpty()
        return when (actuales.size) {
            0 -> listOf(TipoBoletoSolicitudDto(null, TIPO_GENERAL, precio, aforo))
            1 -> listOf(TipoBoletoSolicitudDto(actuales[0].id, actuales[0].nombre, precio, aforo))
            else -> actuales.map { TipoBoletoSolicitudDto(it.id, it.nombre, importe(it.precio), it.cantidadTotal ?: 0) }
        }
    }

    override suspend fun publicarEvento(eventoId: Long): Evento = llamar { publicarEvento(eventoId) }.aDominio()

    override suspend fun marcarRealizado(eventoId: Long): Evento =
        throw noDisponible("La API marca los eventos como realizados automáticamente cuando terminan.")

    /** El mapa se cambia con la misma modificación del evento; el aforo pasa a ser el total de asientos. */
    override suspend fun cambiarAsientos(eventoId: Long, secciones: List<LayoutSeccion>): Evento {
        val actual = llamar { obtenerEvento(eventoId) }
        val aforo = if (secciones.isEmpty()) actual.aforo else secciones.sumOf { it.filas * it.asientosPorFila }
        val solicitud = EventoSolicitudDto(
            nombre = actual.nombre, descripcion = actual.descripcion, lugar = actual.lugar, direccion = actual.direccion,
            ciudad = actual.ciudad, latitud = actual.latitud, longitud = actual.longitud, categoriaId = actual.categoriaId,
            fechaHoraInicio = actual.fechaHoraInicio, fechaHoraFin = actual.fechaHoraFin,
            aforo = aforo, esDePago = actual.esDePago, requiereAsiento = secciones.isNotEmpty(),
            tiposBoleto = tiposConservados(actual, actual.esDePago, importe(actual.tiposBoleto?.firstOrNull()?.precio), aforo),
            secciones = secciones.takeIf { it.isNotEmpty() }?.map { SeccionDto(it.nombre, it.filas, it.asientosPorFila) },
            publicar = false,
        )
        return llamar { modificarEvento(eventoId, solicitud) }.aDominio()
    }

    override suspend fun subirImagenEvento(eventoId: Long, imagen: ImagenParaSubir): Evento {
        val extension = when (imagen.tipoMime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val parte = MultipartBody.Part.createFormData(
            "imagen", "portada.$extension", imagen.bytes.toRequestBody(imagen.tipoMime.toMediaType()),
        )
        return llamar { subirImagen(eventoId, parte) }.aDominio()
    }

    override suspend fun quitarImagenEvento(eventoId: Long): Evento = llamar { quitarImagen(eventoId) }.aDominio()

    /** El servidor decide si es administrativa según el rol de quien consulta; el parámetro no se envía. */
    override suspend fun politicaCancelacion(eventoId: Long, administrativa: Boolean): PoliticaCancelacion =
        llamar { politicaCancelacion(eventoId) }.aDominio()

    override suspend fun cancelarEvento(eventoId: Long, motivo: String?, aceptaComision: Boolean, administrativa: Boolean): ResultadoCancelacion =
        llamar { cancelarEvento(eventoId, CancelacionDto(motivo?.trim()?.ifBlank { null }, aceptaComision)) }.aDominio()

    /**
     * La API no expone al organizador la lista de boletos de su evento; el
     * manifiesto (boletos VALIDO y USADO, con tipo y asiento) trae lo que el panel
     * necesita para contar emitidos, validados e ingresos.
     */
    override suspend fun boletosDeEvento(eventoId: Long): List<BoletoDetalle> {
        val detalle = llamar { obtenerEvento(eventoId) }
        val evento = detalle.aDominio()
        val precios = detalle.tiposBoleto.orEmpty().associate { it.nombre to importe(it.precio) }
        val manifiesto = llamar { manifiesto(eventoId) }
        return manifiesto.boletos.orEmpty().mapIndexed { indice, entrada ->
            val precio = precios[entrada.tipoBoleto] ?: 0.0
            BoletoDetalle(
                boleto = Boleto(
                    id = (indice + 1).toLong(), ordenId = 0L, tipoBoletoId = null, asientoId = null, codigo = entrada.codigo,
                    qrFirma = entrada.firma.orEmpty(), estado = enumerado(entrada.estado, EstadoBoleto.VALIDO), fechaEmision = "",
                ),
                orden = Orden(id = 0L, asistenteId = 0L, eventoId = eventoId, total = precio, estado = EstadoOrden.PAGADA, fechaCreacion = ""),
                evento = evento,
                tipoBoleto = TipoBoleto(0L, eventoId, entrada.tipoBoleto ?: TIPO_GENERAL, precio, 0, 0),
                asiento = asientoDesdeEtiqueta(null, eventoId, entrada.asiento),
            )
        }
    }

    override suspend fun cargosDeOrganizador(): List<CargoOrganizador> = emptyList()   // la API no cobra comisión

    override suspend fun personalDeAcceso(eventoId: Long): List<PersonalAcceso> = llamar { validadoresDe(eventoId) }.map { it.aDominio() }

    override suspend fun designarValidador(eventoId: Long, correo: String): List<PersonalAcceso> {
        llamar { designarValidador(eventoId, DesignarDto(correo.trim().lowercase())) }
        return personalDeAcceso(eventoId)
    }

    override suspend fun quitarValidador(eventoId: Long, usuarioId: Long): List<PersonalAcceso> {
        llamar { quitarValidador(eventoId, usuarioId) }
        return personalDeAcceso(eventoId)
    }

    // ==================================================================
    //  Compra y boletos
    // ==================================================================

    override suspend fun comprar(solicitud: SolicitudCompra, pago: PagoElegido?): CompraRealizada {
        val clave = claveValida(solicitud.claveOperacion)
        val cuerpo = when {
            pago == null -> orden(solicitud, metodo = "GRATIS", token = null)
            // La tokenización la haría el SDK de la pasarela en el teléfono; en sandbox se usan sus tokens fijos (CP-10)
            pago.medio == TipoMetodoPago.TARJETA -> orden(solicitud, metodo = "TARJETA", token = if (pago.simularRechazo) TOKEN_RECHAZO else TOKEN_APROBADO)
            else -> orden(solicitud, metodo = pago.medio.name, token = null)   // OXXO/SPEI: la API responde 422
        }
        val respuesta = try {
            llamar { crearOrden(clave, cuerpo) }
        } catch (e: ErrorEnZona) {
            throw errorDeCompra(e, solicitud.eventoId)
        }
        // Un pago rechazado es un 201 con la orden CANCELADA: la app lo trata como el error recuperable de siempre
        if (respuesta.estadoPago == "RECHAZADO") {
            throw ErrorCompra.PagoRechazado(respuesta.mensaje ?: "La pasarela rechazó el pago. No se realizó ningún cargo y tus lugares siguen disponibles.")
        }
        return respuesta.aCompra()
    }

    private fun orden(s: SolicitudCompra, metodo: String, token: String?) =
        CrearOrdenDto(s.eventoId, s.tipoBoletoId, s.cantidad, s.asientosIds, metodo, token)

    /** `Idempotency-Key` admite de 8 a 64 caracteres [A-Za-z0-9_-]; una clave vacía o inválida se sustituye. */
    private fun claveValida(clave: String): String {
        val limpia = clave.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (limpia.length in 8..64) limpia else UUID.randomUUID().toString()
    }

    /** La orden no trae el evento: se lee su detalle (público) para la pantalla de resumen y los boletos. */
    private suspend fun OrdenDto.aCompra(): CompraRealizada {
        val detalle = llamar { obtenerEvento(eventoId) }
        val precios: Map<Long?, Double> = detalle.tiposBoleto.orEmpty().associate { it.id to importe(it.precio) }
        return aDominio(detalle.aDominio(), usuarioId, precios)
    }

    override suspend fun orden(ordenId: Long): CompraRealizada = llamar { obtenerOrden(ordenId) }.aCompra()

    override suspend fun confirmarPagoPendiente(pagoId: Long): CompraRealizada =
        throw noDisponible("La confirmación de un pago pendiente la envía la pasarela al servidor; la app no puede simularla con esta API.")

    override suspend fun misBoletos(): List<BoletoDetalle> {
        val boletos = llamar { misBoletos() }
        if (boletos.isEmpty()) return emptyList()
        return armar(boletos, detallesDe(boletos.map { it.eventoId }.distinct()))
    }

    /** Detalle de cada evento en paralelo; si uno no está disponible se usa lo que trae el boleto. */
    private suspend fun detallesDe(ids: List<Long>): Map<Long, EventoDto?> = coroutineScope {
        ids.map { id -> async { id to runCatching { llamar { obtenerEvento(id) } }.getOrNull() } }.awaitAll().toMap()
    }

    /**
     * La API no anida la orden en el boleto: el total se reconstruye con los boletos
     * hermanos (misma orden) y el precio de su tipo; el estado, con el del evento.
     */
    private fun armar(boletos: List<BoletoDto>, detalles: Map<Long, EventoDto?>): List<BoletoDetalle> {
        val eventos = boletos.map { it.eventoId }.distinct().associateWith { id -> detalles[id]?.aDominio() ?: boletos.first { it.eventoId == id }.eventoResumen() }
        val precios: Map<Long, Map<Long?, Double>> = detalles.mapValues { (_, d) -> d?.tiposBoleto.orEmpty().associate { it.id to importe(it.precio) } }
        val porOrden = boletos.groupBy { it.ordenId }
        return boletos.map { b ->
            val evento = eventos.getValue(b.eventoId)
            val hermanos = porOrden.getValue(b.ordenId)
            val total = hermanos.sumOf { h -> precios[h.eventoId]?.get(h.tipoBoletoId) ?: 0.0 }
            val estado = when {
                !evento.cancelado -> EstadoOrden.PAGADA
                total > 0 -> EstadoOrden.REEMBOLSADA
                else -> EstadoOrden.CANCELADA
            }
            val orden = Orden(b.ordenId, usuarioId, b.eventoId, total, estado, fechaTexto(hermanos.first().fechaEmision))
            b.aDominio(evento, orden, precios[b.eventoId]?.get(b.tipoBoletoId))
        }
    }

    override suspend fun boleto(boletoId: Long): BoletoDetalle =
        misBoletos().firstOrNull { it.id == boletoId } ?: throw ErrorEnZona(404, "NO_ENCONTRADO", "El boleto no existe.")

    // ---------------- métodos de pago: la API no los guarda (capacidades.metodosGuardados = false) ----------------

    override suspend fun metodosDePago(): List<MetodoPago> = emptyList()

    override suspend fun agregarTarjeta(numero: String, vencimiento: String, titular: String): MetodoPago = throw sinMetodosGuardados()

    override suspend fun eliminarMetodo(metodoId: Long): Unit = throw sinMetodosGuardados()

    override suspend fun marcarPredeterminado(metodoId: Long): Unit = throw sinMetodosGuardados()

    private fun sinMetodosGuardados() =
        noDisponible("La API de EnZona no guarda métodos de pago: cada cobro usa un token de un solo uso de la pasarela.")

    // ==================================================================
    //  Validación de acceso
    // ==================================================================

    /**
     * La API no lista los eventos donde la cuenta fue designada. Un organizador ve
     * sus eventos publicados y un administrador todos; un validador designado ve
     * el catálogo publicado y el servidor rechaza (403) los eventos ajenos.
     */
    override suspend fun eventosParaValidar(): List<Evento> {
        val roles = (usuarioActual ?: runCatching { usuarioActual() }.getOrNull())?.roles.orEmpty()
        val propios = if (Rol.ORGANIZADOR in roles) llamar { misEventos() } else emptyList()
        val supervisados = if (Rol.ADMIN in roles) llamar { todosLosEventos(estado = "PUBLICADO") } else emptyList()
        val publicados = if (Rol.VALIDADOR in roles) llamar { listarEventos() } else emptyList()
        return (propios + supervisados + publicados)
            .filter { it.estado == "PUBLICADO" }
            .distinctBy { it.id }
            .onEach { nombresEventos[it.id] = it.nombre }
            .map { it.aDominio() }
    }

    override suspend fun validarQr(contenido: String, eventoId: Long): ResultadoValidacion {
        val qr = contenido.trim()
        val resultado = llamar { validar(ValidarDto(eventoId, qr)) }.aDominio()
        anotar(RegistroLocal(eventoId, nombresEventos[eventoId].orEmpty(), resultado.codigo ?: qr.substringBefore("."), ahoraTexto(), resultado.permitido, resultado.titulo, offline = false))
        return resultado
    }

    /** La API no expone la bitácora al validador: se muestra lo validado desde este dispositivo. */
    override suspend fun ultimasValidaciones(eventoId: Long?): List<ValidacionAcceso> {
        // Los escaneos hechos sin conexión que aún no se suben van primero, marcados como pendientes
        val pendientes = pendientes().filter { eventoId == null || it.eventoId == eventoId }.reversed().mapIndexed { i, p ->
            ValidacionAcceso(
                id = -(i + 1L), boletoId = null, codigoBoleto = p.contenidoQr.substringBefore("."), eventoNombre = p.eventoNombre,
                validadorId = usuarioId, fechaHora = fechaTexto(p.fechaEscaneo),
                resultado = if (p.permitido) ResultadoAcceso.PERMITIDO else ResultadoAcceso.RECHAZADO,
                motivo = p.titulo, offline = true, sincronizado = false,
            )
        }
        val locales = bitacora().filter { eventoId == null || it.eventoId == eventoId }.reversed().mapIndexed { i, r ->
            ValidacionAcceso(
                id = i + 1L, boletoId = null, codigoBoleto = r.codigo, eventoNombre = r.eventoNombre, validadorId = usuarioId,
                fechaHora = r.fechaHora, resultado = if (r.permitido) ResultadoAcceso.PERMITIDO else ResultadoAcceso.RECHAZADO,
                motivo = r.titulo, offline = r.offline, sincronizado = true,
            )
        }
        return (pendientes + locales).take(6)
    }

    /** Validación ya resuelta por el servidor (en línea o sincronizada), guardada en el dispositivo. */
    internal data class RegistroLocal(
        val eventoId: Long, val eventoNombre: String, val codigo: String, val fechaHora: String,
        val permitido: Boolean, val titulo: String, val offline: Boolean,
    )

    private fun bitacora(): List<RegistroLocal> =
        almacen.leer(BITACORA)?.let { json ->
            runCatching { gson.fromJson<List<RegistroLocal>>(json, object : TypeToken<List<RegistroLocal>>() {}.type) }.getOrNull()
        } ?: emptyList()

    private fun anotar(vararg registros: RegistroLocal) =
        almacen.guardar(BITACORA, gson.toJson((bitacora() + registros).takeLast(MAXIMO_BITACORA)))

    // ---------------- modo sin conexión (RNF-08, CP-14) ----------------

    /** Escaneo hecho sin conexión, en cola para `POST /api/validaciones/sincronizar`. */
    internal data class Pendiente(
        val eventoId: Long, val eventoNombre: String, val contenidoQr: String, val fechaEscaneo: String,
        val permitido: Boolean, val titulo: String,
    )

    private fun claveManifiesto(eventoId: Long) = "manifiesto_$eventoId"

    private fun manifiestoGuardado(eventoId: Long): ManifiestoDto? =
        almacen.leer(claveManifiesto(eventoId))?.let { runCatching { gson.fromJson(it, ManifiestoDto::class.java) }.getOrNull() }

    private fun pendientes(): List<Pendiente> =
        almacen.leer(PENDIENTES)?.let { json ->
            runCatching { gson.fromJson<List<Pendiente>>(json, object : TypeToken<List<Pendiente>>() {}.type) }.getOrNull()
        } ?: emptyList()

    private fun guardarPendientes(lista: List<Pendiente>) =
        almacen.guardar(PENDIENTES, if (lista.isEmpty()) null else gson.toJson(lista))

    override suspend fun prepararModoSinConexion(eventoId: Long): Int {
        val manifiesto = llamar { manifiesto(eventoId) }
        almacen.guardar(claveManifiesto(eventoId), gson.toJson(manifiesto))
        manifiesto.evento?.let { nombresEventos[eventoId] = it }
        return manifiesto.boletos.orEmpty().count { it.estado == "VALIDO" }
    }

    /**
     * Sin conexión no se puede verificar la firma (la clave HMAC nunca sale del
     * servidor): se comprueba que el QR leído (código y firma) esté en el manifiesto
     * descargado y que el boleto no se haya admitido ya en este dispositivo. El
     * servidor aplica la regla definitiva al sincronizar.
     */
    override suspend fun validarSinConexion(contenido: String, eventoId: Long): ResultadoValidacion = withContext(Dispatchers.Default) {
        val manifiesto = manifiestoGuardado(eventoId)
            ?: throw ErrorEnZona(409, "SIN_MANIFIESTO", "Descarga primero el manifiesto del evento (con conexión) para validar sin conexión.")
        val qr = contenido.trim()
        val nombre = manifiesto.evento.orEmpty()
        val entrada = manifiesto.boletos.orEmpty().firstOrNull { "${it.codigo}.${it.firma.orEmpty()}" == qr }
        val yaAdmitido = pendientes().any { it.eventoId == eventoId && it.contenidoQr == qr && it.permitido }
        val codigo = qr.substringBefore(".").takeIf { qr.contains(".") }

        val (permitido, titulo, detalle) = when {
            entrada == null -> Triple(false, "Boleto no reconocido", "El código no pertenece al manifiesto de «$nombre».")
            yaAdmitido -> Triple(false, "Boleto ya utilizado", "Este dispositivo ya admitió este boleto sin conexión.")
            entrada.estado == "USADO" -> Triple(false, "Boleto ya utilizado", "Ya se había registrado su acceso antes de descargar el manifiesto.")
            entrada.estado == "CANCELADO" -> Triple(false, "Boleto cancelado", "El boleto se anuló.")
            entrada.estado != "VALIDO" -> Triple(false, "Boleto expirado", "La vigencia de este boleto ya terminó.")
            else -> Triple(true, "Acceso permitido", listOfNotNull(nombre.ifBlank { null }, entrada.tipoBoleto, entrada.asiento).joinToString(" · ") + " · sin conexión, pendiente de sincronizar")
        }
        guardarPendientes(pendientes() + Pendiente(eventoId, nombre, qr, OffsetDateTime.now(ZoneOffset.UTC).toString(), permitido, titulo))
        ResultadoValidacion(permitido, titulo, detalle, codigo, entrada?.asiento)
    }

    override fun pendientesDeSincronizar(): Int = pendientes().size

    override suspend fun sincronizarValidaciones(): ResumenSincronizacion {
        val cola = pendientes()
        if (cola.isEmpty()) return ResumenSincronizacion(0, 0, 0, 0)
        var permitidas = 0
        var rechazadas = 0
        var duplicadas = 0
        var restantes = cola
        for ((eventoId, delEvento) in cola.groupBy { it.eventoId }) {
            val r = llamar { sincronizar(SincronizacionDto(eventoId, delEvento.map { RegistroOfflineDto(it.contenidoQr, it.fechaEscaneo) })) }
            permitidas += r.permitidos ?: 0
            rechazadas += r.rechazados ?: 0
            // «duplicado»: ya se había sincronizado; «conflicto»: otro dispositivo admitió antes el mismo boleto
            duplicadas += r.resultados.orEmpty().count { it.duplicado == true || it.conflicto == true }
            // Lo sincronizado pasa a la bitácora local con el veredicto del servidor. El servidor responde en
            // orden cronológico, igual que la cola; el mismo QR puede repetirse (admitido y luego rechazado),
            // así que se empareja por posición y no por contenido
            val enviados = delEvento.sortedBy { it.fechaEscaneo }
            val resultados = r.resultados.orEmpty()
            anotar(
                *enviados.mapIndexed { i, p ->
                    val v = resultados.getOrNull(i)?.takeIf { it.qr == p.contenidoQr }
                    RegistroLocal(
                        eventoId, p.eventoNombre, p.contenidoQr.substringBefore("."), fechaTexto(p.fechaEscaneo),
                        permitido = if (v != null) v.resultado == "PERMITIDO" else p.permitido,
                        titulo = v?.let { tituloValidacion(it.resultado, it.motivo, it.conflicto == true) } ?: p.titulo,
                        offline = true,
                    )
                }.toTypedArray(),
            )
            // Se quita de la cola solo lo que el servidor ya recibió
            restantes = restantes.filterNot { it.eventoId == eventoId }
            guardarPendientes(restantes)
            almacen.guardar(claveManifiesto(eventoId), null)
        }
        return ResumenSincronizacion(cola.size, permitidas, rechazadas, duplicadas)
    }

    // ==================================================================
    //  Notificaciones: la API avisa por correo; no hay centro en la app (capacidades.notificaciones = false)
    // ==================================================================

    override suspend fun notificaciones(): List<NotificacionAsistente> = emptyList()

    override suspend fun notificacionesNoLeidas(): Int = 0

    override suspend fun marcarNotificacionLeida(id: Long) = Unit

    override suspend fun marcarNotificacionesLeidas() = Unit

    // ==================================================================
    //  Administración
    // ==================================================================

    override suspend fun usuarios(): List<Usuario> = llamar { usuarios() }.map { it.aDominio() }

    override suspend fun asignarRol(usuarioId: Long, rol: Rol, asignar: Boolean): Usuario =
        throw noDisponible("La API de EnZona no permite cambiar roles desde la administración: se obtienen al registrarse, al activarse como organizador o al ser designado validador.")

    override suspend fun cambiarEstadoUsuario(usuarioId: Long, estado: EstadoUsuario): Usuario =
        llamar { cambiarEstado(usuarioId, EstadoDto(estado.name)) }.aDominio()

    override suspend fun todosLosEventos(): List<Evento> = llamar { todosLosEventos() }.map { it.aDominio() }

    /**
     * Cuentas y eventos de administración más el reporte de cada evento que no
     * sea borrador. Los reportes se piden en paralelo, de pocos en pocos, para
     * no agotar el pool de conexiones de la API (Aiven admite 20 en total).
     */
    override suspend fun estadisticasAdmin(): EstadisticasAdmin = coroutineScope {
        val usuarios = async { llamar { usuarios() }.map { it.aDominio() } }
        val eventos = llamar { todosLosEventos() }.map { it.aDominio() }
        val reportes = eventos.filter { it.estado != EstadoEvento.BORRADOR }
            .chunked(REPORTES_EN_PARALELO)
            .flatMap { grupo -> grupo.map { e -> async { runCatching { llamar { reporteDe(e.id) }.aDominio() }.getOrNull() } }.awaitAll() }
            .filterNotNull()
        calcularEstadisticas(usuarios.await(), eventos, reportes)
    }

    companion object {
        private const val REPORTES_EN_PARALELO = 4
        /** Tokens de la pasarela `sandbox` de la API (no hay proveedor contratado). */
        const val TOKEN_APROBADO = "tok_aprobado"
        const val TOKEN_RECHAZO = "tok_rechazado"
        /** Radio de búsqueda por cercanía; la API admite hasta `EVENT_MAX_RADIUS_KM` (50 por defecto). */
        const val RADIO_KM = 50.0
        /** Nombre del único tipo de boleto que crea la app (la API usa el mismo en eventos gratuitos). */
        const val TIPO_GENERAL = "General"
        private const val MAXIMO_BITACORA = 50
        private const val PENDIENTES = "validaciones_pendientes"
        private const val BITACORA = "validaciones_locales"
        private const val VERIFICACION = "verificacion_pendiente"
        private const val ACCESO_PERSONAL = "acceso_personal"
    }
}
