package com.uv.enzona.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.Boleto
import com.uv.enzona.data.model.CargoOrganizador
import com.uv.enzona.data.model.NotificacionAsistente
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.data.model.ResultadoCancelacion
import com.uv.enzona.data.model.TipoCancelacion
import com.uv.enzona.data.model.PORCENTAJE_COMISION_CANCELACION
import com.uv.enzona.data.model.politicaCancelacion
import com.uv.enzona.data.model.CompraRealizada
import com.uv.enzona.data.model.ErrorCompra
import com.uv.enzona.data.model.SolicitudCompra
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.EstadoAsiento
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.EstadoPago
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.LayoutSeccion
import com.uv.enzona.data.model.MetodoPago
import com.uv.enzona.data.model.Orden
import com.uv.enzona.data.model.Pago
import com.uv.enzona.data.model.ResultadoAcceso
import com.uv.enzona.data.model.ResultadoValidacion
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.data.model.TipoMetodoPago
import com.uv.enzona.data.model.Usuario
import com.uv.enzona.data.model.ValidacionAcceso
import com.uv.enzona.util.FechaEvento
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Repositorio de PRUEBA: reproduce en memoria las tablas de `esquema_enzona.sql`
 * para poder usar la app completa sin backend.
 *
 * Cuando el backend Spring Boot esté listo se sustituye por uno que consuma
 * la API (ver data/remote/ApiService.kt) manteniendo las mismas funciones.
 */
object MockRepository {

    // En producción la clave HMAC vive SOLO en el servidor (RNF-02).
    private const val CLAVE_DEMO = "clave-demo-enzona-no-usar-en-produccion"

    private var siguienteUsuario = 1L
    private var siguienteEvento = 1L
    private var siguienteTipo = 1L
    private var siguienteAsiento = 1L
    private var siguienteOrden = 1L
    private var siguientePago = 1L
    private var siguienteBoleto = 1L
    private var siguienteValidacion = 1L

    // ------------------------------------------------------------------ tablas
    val usuarios = mutableStateListOf<Usuario>()
    val eventos = mutableStateListOf<Evento>()
    val tiposBoleto = mutableStateListOf<TipoBoleto>()
    val asientos = mutableStateListOf<Asiento>()
    val ordenes = mutableStateListOf<Orden>()
    val pagos = mutableStateListOf<Pago>()
    val boletos = mutableStateListOf<Boleto>()
    val validaciones = mutableStateListOf<ValidacionAcceso>()

    val metodosPago = mutableStateListOf<MetodoPago>()

    /** v3: cargos pendientes del organizador (comisión de cancelación) y avisos a asistentes. */
    val cargosOrganizador = mutableStateListOf<CargoOrganizador>()
    val notificaciones = mutableStateListOf<NotificacionAsistente>()
    private var siguienteCargo = 1L
    private var siguienteNotificacion = 1L

    /** OTP pendiente por usuario (en producción se envía por correo/SMS). */
    private val otpPendientes = mutableMapOf<Long, String>()

    // ---- Preferencias de zona (enfoque hiperlocal del proyecto) ----
    val ciudad = mutableStateOf("Orizaba, Veracruz")
    val pais = mutableStateOf("México")
    val contenidoPorUbicacion = mutableStateOf(true)

    /** Modo sin conexión del validador (RNF-08). */
    val modoOffline = mutableStateOf(false)

    init { sembrarDatos() }

    // ==================================================================
    //  Datos de demostración
    // ==================================================================

    /** Fecha de muestra en la zona del evento (año 2026, área de estudio Orizaba). */
    private fun f(mes: Int, dia: Int, hora: Int, minuto: Int): LocalDateTime = LocalDateTime.of(2026, mes, dia, hora, minuto)

    private fun sembrarDatos() {
        val organizador = nuevoUsuario(
            "Nancy Cruz", "organizador@uv.mx", "ORGA850101HVZRRN04",
            setOf(Rol.ORGANIZADOR, Rol.ASISTENTE)
        )
        nuevoUsuario("Luis Puerta", "validador@uv.mx", "VALI900202HVZLRS08", setOf(Rol.VALIDADOR))
        nuevoUsuario("Admin EnZona", "admin@uv.mx", "ADMI880303HVZDMN01", setOf(Rol.ADMIN, Rol.ASISTENTE))
        nuevoUsuario("Ana Estudiante", "asistente@uv.mx", "ASIS020404MVZSNN09", setOf(Rol.ASISTENTE))

        val oid = organizador.id

        val feria = nuevoEvento(
            oid, "Feria de Emprendimiento FCAS",
            "Muestra de proyectos y startups universitarias. Stands, conferencias y networking con empresas de la región.",
            "Facultad de Negocios y Tecnologías", "Campus Orizaba, UV",
            "Académico", f(9, 25, 10, 0), esDePago = false, precio = 0.0, aforo = 300, semilla = 0,
            latitud = 18.85030, longitud = -97.10360,   // Facultad de Negocios y Tecnologías, campus Orizaba (aprox.)
        )
        eventos[eventos.indexOfFirst { it.id == feria.id }] = feria.copy(disponibles = 142)

        // --- Evento con asientos numerados (teatro) ---
        val concierto = nuevoEvento(
            oid, "Concierto: Orquesta Universitaria",
            "Gala de temporada con obras de Márquez y Revueltas. Una noche de música mexicana para toda la comunidad.",
            "Teatro Ignacio de la Llave", "Av. Oriente 6 esq. Sur 5, Centro",
            "Música", f(10, 3, 19, 0), esDePago = true, precio = 120.0, aforo = 250, semilla = 1,
            latitud = 18.85118, longitud = -97.10102,   // Teatro Ignacio de la Llave, centro de Orizaba (aprox.)
            fin = f(10, 3, 21, 30),
        )
        tiposBoleto += TipoBoleto(siguienteTipo++, concierto.id, "Planta baja", 120.0, 180, 180)
        tiposBoleto += TipoBoleto(siguienteTipo++, concierto.id, "Balcón", 220.0, 70, 70)
        activarAsientos(
            concierto.id,
            listOf(LayoutSeccion("Planta baja", filas = 10, asientosPorFila = 18),
                   LayoutSeccion("Balcón", filas = 5, asientosPorFila = 14))
        )
        ocuparAsientosDeMuestra(concierto.id, 0.34)

        val hackathon = nuevoEvento(
            oid, "Hackathon UV 2026",
            "24 horas construyendo soluciones tecnológicas. Premios para los tres primeros lugares y comida incluida.",
            "Centro de Cómputo UV", "Campus Orizaba, UV",
            "Tecnología", f(10, 9, 17, 0), esDePago = false, precio = 0.0, aforo = 120, semilla = 2,
            latitud = 18.84890, longitud = -97.10520,   // Centro de Cómputo, campus Orizaba (aprox.)
            fin = f(10, 10, 17, 0),
        )
        eventos[eventos.indexOfFirst { it.id == hackathon.id }] = hackathon.copy(disponibles = 37)

        // --- Segundo evento con asientos ---
        val obra = nuevoEvento(
            oid, "Obra: La Casa de Bernarda Alba",
            "Puesta en escena del grupo de teatro universitario. Función con causa: lo recaudado apoya al taller de artes.",
            "Auditorio Principal", "Campus Orizaba, UV",
            "Teatro", f(10, 15, 18, 30), esDePago = true, precio = 60.0, aforo = 128, semilla = 3,
            latitud = 18.84760, longitud = -97.10610,   // Auditorio, campus Orizaba (aprox.)
        )
        tiposBoleto += TipoBoleto(siguienteTipo++, obra.id, "General", 60.0, 128, 128)
        activarAsientos(obra.id, listOf(LayoutSeccion("Luneta", filas = 8, asientosPorFila = 16)))
        ocuparAsientosDeMuestra(obra.id, 0.22)

        val torneo = nuevoEvento(
            oid, "Torneo de Fútbol Rápido Empresarial",
            "Liga relámpago entre empresas locales. Inscribe a tu equipo o asiste a apoyar; habrá venta de comida.",
            "Unidad Deportiva Sur", "Col. Rancho Grande",
            "Deportes", f(10, 18, 9, 0), esDePago = true, precio = 35.0, aforo = 500, semilla = 4,
            latitud = 18.83980, longitud = -97.09310,   // Unidad deportiva al sur de Orizaba (aprox.)
        )
        tiposBoleto += TipoBoleto(siguienteTipo++, torneo.id, "General", 35.0, 400, 400)
        tiposBoleto += TipoBoleto(siguienteTipo++, torneo.id, "Palco", 90.0, 100, 79)
        eventos[eventos.indexOfFirst { it.id == torneo.id }] = torneo.copy(disponibles = 421)
    }

    private fun nuevoUsuario(nombre: String, correo: String, curp: String, roles: Set<Rol>): Usuario {
        val u = Usuario(
            id = siguienteUsuario++, nombre = nombre, correo = correo, curp = curp,
            contrasena = "enzona123", roles = roles,
            estado = EstadoUsuario.ACTIVO, correoVerificado = true
        )
        usuarios += u
        return u
    }

    private fun nuevoEvento(
        organizadorId: Long, nombre: String, descripcion: String, lugar: String, direccion: String,
        categoria: String, fecha: LocalDateTime, esDePago: Boolean, precio: Double, aforo: Int, semilla: Int,
        latitud: Double? = null, longitud: Double? = null, fin: LocalDateTime? = null,
    ): Evento {
        val e = Evento(
            id = siguienteEvento++, organizadorId = organizadorId, nombre = nombre,
            descripcion = descripcion, lugar = lugar, direccion = direccion, categoria = categoria,
            fecha = fecha, fechaFin = fin, esDePago = esDePago, precioDesde = precio,
            aforo = aforo, disponibles = aforo, colorSemilla = semilla,
            latitud = latitud, longitud = longitud,
        )
        eventos += e
        return e
    }

    /** Marca asientos como ocupados para que el mapa de la demo se vea realista. */
    private fun ocuparAsientosDeMuestra(eventoId: Long, proporcion: Double) {
        val delEvento = asientos.filter { it.eventoId == eventoId }
        val cuantos = (delEvento.size * proporcion).toInt()
        delEvento.shuffled(Random(eventoId)).take(cuantos).forEach { asiento ->
            val i = asientos.indexOfFirst { it.id == asiento.id }
            if (i >= 0) asientos[i] = asientos[i].copy(estado = EstadoAsiento.OCUPADO)
        }
        val i = eventos.indexOfFirst { it.id == eventoId }
        if (i >= 0) eventos[i] = eventos[i].copy(disponibles = eventos[i].aforo - cuantos)
    }

    // ==================================================================
    //  Identidad (RF-01 a RF-04)
    // ==================================================================

    fun correoRegistrado(correo: String) = usuarios.any { it.correo.equals(correo, true) }

    fun curpRegistrado(curp: String) = usuarios.any { it.curp.equals(curp, true) }

    fun registrar(nombre: String, correo: String, telefono: String, curp: String, contrasena: String): Pair<Usuario, String> {
        val usuario = Usuario(
            id = siguienteUsuario++, nombre = nombre, correo = correo, telefono = telefono,
            curp = curp.uppercase(), contrasena = contrasena,
            roles = setOf(Rol.ASISTENTE), estado = EstadoUsuario.PENDIENTE
        )
        usuarios += usuario
        val otp = "%06d".format(Random.nextInt(0, 1_000_000))
        otpPendientes[usuario.id] = otp
        return usuario to otp
    }

    fun otpDe(usuarioId: Long): String? = otpPendientes[usuarioId]

    fun verificarOtp(usuarioId: Long, codigo: String): Boolean {
        if (otpPendientes[usuarioId] != codigo) return false
        val i = usuarios.indexOfFirst { it.id == usuarioId }
        if (i >= 0) usuarios[i] = usuarios[i].copy(estado = EstadoUsuario.ACTIVO, correoVerificado = true)
        otpPendientes.remove(usuarioId)
        return true
    }

    fun reenviarOtp(usuarioId: Long): String {
        val otp = "%06d".format(Random.nextInt(0, 1_000_000))
        otpPendientes[usuarioId] = otp
        return otp
    }

    fun autenticar(identificador: String, contrasena: String): Result<Usuario> {
        val usuario = usuarios.firstOrNull {
            it.correo.equals(identificador, true) || it.curp.equals(identificador, true)
        } ?: return Result.failure(IllegalArgumentException("No existe una cuenta con ese correo o CURP."))

        if (usuario.contrasena != contrasena) {
            return Result.failure(IllegalArgumentException("La contraseña es incorrecta."))
        }
        return when (usuario.estado) {
            EstadoUsuario.PENDIENTE -> Result.failure(IllegalStateException("Tu cuenta aún no está verificada."))
            EstadoUsuario.BLOQUEADO -> Result.failure(IllegalStateException("Tu cuenta está bloqueada. Contacta al administrador."))
            EstadoUsuario.ACTIVO -> Result.success(usuario)
        }
    }

    fun actualizarPerfil(usuarioId: Long, nombre: String, telefono: String): Usuario? {
        val i = usuarios.indexOfFirst { it.id == usuarioId }
        if (i < 0) return null
        usuarios[i] = usuarios[i].copy(nombre = nombre, telefono = telefono)
        return usuarios[i]
    }

    // ==================================================================
    //  Eventos (RF-05 a RF-08, RF-13, RF-14)
    // ==================================================================

    fun obtenerEvento(id: Long): Evento? = eventos.find { it.id == id }

    fun eventosPublicados(): List<Evento> = eventos.filter { it.estado == EstadoEvento.PUBLICADO }

    fun eventosDe(organizadorId: Long): List<Evento> = eventos.filter { it.organizadorId == organizadorId }

    fun tiposDe(eventoId: Long): List<TipoBoleto> = tiposBoleto.filter { it.eventoId == eventoId }

    /**
     * Alta de evento (RF-08). Publicar exige coordenadas (RF-19); guardar un
     * borrador no. Las coordenadas van juntas o ninguna, nunca 0/0.
     */
    fun crearEvento(
        organizadorId: Long, nombre: String, descripcion: String, lugar: String, direccion: String,
        categoria: String, fecha: LocalDateTime, esDePago: Boolean, precio: Double, aforo: Int,
        publicar: Boolean, requiereAsiento: Boolean = false,
        filas: Int = 0, asientosPorFila: Int = 0, seccion: String = "General",
        fechaFin: LocalDateTime? = null, latitud: Double? = null, longitud: Double? = null,
        ciudad: String = "Orizaba",
    ): Evento {
        require((latitud == null) == (longitud == null)) { "Latitud y longitud van juntas o ninguna." }
        require(!publicar || latitud != null) { "Publicar un evento exige su ubicación en el mapa." }
        val evento = Evento(
            id = siguienteEvento++, organizadorId = organizadorId, nombre = nombre,
            descripcion = descripcion, lugar = lugar, direccion = direccion, categoria = categoria,
            fecha = fecha, fechaFin = fechaFin, latitud = latitud, longitud = longitud, ciudad = ciudad,
            esDePago = esDePago, precioDesde = if (esDePago) precio else 0.0,
            aforo = aforo, disponibles = aforo,
            requiereAsiento = requiereAsiento && filas > 0 && asientosPorFila > 0,
            estado = if (publicar) EstadoEvento.PUBLICADO else EstadoEvento.BORRADOR,
            colorSemilla = (eventos.size + 1) % 5
        )
        eventos += evento

        if (esDePago) {
            tiposBoleto += TipoBoleto(siguienteTipo++, evento.id, "General", precio, aforo, aforo)
        }
        if (requiereAsiento && filas > 0 && asientosPorFila > 0) {
            activarAsientos(evento.id, listOf(LayoutSeccion(seccion, filas, asientosPorFila)))
        }
        return eventos.first { it.id == evento.id }
    }

    fun modificarEvento(
        eventoId: Long, nombre: String, descripcion: String, lugar: String,
        direccion: String, categoria: String, fecha: LocalDateTime, nuevoAforo: Int,
        fechaFin: LocalDateTime? = null, latitud: Double? = null, longitud: Double? = null,
    ): Result<Evento> {
        if ((latitud == null) != (longitud == null)) {
            return Result.failure(IllegalArgumentException("Latitud y longitud van juntas o ninguna."))
        }
        val i = eventos.indexOfFirst { it.id == eventoId }
        if (i < 0) return Result.failure(IllegalArgumentException("El evento no existe."))
        val actual = eventos[i]

        if (nuevoAforo < actual.vendidos) {
            return Result.failure(
                IllegalArgumentException(
                    "No puedes reducir el aforo a $nuevoAforo: ya hay ${actual.vendidos} boletos entregados."
                )
            )
        }
        // Con asientos numerados el aforo lo define el mapa, no un número suelto
        val aforoFinal = if (actual.requiereAsiento) actual.aforo else nuevoAforo
        if (actual.estado == EstadoEvento.PUBLICADO && latitud == null) {
            return Result.failure(IllegalArgumentException("Un evento publicado necesita su ubicación en el mapa."))
        }
        val actualizado = actual.copy(
            nombre = nombre, descripcion = descripcion, lugar = lugar, direccion = direccion,
            categoria = categoria, fecha = fecha, fechaFin = fechaFin, aforo = aforoFinal,
            disponibles = aforoFinal - actual.vendidos,
            latitud = latitud, longitud = longitud,
        )
        eventos[i] = actualizado
        return Result.success(actualizado)
    }

    /** Publica un borrador (RF-08). Exige ubicación en el mapa (RF-19). */
    fun publicarEvento(eventoId: Long): Result<Evento> {
        val e = obtenerEvento(eventoId) ?: return Result.failure(IllegalArgumentException("El evento no existe."))
        if (!e.tieneCoordenadas) {
            return Result.failure(IllegalStateException("Para publicar, fija la ubicación del evento en el mapa."))
        }
        cambiarEstado(eventoId, EstadoEvento.PUBLICADO)
        return Result.success(eventos.first { it.id == eventoId })
    }

    /** Marca como finalizado un evento que ya ocurrió (no se puede cancelar). */
    fun marcarRealizado(eventoId: Long) = cambiarEstado(eventoId, EstadoEvento.REALIZADO)

    /** Órdenes aprobadas (cobradas) del evento: base de la comisión de cancelación. */
    fun ordenesAprobadasDe(eventoId: Long): List<Orden> =
        ordenes.filter { it.eventoId == eventoId && it.estado == EstadoOrden.PAGADA }

    /**
     * Política de cancelación vigente para un evento (RF-14, v3). Es lo que
     * muestra el diálogo; `cancelarEvento` la vuelve a evaluar.
     */
    fun politicaCancelacionDe(eventoId: Long, administrativa: Boolean = false, ahora: LocalDateTime = FechaEvento.ahora()): PoliticaCancelacion? {
        val evento = obtenerEvento(eventoId) ?: return null
        val vigentes = boletosDeEvento(eventoId).count { it.estado == EstadoBoleto.VALIDO || it.estado == EstadoBoleto.USADO }
        return politicaCancelacion(evento, tiposDe(eventoId), ordenesAprobadasDe(eventoId), vigentes, ahora, administrativa)
    }

    /**
     * Cancela el evento aplicando la política (RF-14, CP-16, CP-CANCEL-*).
     *
     * Operación completa o nada: valida la política y el motivo antes de tocar
     * nada; después anula boletos (→ CANCELADO), libera asientos, pasa órdenes a
     * REEMBOLSADA y pagos a REEMBOLSADO, registra la cancelación en el evento,
     * el cargo de comisión al organizador (si aplica) y un aviso por asistente.
     *
     * @param ejecutadoPor usuario que cancela (organizador o administrador).
     * @param administrativa cancelación por moderación (RF-18): motivo obligatorio, sin comisión.
     * @param aceptaComision el organizador aceptó la comisión mostrada en el diálogo.
     */
    fun cancelarEvento(
        eventoId: Long,
        ejecutadoPor: Long,
        motivo: String? = null,
        aceptaComision: Boolean = false,
        administrativa: Boolean = false,
        ahora: LocalDateTime = FechaEvento.ahora(),
    ): Result<ResultadoCancelacion> {
        val i = eventos.indexOfFirst { it.id == eventoId }
        if (i < 0) return Result.failure(IllegalArgumentException("El evento no existe."))
        val evento = eventos[i]
        val politica = politicaCancelacionDe(eventoId, administrativa, ahora)!!

        if (!politica.permitida) {
            return Result.failure(IllegalStateException(politica.motivoBloqueo ?: "No se puede cancelar el evento."))
        }
        if (administrativa && motivo.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("La cancelación administrativa requiere un motivo."))
        }
        if (politica.cobraComision && !aceptaComision) {
            return Result.failure(
                IllegalStateException("Para cancelar un evento de pago con ventas debes aceptar la comisión de cancelación del $PORCENTAJE_COMISION_CANCELACION %.")
            )
        }
        if (!administrativa && evento.organizadorId != ejecutadoPor) {
            return Result.failure(IllegalStateException("Solo el organizador del evento o un administrador pueden cancelarlo."))
        }

        // ---------- aplicar (todo o nada: a partir de aquí no hay más validaciones) ----------
        val ordenesEvento = ordenes.filter { it.eventoId == eventoId }.map { it.id }.toSet()
        var anulados = 0
        boletos.indices.forEach { k ->
            val b = boletos[k]
            if (b.ordenId in ordenesEvento && (b.estado == EstadoBoleto.VALIDO || b.estado == EstadoBoleto.USADO)) {
                boletos[k] = b.copy(estado = EstadoBoleto.CANCELADO)
                b.asientoId?.let { liberarAsiento(it) }
                anulados++
            }
        }
        val asistentes = mutableSetOf<Long>()
        ordenes.indices.forEach { k ->
            val o = ordenes[k]
            if (o.eventoId == eventoId && o.estado == EstadoOrden.PAGADA) {
                ordenes[k] = o.copy(estado = if (o.total > 0) EstadoOrden.REEMBOLSADA else EstadoOrden.CANCELADA)
                asistentes += o.asistenteId
            }
        }
        pagos.indices.forEach { k ->
            if (pagos[k].ordenId in ordenesEvento && pagos[k].estado == EstadoPago.APROBADO) {
                // El reembolso se registra contra el pago y su referencia de pasarela; nunca datos de tarjeta
                pagos[k] = pagos[k].copy(estado = EstadoPago.REEMBOLSADO)
            }
        }
        val comision = politica.comision.toDouble()
        eventos[i] = evento.copy(
            estado = EstadoEvento.CANCELADO,
            fechaCancelacion = ahora,
            canceladoPor = ejecutadoPor,
            motivoCancelacion = motivo?.trim()?.ifBlank { null },
            comisionCancelacion = comision,
        )
        if (comision > 0) {
            cargosOrganizador += CargoOrganizador(
                id = siguienteCargo++, organizadorId = evento.organizadorId, eventoId = eventoId,
                concepto = "Comisión de cancelación ($PORCENTAJE_COMISION_CANCELACION %) · ${evento.nombre}",
                importe = comision, fecha = ahora,
            )
        }
        asistentes.forEach { usuarioId ->
            notificaciones += NotificacionAsistente(
                id = siguienteNotificacion++, usuarioId = usuarioId, eventoId = eventoId, fecha = ahora,
                texto = if (politica.reembolso.signum() > 0)
                    "El evento «${evento.nombre}» fue cancelado. Recibirás el reembolso completo de tu compra."
                else "El evento «${evento.nombre}» fue cancelado. Tu confirmación quedó anulada.",
            )
        }
        return Result.success(
            ResultadoCancelacion(
                eventoId = eventoId, boletosAnulados = anulados,
                importeReembolsado = politica.reembolso, comision = politica.comision,
                asistentesNotificados = asistentes.size,
            )
        )
    }

    private fun cambiarEstado(eventoId: Long, estado: EstadoEvento) {
        val i = eventos.indexOfFirst { it.id == eventoId }
        if (i >= 0) eventos[i] = eventos[i].copy(estado = estado)
    }

    // ==================================================================
    //  Asientos (RF-09)
    // ==================================================================

    fun asientosDe(eventoId: Long): List<Asiento> = asientos.filter { it.eventoId == eventoId }

    fun obtenerAsiento(id: Long): Asiento? = asientos.find { it.id == id }

    fun asientosLibres(eventoId: Long): Int = asientos.count { it.eventoId == eventoId && it.libre }

    /** ¿Se puede cambiar el esquema de asientos? Solo mientras no haya boletos. */
    fun puedeCambiarAsientos(eventoId: Long): Boolean = boletosDeEvento(eventoId).isEmpty()

    /**
     * Enciende el interruptor «requiere asientos»: genera el mapa y ajusta el
     * aforo del evento al número de butacas creadas.
     */
    fun activarAsientos(eventoId: Long, secciones: List<LayoutSeccion>): Int {
        val i = eventos.indexOfFirst { it.id == eventoId }
        if (i < 0) return 0
        asientos.removeAll { it.eventoId == eventoId }

        var total = 0
        secciones.forEach { seccion ->
            repeat(seccion.filas) { fila ->
                val letra = ('A' + fila).toString()
                repeat(seccion.asientosPorFila) { indice ->
                    asientos += Asiento(
                        id = siguienteAsiento++, eventoId = eventoId, seccion = seccion.nombre,
                        fila = letra, numero = indice + 1
                    )
                    total++
                }
            }
        }
        eventos[i] = eventos[i].copy(requiereAsiento = true, aforo = total, disponibles = total)
        return total
    }

    /** Apaga el interruptor: elimina el mapa y deja el evento con aforo simple. */
    fun desactivarAsientos(eventoId: Long) {
        val i = eventos.indexOfFirst { it.id == eventoId }
        if (i < 0) return
        asientos.removeAll { it.eventoId == eventoId }
        eventos[i] = eventos[i].copy(requiereAsiento = false)
    }

    private fun ocuparAsiento(asientoId: Long) {
        val i = asientos.indexOfFirst { it.id == asientoId }
        if (i >= 0) asientos[i] = asientos[i].copy(estado = EstadoAsiento.OCUPADO)
    }

    private fun liberarAsiento(asientoId: Long) {
        val i = asientos.indexOfFirst { it.id == asientoId }
        if (i >= 0) asientos[i] = asientos[i].copy(estado = EstadoAsiento.DISPONIBLE)
    }

    // ==================================================================
    //  Órdenes, pagos y boletos (RF-10 a RF-12, RF-16)
    // ==================================================================

    fun detalleDe(boleto: Boleto): BoletoDetalle? {
        val orden = ordenes.find { it.id == boleto.ordenId } ?: return null
        val evento = eventos.find { it.id == orden.eventoId } ?: return null
        return BoletoDetalle(
            boleto = boleto,
            orden = orden,
            evento = evento,
            tipoBoleto = boleto.tipoBoletoId?.let { id -> tiposBoleto.find { it.id == id } },
            asiento = boleto.asientoId?.let { id -> asientos.find { it.id == id } },
        )
    }

    fun detalleDeBoleto(boletoId: Long): BoletoDetalle? =
        boletos.find { it.id == boletoId }?.let { detalleDe(it) }

    /** Boletos de un asistente, resolviendo la relación boleto → orden → usuario. */
    fun boletosDe(usuarioId: Long): List<BoletoDetalle> {
        val misOrdenes = ordenes.filter { it.asistenteId == usuarioId }.map { it.id }.toSet()
        return boletos.filter { it.ordenId in misOrdenes }.mapNotNull { detalleDe(it) }
    }

    fun boletosDeEvento(eventoId: Long): List<BoletoDetalle> {
        val delEvento = ordenes.filter { it.eventoId == eventoId }.map { it.id }.toSet()
        return boletos.filter { it.ordenId in delEvento }.mapNotNull { detalleDe(it) }
    }

    fun yaTieneBoleto(usuarioId: Long, eventoId: Long): Boolean =
        boletosDe(usuarioId).any { it.evento.id == eventoId && it.estado != EstadoBoleto.CANCELADO }

    /** Boletos de una orden, en el orden en que se emitieron ("Boleto k de N"). */
    fun boletosDeOrden(ordenId: Long): List<BoletoDetalle> =
        boletos.filter { it.ordenId == ordenId }.mapNotNull { detalleDe(it) }

    /** Operaciones ya aplicadas, por clave de idempotencia (ver `comprar`). */
    private val operacionesAplicadas = mutableMapOf<String, CompraRealizada>()

    /**
     * Cupo máximo que un asistente puede pedir ahora mismo para un tipo: el
     * mínimo entre el cupo del evento, el stock del tipo y los asientos libres.
     * La UI lo usa para limitar el selector de cantidad; `comprar` lo vuelve a
     * validar antes de emitir.
     */
    fun cupoDisponible(eventoId: Long, tipoBoletoId: Long?): Int {
        val evento = obtenerEvento(eventoId) ?: return 0
        val tipo = tipoBoletoId?.let { id -> tiposBoleto.find { it.id == id } }
        val porAsientos = if (evento.requiereAsiento) asientosLibres(eventoId) else Int.MAX_VALUE
        return minOf(evento.disponibles, tipo?.cantidadDisponible ?: Int.MAX_VALUE, porAsientos).coerceAtLeast(0)
    }

    /**
     * Compra de N boletos (RF-11, RF-16) o confirmación gratuita (RF-10).
     *
     * Valida TODO antes de tocar el inventario y luego aplica la operación
     * completa: una orden, un pago (si hay cobro) y N boletos con código y
     * firma propios; descuenta N del cupo y del stock del tipo y ocupa los N
     * asientos. Si algo falla no se emite ningún boleto ni se descuenta nada
     * (todo o nada). Un pago rechazado deja la orden CANCELADA y el pago
     * RECHAZADO como registro, sin boletos ni descuentos.
     *
     * Idempotencia (demo): si `claveOperacion` ya se aplicó con éxito se
     * devuelve el mismo resultado sin volver a emitir. En el backend esto debe
     * resolverse en servidor con la misma clave (RNF-05); aquí solo evita el
     * doble toque y la recomposición en el teléfono.
     *
     * Política de usuario conservada: en eventos gratuitos, una confirmación
     * por persona. En eventos de pago se permite comprar varias veces mientras
     * haya cupo (no hay política que lo impida).
     *
     * Contrato pendiente: el esquema no asocia `tipo_boleto` con secciones de
     * `asiento`; aquí solo se valida que el asiento pertenezca al evento y esté
     * libre. No se deduce compatibilidad por el nombre de la sección.
     */
    fun comprar(
        usuarioId: Long,
        solicitud: SolicitudCompra,
        metodoPago: MetodoPago? = null,
        pagoAprobado: Boolean = true,
    ): Result<CompraRealizada> {
        if (solicitud.claveOperacion.isNotBlank()) {
            operacionesAplicadas[solicitud.claveOperacion]?.let { return Result.success(it) }
        }

        val i = eventos.indexOfFirst { it.id == solicitud.eventoId }
        if (i < 0) return Result.failure(ErrorCompra.EventoNoDisponible("El evento no existe."))
        val evento = eventos[i]
        val cantidad = solicitud.cantidad
        val tipo = solicitud.tipoBoletoId?.let { id -> tiposBoleto.find { it.id == id } }

        // ---------- validaciones (sin efectos) ----------
        if (evento.estado == EstadoEvento.CANCELADO) {
            return Result.failure(ErrorCompra.EventoNoDisponible("El evento fue cancelado."))
        }
        if (evento.estado != EstadoEvento.PUBLICADO) {
            return Result.failure(ErrorCompra.EventoNoDisponible("El evento no admite compras en este momento."))
        }
        if (cantidad <= 0) {
            return Result.failure(ErrorCompra.CantidadInvalida("Elige al menos un boleto."))
        }
        if (evento.esDePago && tipo == null) {
            return Result.failure(ErrorCompra.CantidadInvalida("Elige un tipo de boleto."))
        }
        if (tipo != null && tipo.eventoId != evento.id) {
            return Result.failure(ErrorCompra.CantidadInvalida("El tipo de boleto no pertenece a este evento."))
        }
        if (!evento.esDePago && cantidad != 1) {
            // Política vigente de confirmación gratuita: un lugar por persona
            return Result.failure(ErrorCompra.PoliticaUsuario("En eventos gratuitos se confirma un lugar por persona."))
        }
        if (!evento.esDePago && yaTieneBoleto(usuarioId, evento.id)) {
            return Result.failure(ErrorCompra.PoliticaUsuario("Ya confirmaste tu asistencia a este evento."))
        }
        if (evento.disponibles < cantidad) {
            return Result.failure(
                ErrorCompra.SinCupo(
                    evento.disponibles,
                    if (evento.disponibles <= 0) "Aforo agotado: ya no quedan lugares disponibles."
                    else "Solo quedan ${evento.disponibles} lugares y pediste $cantidad."
                )
            )
        }
        if (tipo != null && tipo.cantidadDisponible < cantidad) {
            return Result.failure(
                ErrorCompra.SinCupo(
                    tipo.cantidadDisponible,
                    if (tipo.cantidadDisponible <= 0) "Ya no hay boletos de tipo ${tipo.nombre}."
                    else "Solo quedan ${tipo.cantidadDisponible} boletos de tipo ${tipo.nombre} y pediste $cantidad."
                )
            )
        }

        // ---------- asientos ----------
        val asientosElegidos: List<Asiento>
        if (evento.requiereAsiento) {
            val ids = solicitud.asientosIds
            if (ids.size != ids.distinct().size) {
                return Result.failure(ErrorCompra.AsientosInvalidos("Hay asientos repetidos en la selección."))
            }
            if (ids.size != cantidad) {
                val faltan = cantidad - ids.size
                return Result.failure(
                    ErrorCompra.AsientosInvalidos(
                        if (faltan > 0) "Faltan $faltan asientos por elegir (${ids.size} de $cantidad)."
                        else "Elegiste ${ids.size} asientos para $cantidad boletos. Quita ${-faltan}."
                    )
                )
            }
            asientosElegidos = ids.map { id ->
                asientos.find { it.id == id }
                    ?: return Result.failure(ErrorCompra.AsientosInvalidos("El asiento $id no existe."))
            }
            asientosElegidos.firstOrNull { it.eventoId != evento.id }?.let {
                return Result.failure(ErrorCompra.AsientosInvalidos("El asiento ${it.etiquetaCorta} no pertenece a este evento."))
            }
            val ocupados = asientosElegidos.filter { !it.libre }
            if (ocupados.isNotEmpty()) {
                val etiquetas = ocupados.map { it.etiquetaLarga }
                return Result.failure(
                    ErrorCompra.AsientosOcupados(
                        etiquetas,
                        if (ocupados.size == 1) "El asiento ${ocupados[0].etiquetaCorta} acaba de ocuparse. Elige otro."
                        else "Estos asientos acaban de ocuparse: ${ocupados.joinToString { it.etiquetaCorta }}. Elige otros."
                    )
                )
            }
        } else {
            if (solicitud.asientosIds.isNotEmpty()) {
                return Result.failure(ErrorCompra.AsientosInvalidos("Este evento no tiene asientos numerados."))
            }
            asientosElegidos = emptyList()
        }

        // ---------- orden y pago ----------
        val ahora = ahoraTexto()
        val precioUnitario = tipo?.precio ?: 0.0
        // Precio confirmado en el momento de la orden; no cambia si después se edita el tipo
        val total = redondearMxn(precioUnitario * cantidad)

        val orden = Orden(
            id = siguienteOrden++, asistenteId = usuarioId, eventoId = evento.id,
            total = total, estado = EstadoOrden.PENDIENTE, fechaCreacion = ahora
        )
        ordenes += orden

        var pago: Pago? = null
        if (total > 0) {
            val referencia = "SIM-" + Random.nextInt(100000, 999999)
            pago = Pago(
                id = siguientePago++, ordenId = orden.id, monto = total, moneda = "MXN",
                estado = if (pagoAprobado) EstadoPago.APROBADO else EstadoPago.RECHAZADO,
                referenciaPasarela = metodoPago?.tokenPasarela ?: referencia, fecha = ahora
            )
            pagos += pago
            if (!pagoAprobado) {
                // CP-10: la orden se cancela; no se emite boleto ni se descuenta nada
                val io = ordenes.indexOfFirst { it.id == orden.id }
                if (io >= 0) ordenes[io] = ordenes[io].copy(estado = EstadoOrden.CANCELADA)
                return Result.failure(
                    ErrorCompra.PagoRechazado("La pasarela rechazó el pago. No se realizó ningún cargo y tus lugares siguen disponibles.")
                )
            }
        }
        val ip = ordenes.indexOfFirst { it.id == orden.id }
        if (ip >= 0) ordenes[ip] = ordenes[ip].copy(estado = EstadoOrden.PAGADA)

        // ---------- inventario (N) ----------
        eventos[i] = evento.copy(disponibles = evento.disponibles - cantidad)
        if (tipo != null) {
            val t = tiposBoleto.indexOfFirst { it.id == tipo.id }
            if (t >= 0) {
                tiposBoleto[t] = tiposBoleto[t].copy(cantidadDisponible = tiposBoleto[t].cantidadDisponible - cantidad)
            }
        }
        asientosElegidos.forEach { ocuparAsiento(it.id) }

        // ---------- N boletos, cada uno con código y firma propios ----------
        val emitidos = (0 until cantidad).map { k ->
            val codigo = generarCodigo()
            val boleto = Boleto(
                id = siguienteBoleto++, ordenId = orden.id, tipoBoletoId = tipo?.id,
                asientoId = asientosElegidos.getOrNull(k)?.id,
                codigo = codigo, qrFirma = firmarHmac(codigo), fechaEmision = ahora
            )
            boletos += boleto
            detalleDe(boleto)!!
        }

        val resultado = CompraRealizada(orden = ordenes[ip], pago = pago, boletos = emitidos)
        if (solicitud.claveOperacion.isNotBlank()) operacionesAplicadas[solicitud.claveOperacion] = resultado
        return Result.success(resultado)
    }

    /**
     * Compatibilidad: compra o confirmación de UN boleto. Delega en `comprar`
     * con cantidad 1. Se conserva para no romper llamadas existentes.
     */
    fun comprarOConfirmar(
        usuarioId: Long,
        eventoId: Long,
        tipo: TipoBoleto?,
        asientoId: Long? = null,
        metodoPago: MetodoPago? = null,
        pagoAprobado: Boolean = true,
    ): Result<BoletoDetalle> = comprar(
        usuarioId = usuarioId,
        solicitud = SolicitudCompra(
            eventoId = eventoId, tipoBoletoId = tipo?.id, cantidad = 1,
            asientosIds = listOfNotNull(asientoId),
        ),
        metodoPago = metodoPago,
        pagoAprobado = pagoAprobado,
    ).map { it.boletos.first() }

    private fun redondearMxn(importe: Double): Double =
        java.math.BigDecimal(importe).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    // ==================================================================
    //  Métodos de pago guardados
    // ==================================================================

    fun metodosDe(usuarioId: Long): List<MetodoPago> = metodosPago.filter { it.usuarioId == usuarioId }

    fun metodoPredeterminado(usuarioId: Long): MetodoPago? =
        metodosDe(usuarioId).firstOrNull { it.predeterminado } ?: metodosDe(usuarioId).firstOrNull()

    /**
     * Registra una tarjeta.
     *
     * El número completo se usa únicamente para deducir la marca y los últimos
     * cuatro dígitos, y para simular la tokenización; NO se almacena. En el
     * sistema real esta operación la hace el SDK de la pasarela en el cliente y
     * el backend solo recibe el token resultante.
     */
    fun agregarTarjeta(usuarioId: Long, numero: String, vencimiento: String, nombre: String): Result<MetodoPago> {
        val digitos = numero.filter { it.isDigit() }
        if (digitos.length !in 15..16) {
            return Result.failure(IllegalArgumentException("El número de tarjeta debe tener 15 o 16 dígitos."))
        }
        if (!Regex("^(0[1-9]|1[0-2])/\\d{2}$").matches(vencimiento)) {
            return Result.failure(IllegalArgumentException("La fecha de vencimiento debe tener el formato MM/AA."))
        }
        if (nombre.isBlank()) {
            return Result.failure(IllegalArgumentException("Escribe el nombre como aparece en la tarjeta."))
        }
        val marca = when (digitos.first()) {
            '4' -> "Visa"
            '5' -> "Mastercard"
            '3' -> "American Express"
            else -> "Tarjeta"
        }
        val metodo = MetodoPago(
            id = (metodosPago.maxOfOrNull { it.id } ?: 0L) + 1,
            usuarioId = usuarioId,
            tipo = TipoMetodoPago.TARJETA,
            marca = marca,
            ultimos4 = digitos.takeLast(4),
            vencimiento = vencimiento,
            tokenPasarela = "tok_" + Random.nextInt(100000, 999999),
            predeterminado = metodosDe(usuarioId).isEmpty(),
        )
        metodosPago += metodo
        return Result.success(metodo)
    }

    fun eliminarMetodo(metodoId: Long) {
        val era = metodosPago.find { it.id == metodoId } ?: return
        metodosPago.removeAll { it.id == metodoId }
        // Si se borró el predeterminado, el primero que quede toma su lugar
        if (era.predeterminado) {
            val i = metodosPago.indexOfFirst { it.usuarioId == era.usuarioId }
            if (i >= 0) metodosPago[i] = metodosPago[i].copy(predeterminado = true)
        }
    }

    fun marcarPredeterminado(metodoId: Long) {
        val metodo = metodosPago.find { it.id == metodoId } ?: return
        metodosPago.indices.forEach { i ->
            if (metodosPago[i].usuarioId == metodo.usuarioId) {
                metodosPago[i] = metodosPago[i].copy(predeterminado = metodosPago[i].id == metodoId)
            }
        }
    }

    // ==================================================================
    //  Seguridad de la cuenta
    // ==================================================================

    fun cambiarContrasena(usuarioId: Long, actual: String, nueva: String): Result<Unit> {
        val i = usuarios.indexOfFirst { it.id == usuarioId }
        if (i < 0) return Result.failure(IllegalArgumentException("La cuenta no existe."))
        if (usuarios[i].contrasena != actual) {
            return Result.failure(IllegalArgumentException("La contraseña actual no es correcta."))
        }
        if (!com.uv.enzona.util.Validaciones.contrasenaValida(nueva)) {
            return Result.failure(IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres, con letras y números."))
        }
        usuarios[i] = usuarios[i].copy(contrasena = nueva)
        return Result.success(Unit)
    }

    // ==================================================================
    //  Validación de acceso (RF-15, RNF-02, RNF-08)
    // ==================================================================

    fun validarQr(contenido: String, validadorId: Long): ResultadoValidacion {
        val partes = contenido.trim().split(".")
        if (partes.size != 2) {
            return registrar(contenido, "—", validadorId, false,
                "Código no válido", "El contenido escaneado no tiene el formato de un boleto EnZona.")
        }
        val (codigo, firma) = partes

        if (firmarHmac(codigo) != firma) {
            return registrar(codigo, "—", validadorId, false,
                "Boleto falsificado", "La firma digital no coincide. Este boleto no fue emitido por EnZona.")
        }

        val idx = boletos.indexOfFirst { it.codigo == codigo }
        if (idx < 0) {
            return registrar(codigo, "—", validadorId, false,
                "Boleto no encontrado", "El código no corresponde a ningún boleto emitido.")
        }
        val detalle = detalleDe(boletos[idx])
        val nombreEvento = detalle?.evento?.nombre ?: "—"
        val asiento = detalle?.asiento?.etiquetaLarga

        return when (boletos[idx].estado) {
            EstadoBoleto.USADO -> registrar(codigo, nombreEvento, validadorId, false,
                "Boleto ya utilizado",
                "Se registró en el acceso el ${boletos[idx].fechaUso ?: "—"}.", asiento)

            EstadoBoleto.CANCELADO -> if (detalle?.evento?.cancelado == true) {
                registrar(codigo, nombreEvento, validadorId, false,
                    "Evento cancelado", "El organizador canceló «$nombreEvento». Este boleto ya no da acceso.", asiento)
            } else {
                registrar(codigo, nombreEvento, validadorId, false,
                    "Boleto cancelado", "El boleto se anuló.", asiento)
            }

            EstadoBoleto.EXPIRADO -> registrar(codigo, nombreEvento, validadorId, false,
                "Boleto expirado", "La vigencia de este boleto ya terminó.", asiento)

            EstadoBoleto.VALIDO -> {
                boletos[idx] = boletos[idx].copy(estado = EstadoBoleto.USADO, fechaUso = ahoraTexto())
                registrar(codigo, nombreEvento, validadorId, true,
                    "Acceso permitido", "$nombreEvento · ${detalle?.nombreTipo ?: ""}", asiento)
            }
        }
    }

    private fun registrar(
        codigo: String, eventoNombre: String, validadorId: Long,
        permitido: Boolean, titulo: String, detalle: String, asiento: String? = null,
    ): ResultadoValidacion {
        val offline = modoOffline.value
        validaciones += ValidacionAcceso(
            id = siguienteValidacion++,
            codigoBoleto = codigo,
            eventoNombre = eventoNombre,
            validadorId = validadorId,
            fechaHora = ahoraTexto(),
            resultado = if (permitido) ResultadoAcceso.PERMITIDO else ResultadoAcceso.RECHAZADO,
            motivo = titulo,
            offline = offline,
            sincronizado = !offline,
        )
        return ResultadoValidacion(permitido, titulo, detalle, codigo, asiento)
    }

    fun pendientesDeSincronizar(): Int = validaciones.count { !it.sincronizado }

    fun sincronizarValidaciones(): Int {
        var n = 0
        validaciones.indices.forEach { i ->
            if (!validaciones[i].sincronizado) {
                validaciones[i] = validaciones[i].copy(sincronizado = true)
                n++
            }
        }
        return n
    }

    // ==================================================================
    //  Administración (RF-18)
    // ==================================================================

    fun cambiarEstadoUsuario(usuarioId: Long, estado: EstadoUsuario) {
        val i = usuarios.indexOfFirst { it.id == usuarioId }
        if (i >= 0) usuarios[i] = usuarios[i].copy(estado = estado)
    }

    /**
     * Alta autogestionada como organizador.
     *
     * El documento de visión (sección 6) establece que «un mismo usuario puede
     * tener a la vez el rol de asistente y de organizador», así que cualquier
     * cuenta verificada puede publicar sus propios eventos sin pasar por el
     * administrador. Se agrega la fila correspondiente en `usuario_rol`.
     */
    fun activarRolOrganizador(usuarioId: Long): Usuario? {
        asignarRol(usuarioId, Rol.ORGANIZADOR, true)
        return usuarios.find { it.id == usuarioId }
    }

    /**
     * ¿Puede el usuario dejar de ser organizador? Devuelve el motivo que lo
     * impide o null si procede. Reglas (v3):
     *  - no si es administrador (lo gestiona RF-18);
     *  - no si organizador es su único rol;
     *  - no si tiene eventos publicados con boletos vigentes.
     */
    fun motivoNoPuedeDejarOrganizador(usuarioId: Long): String? {
        val u = usuarios.find { it.id == usuarioId } ?: return "La cuenta no existe."
        if (Rol.ORGANIZADOR !in u.roles) return "Esta cuenta no es organizadora."
        if (Rol.ADMIN in u.roles) return "Las cuentas de administración cambian sus roles desde Administración."
        if (u.roles == setOf(Rol.ORGANIZADOR)) return "Organizador es el único rol de esta cuenta."
        val conBoletos = eventosDe(usuarioId).filter { e ->
            e.estado == EstadoEvento.PUBLICADO && boletosDeEvento(e.id).any { it.estado == EstadoBoleto.VALIDO }
        }
        if (conBoletos.isNotEmpty()) {
            return "Tienes ${conBoletos.size} evento(s) publicado(s) con boletos vigentes: " +
                conBoletos.joinToString { "«${it.nombre}»" } + ". Cancélalos o espera a que se realicen."
        }
        return null
    }

    /** Quita el rol ORGANIZADOR (fila de usuario_rol) conservando el de asistente. */
    fun dejarRolOrganizador(usuarioId: Long): Result<Usuario> {
        motivoNoPuedeDejarOrganizador(usuarioId)?.let { return Result.failure(IllegalStateException(it)) }
        asignarRol(usuarioId, Rol.ORGANIZADOR, false)
        asignarRol(usuarioId, Rol.ASISTENTE, true)
        return Result.success(usuarios.first { it.id == usuarioId })
    }

    /**
     * El organizador designa personal de acceso (sección 6 del documento):
     * concede el rol VALIDADOR a una cuenta ya registrada, buscándola por correo.
     */
    fun designarValidador(correo: String): Result<Usuario> {
        val usuario = usuarios.firstOrNull { it.correo.equals(correo.trim(), true) }
            ?: return Result.failure(
                IllegalArgumentException("No hay ninguna cuenta registrada con ese correo. Pídele que se registre primero.")
            )
        if (Rol.VALIDADOR in usuario.roles) {
            return Result.failure(IllegalStateException("${usuario.nombre} ya es personal de acceso."))
        }
        asignarRol(usuario.id, Rol.VALIDADOR, true)
        return Result.success(usuarios.first { it.id == usuario.id })
    }

    fun quitarValidador(usuarioId: Long) = asignarRol(usuarioId, Rol.VALIDADOR, false)

    fun personalDeAcceso(): List<Usuario> = usuarios.filter { Rol.VALIDADOR in it.roles }

    fun asignarRol(usuarioId: Long, rol: Rol, asignar: Boolean) {
        val i = usuarios.indexOfFirst { it.id == usuarioId }
        if (i < 0) return
        val roles = usuarios[i].roles.toMutableSet()
        if (asignar) roles += rol else roles -= rol
        if (roles.isEmpty()) roles += Rol.ASISTENTE
        usuarios[i] = usuarios[i].copy(roles = roles)
    }

    // ==================================================================
    //  Utilidades
    // ==================================================================

    private fun generarCodigo(): String =
        "EZ-" + List(3) { Random.nextInt(1000, 9999) }.joinToString("-")

    /** HMAC-SHA256 truncado, igual que lo haría el backend (RNF-02). */
    private fun firmarHmac(mensaje: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(CLAVE_DEMO.toByteArray(), "HmacSHA256"))
        return mac.doFinal(mensaje.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun ahoraTexto(): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
}
