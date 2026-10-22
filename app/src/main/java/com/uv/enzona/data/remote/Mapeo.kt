package com.uv.enzona.data.remote

import com.uv.enzona.data.PersonalAcceso
import com.uv.enzona.data.model.Asiento
import com.uv.enzona.data.model.Boleto
import com.uv.enzona.data.model.BoletoDetalle
import com.uv.enzona.data.model.Categoria
import com.uv.enzona.data.model.CompraRealizada
import com.uv.enzona.data.model.EstadoAsiento
import com.uv.enzona.data.model.EstadoBoleto
import com.uv.enzona.data.model.EstadoEvento
import com.uv.enzona.data.model.EstadoOrden
import com.uv.enzona.data.model.EstadoPago
import com.uv.enzona.data.model.EstadoUsuario
import com.uv.enzona.data.model.Evento
import com.uv.enzona.data.model.Orden
import com.uv.enzona.data.model.Pago
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.data.model.ReporteEvento
import com.uv.enzona.data.model.ResultadoCancelacion
import com.uv.enzona.data.model.ResultadoValidacion
import com.uv.enzona.data.model.Rol
import com.uv.enzona.data.model.TipoBoleto
import com.uv.enzona.data.model.TipoCancelacion
import com.uv.enzona.data.model.Usuario
import com.uv.enzona.data.model.aMxn
import com.uv.enzona.util.FechaEvento
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Conversión DTO de la API ↔ modelo de dominio de la app.
 *
 * La API habla en ISO-8601 con desfase (hora del centro de México) y en importes
 * de texto exacto; el dominio usa `LocalDateTime` en la zona del evento
 * (America/Mexico_City). Un valor desconocido de un enumerado no rompe la app:
 * cae en un valor seguro.
 */

private val FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

internal fun fecha(iso: String?): LocalDateTime? = iso?.let { FechaEvento.desdeIso(it) }

internal fun fechaTexto(iso: String?): String = fecha(iso)?.format(FECHA_HORA) ?: ""

internal fun ahoraTexto(): String = FechaEvento.ahora().format(FECHA_HORA)

internal fun importe(texto: String?): Double = texto?.toDoubleOrNull() ?: 0.0

internal inline fun <reified T : Enum<T>> enumerado(valor: String?, porDefecto: T): T =
    enumValues<T>().firstOrNull { it.name == valor } ?: porDefecto

/**
 * Sirve para el perfil (`/me`), el resumen de la sesión (id, nombre, correo, roles)
 * y la vista administrativa. La CURP nunca viaja desde el servidor (RNF-03).
 */
fun UsuarioDto.aDominio(): Usuario = Usuario(
    id = id,
    nombre = nombre,
    correo = correo.orEmpty(),
    telefono = telefono.orEmpty(),
    curp = "",                                  // la API no la devuelve ni enmascarada
    contrasenaHash = "",                        // el hash nunca sale del servidor
    roles = roles.orEmpty().mapNotNull { r -> Rol.entries.firstOrNull { it.name == r } }.toSet().ifEmpty { setOf(Rol.ASISTENTE) },
    estado = enumerado(estado, EstadoUsuario.ACTIVO),
    // El resumen de sesión no trae las banderas: una cuenta que pudo iniciar sesión ya verificó su correo
    correoVerificado = correoVerificado ?: true,
    telefonoVerificado = telefonoVerificado ?: false,
    fechaRegistro = fecha(fechaRegistro) ?: FechaEvento.ahora(),
)

fun CategoriaDto.aDominio() = Categoria(id, nombre)

fun EventoDto.aDominio(): Evento = Evento(
    id = id,
    organizadorId = organizadorId ?: 0L,
    nombre = nombre,
    descripcion = descripcion.orEmpty(),
    lugar = lugar,
    direccion = direccion.orEmpty(),
    categoria = categoria.orEmpty(),
    fecha = fecha(fechaHoraInicio) ?: FechaEvento.ahora(),
    fechaFin = fecha(fechaHoraFin),
    esDePago = esDePago,
    precioDesde = importe(precioDesde),
    aforo = aforo,
    disponibles = disponibles,
    requiereAsiento = requiereAsiento,
    estado = enumerado(estado, EstadoEvento.PUBLICADO),
    categoriaId = categoriaId,
    fechaCreacion = FechaEvento.ahora(),         // la API no la expone
    colorSemilla = (id % 5).toInt(),
    ciudad = ciudad ?: "Orizaba",
    latitud = latitud,
    longitud = longitud,
    fechaCancelacion = fecha(fechaCancelacion),
    motivoCancelacion = motivoCancelacion,
    comisionCancelacion = 0.0,                   // la API no cobra comisión al organizador
    distanciaKm = distanciaKm,
    // La API no dice si hay boletos: se deduce del inventario (lugares ya tomados)
    tieneBoletos = disponibles < aforo,
    imagenUrl = imagenUrl?.takeIf { it.isNotBlank() },
)

fun TipoBoletoDto.aDominio(eventoId: Long) =
    TipoBoleto(id, eventoId, nombre, importe(precio), cantidadTotal ?: 0, disponibles ?: 0)

fun AsientoDto.aDominio(eventoId: Long) = Asiento(
    id = id, eventoId = eventoId, seccion = seccion.orEmpty(), fila = fila.orEmpty(),
    numero = numero?.toIntOrNull() ?: 0, estado = enumerado(estado, EstadoAsiento.OCUPADO),
)

private val ETIQUETA_ASIENTO = Regex("""^(.*?) · Fila (.+?) · Asiento (\d+)$""")

/** Reconstruye el asiento desde la etiqueta «Sección · Fila A · Asiento 5» que entrega la API en boletos y manifiesto. */
internal fun asientoDesdeEtiqueta(id: Long?, eventoId: Long, etiqueta: String?): Asiento? {
    val texto = etiqueta?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    val partes = ETIQUETA_ASIENTO.matchEntire(texto)
    return if (partes != null) {
        Asiento(id ?: 0L, eventoId, partes.groupValues[1], partes.groupValues[2], partes.groupValues[3].toInt(), EstadoAsiento.OCUPADO)
    } else {
        Asiento(id ?: 0L, eventoId, texto, "", 0, EstadoAsiento.OCUPADO)
    }
}

/** Evento mínimo con lo que trae el boleto, para cuando el detalle no está disponible (p. ej. un borrador ajeno). */
internal fun BoletoDto.eventoResumen(): Evento = Evento(
    id = eventoId, organizadorId = 0L, nombre = eventoNombre ?: "Evento", descripcion = "", lugar = lugar.orEmpty(),
    direccion = "", categoria = "", fecha = fecha(fechaHoraInicio) ?: FechaEvento.ahora(), esDePago = false,
    precioDesde = 0.0, aforo = 0, disponibles = 0, colorSemilla = (eventoId % 5).toInt(),
)

/**
 * Boleto con sus relaciones resueltas. La API no anida la orden en el boleto: la
 * construye [RepositorioRemoto] con los boletos hermanos y el precio de su tipo.
 */
fun BoletoDto.aDominio(evento: Evento, orden: Orden, precio: Double?): BoletoDetalle = BoletoDetalle(
    boleto = Boleto(
        id = id, ordenId = ordenId, tipoBoletoId = tipoBoletoId, asientoId = asientoId, codigo = codigo,
        qrFirma = firma ?: contenidoQr?.substringAfter(".", "").orEmpty(),
        estado = enumerado(estado, EstadoBoleto.CANCELADO),
        fechaEmision = fechaTexto(fechaEmision), fechaUso = fechaUso?.let { fechaTexto(it) },
    ),
    orden = orden,
    evento = evento,
    tipoBoleto = TipoBoleto(tipoBoletoId ?: 0L, eventoId, tipoNombre ?: "Entrada general", precio ?: 0.0, 0, 0),
    asiento = asientoDesdeEtiqueta(asientoId, eventoId, asientoEtiqueta),
)

/** `precios`: precio por id de tipo de boleto del evento, para valorar cada boleto. */
fun OrdenDto.aDominio(evento: Evento, asistenteId: Long, precios: Map<Long?, Double>): CompraRealizada {
    val importeTotal = importe(total)
    val orden = Orden(
        id = ordenId, asistenteId = asistenteId, eventoId = eventoId, total = importeTotal,
        estado = enumerado(estadoOrden, EstadoOrden.PENDIENTE), fechaCreacion = ahoraTexto(),
    )
    val pago = when {
        estadoPago != null -> Pago(ordenId, ordenId, importeTotal, moneda ?: "MXN", enumerado(estadoPago, EstadoPago.PENDIENTE), referenciaPago, orden.fechaCreacion)
        // La pasarela no respondió: la orden queda PENDIENTE sin estado de pago
        orden.estado == EstadoOrden.PENDIENTE && importeTotal > 0 -> Pago(ordenId, ordenId, importeTotal, moneda ?: "MXN", EstadoPago.PENDIENTE, referenciaPago, orden.fechaCreacion)
        else -> null
    }
    return CompraRealizada(orden, pago, boletos.orEmpty().map { it.aDominio(evento, orden, precios[it.tipoBoletoId]) })
}

fun ValidadorDto.aDominio() = PersonalAcceso(usuarioId, nombre, correo)

private fun decimal(texto: String?): BigDecimal = (texto?.toBigDecimalOrNull() ?: BigDecimal.ZERO).aMxn()

fun PoliticaCancelacionDto.aDominio() = PoliticaCancelacion(
    permitida = permitida,
    tipo = TipoCancelacion.entries.firstOrNull { it.name == tipo },
    importeCobrado = decimal(importeCobrado), comision = decimal(comision), reembolso = decimal(reembolso),
    boletosAfectados = boletosAfectados ?: 0, motivoBloqueo = motivoBloqueo,
)

fun CancelacionResultadoDto.aDominio() = ResultadoCancelacion(
    eventoId = eventoId, boletosAnulados = boletosAnulados ?: 0, importeReembolsado = decimal(importeReembolsado),
    comision = decimal(comision), asistentesNotificados = asistentesNotificados ?: 0,
)

fun ReporteDto.aDominio() = ReporteEvento(
    eventoId = eventoId,
    boletosEmitidos = (boletosEmitidos ?: 0L).toInt(),
    accesosValidados = (accesosValidados ?: 0L).toInt(),
    boletosCancelados = (boletosCancelados ?: 0L).toInt(),
    ingresos = importe(ingresos),
    reembolsado = importe(reembolsado),
)

/** Título corto para la pantalla del validador según el motivo que devuelve la API. */
internal fun tituloValidacion(resultado: String?, motivo: String?, conflicto: Boolean = false): String = when {
    conflicto -> "Boleto ya admitido en otro dispositivo"
    resultado == "PERMITIDO" -> "Acceso permitido"
    else -> when (motivo) {
        "YA_UTILIZADO" -> "Boleto ya utilizado"
        "FIRMA_INVALIDA" -> "Boleto no válido"
        "BOLETO_INEXISTENTE" -> "Boleto no reconocido"
        "FORMATO_INVALIDO" -> "Código no reconocido"
        "OTRO_EVENTO" -> "Boleto de otro evento"
        "EVENTO_CANCELADO" -> "Evento cancelado"
        "CANCELADO" -> "Boleto cancelado"
        "EXPIRADO" -> "Boleto expirado"
        else -> "Acceso rechazado"
    }
}

fun ResultadoValidacionDto.aDominio(): ResultadoValidacion {
    val permitido = resultado == "PERMITIDO"
    val detalle = if (permitido) listOfNotNull(mensaje, tipoBoleto, asiento).joinToString(" · ") else mensaje.orEmpty()
    return ResultadoValidacion(permitido, tituloValidacion(resultado, motivo), detalle, codigo, asiento)
}
