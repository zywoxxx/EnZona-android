package com.uv.enzona.data.model

import java.time.YearMonth

/**
 * Estadísticas de la plataforma para el panel de administración (RF-18).
 *
 * Se calculan en el teléfono a partir de tres fuentes que la API ya expone:
 * la lista de cuentas (`/api/admin/usuarios`), la lista de eventos
 * (`/api/admin/eventos`) y el reporte de cada evento (`/api/eventos/{id}/reportes`),
 * que es la única fuente de ingresos y accesos validados.
 */

/** Un valor con su etiqueta, para las gráficas de barras. */
data class ParDato(val etiqueta: String, val valor: Double)

/** Reporte de un evento tal como lo entrega la API (o se calcula con los datos de prueba). */
data class ReporteEvento(
    val eventoId: Long,
    val boletosEmitidos: Int,
    val accesosValidados: Int,
    val boletosCancelados: Int,
    val ingresos: Double,
    val reembolsado: Double,
)

data class EstadisticasAdmin(
    // Cuentas
    val usuariosTotal: Int,
    val usuariosActivos: Int,
    val usuariosPendientes: Int,
    val usuariosBloqueados: Int,
    val organizadores: Int,
    val validadores: Int,
    val administradores: Int,
    // Eventos
    val eventosTotal: Int,
    val eventosPublicados: Int,
    val eventosBorrador: Int,
    val eventosRealizados: Int,
    val eventosCancelados: Int,
    val eventosGratuitos: Int,
    val eventosDePago: Int,
    /** De mayor a menor cantidad. */
    val eventosPorCategoria: List<ParDato>,
    /** Eventos por mes de inicio, en orden cronológico. */
    val eventosPorMes: List<ParDato>,
    // Boletaje
    val boletosEmitidos: Int,
    val accesosValidados: Int,
    val boletosCancelados: Int,
    /** Aforo de los eventos publicados o realizados. */
    val aforoTotal: Int,
    /** Lugares vendidos o confirmados en esos eventos. */
    val lugaresOcupados: Int,
    val ingresosTotales: Double,
    val reembolsado: Double,
    /** Eventos con mayores ingresos (hasta 6). */
    val ingresosPorEvento: List<ParDato>,
    /** Porcentaje de ocupación de los eventos publicados (hasta 6, de mayor a menor). */
    val ocupacionPorEvento: List<ParDato>,
) {
    val ocupacionGlobal: Double get() = if (aforoTotal > 0) lugaresOcupados * 100.0 / aforoTotal else 0.0
    val asistencia: Double get() = if (boletosEmitidos > 0) accesosValidados * 100.0 / boletosEmitidos else 0.0
    val ingresosNetos: Double get() = ingresosTotales - reembolsado
}

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Etiqueta de un mes para las gráficas: «oct 26». */
fun etiquetaMes(mes: YearMonth): String = "${MESES_CORTOS[mes.monthValue - 1]} ${mes.year % 100}"

private const val MAXIMO_BARRAS = 6

/**
 * Agrega usuarios, eventos y reportes. Función pura: no toca la red ni el
 * repositorio, para poder probarla con datos fijos.
 */
fun calcularEstadisticas(usuarios: List<Usuario>, eventos: List<Evento>, reportes: List<ReporteEvento>): EstadisticasAdmin {
    val porEvento = reportes.associateBy { it.eventoId }
    val conAforo = eventos.filter { it.estado == EstadoEvento.PUBLICADO || it.estado == EstadoEvento.REALIZADO }

    val porCategoria = eventos.groupingBy { it.categoria.ifBlank { "Sin categoría" } }.eachCount()
        .map { (categoria, n) -> ParDato(categoria, n.toDouble()) }
        .sortedWith(compareByDescending<ParDato> { it.valor }.thenBy { it.etiqueta })

    val porMes = eventos.groupingBy { YearMonth.from(it.fecha) }.eachCount()
        .toSortedMap()
        .map { (mes, n) -> ParDato(etiquetaMes(mes), n.toDouble()) }

    val ingresosPorEvento = eventos
        .mapNotNull { e -> porEvento[e.id]?.let { r -> ParDato(e.nombre, r.ingresos) } }
        .filter { it.valor > 0 }
        .sortedByDescending { it.valor }
        .take(MAXIMO_BARRAS)

    val ocupacionPorEvento = eventos
        .filter { it.estado == EstadoEvento.PUBLICADO && it.aforo > 0 }
        .map { ParDato(it.nombre, it.vendidos * 100.0 / it.aforo) }
        .sortedByDescending { it.valor }
        .take(MAXIMO_BARRAS)

    return EstadisticasAdmin(
        usuariosTotal = usuarios.size,
        usuariosActivos = usuarios.count { it.estado == EstadoUsuario.ACTIVO },
        usuariosPendientes = usuarios.count { it.estado == EstadoUsuario.PENDIENTE },
        usuariosBloqueados = usuarios.count { it.estado == EstadoUsuario.BLOQUEADO },
        organizadores = usuarios.count { Rol.ORGANIZADOR in it.roles },
        validadores = usuarios.count { Rol.VALIDADOR in it.roles },
        administradores = usuarios.count { Rol.ADMIN in it.roles },
        eventosTotal = eventos.size,
        eventosPublicados = eventos.count { it.estado == EstadoEvento.PUBLICADO },
        eventosBorrador = eventos.count { it.estado == EstadoEvento.BORRADOR },
        eventosRealizados = eventos.count { it.estado == EstadoEvento.REALIZADO },
        eventosCancelados = eventos.count { it.estado == EstadoEvento.CANCELADO },
        eventosGratuitos = eventos.count { !it.esDePago },
        eventosDePago = eventos.count { it.esDePago },
        eventosPorCategoria = porCategoria,
        eventosPorMes = porMes,
        boletosEmitidos = reportes.sumOf { it.boletosEmitidos },
        accesosValidados = reportes.sumOf { it.accesosValidados },
        boletosCancelados = reportes.sumOf { it.boletosCancelados },
        aforoTotal = conAforo.sumOf { it.aforo },
        lugaresOcupados = conAforo.sumOf { it.vendidos },
        ingresosTotales = reportes.sumOf { it.ingresos },
        reembolsado = reportes.sumOf { it.reembolsado },
        ingresosPorEvento = ingresosPorEvento,
        ocupacionPorEvento = ocupacionPorEvento,
    )
}
