package com.uv.enzona.data

import com.uv.enzona.data.model.Evento
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Filtro por fecha (RF-07). */
enum class FiltroFecha(val etiqueta: String) {
    CUALQUIERA("Cualquier fecha"),
    HOY("Hoy"),
    ESTA_SEMANA("Esta semana"),
    ESTE_MES("Este mes"),
    PROXIMO_MES("Próximo mes"),
}

/** Filtro por tipo de acceso. */
enum class FiltroPrecio(val etiqueta: String) {
    TODOS("Todos"),
    GRATIS("Gratis"),
    DE_PAGO("De pago"),
}

/**
 * Estado de búsqueda y filtros de Inicio (RF-06, RF-07). Es lógica pura para
 * poder probarla sin Compose (caso CP-07: lugar y fecha).
 *
 * `lugar` filtra por la sede del evento (`evento.lugar`). El filtro de fecha
 * compara `Evento.fecha` (LocalDateTime) por fecha real, no por texto (v3).
 */
data class FiltrosEventos(
    val busqueda: String = "",
    val categoria: String? = null,
    val lugar: String? = null,
    val precio: FiltroPrecio = FiltroPrecio.TODOS,
    val fecha: FiltroFecha = FiltroFecha.CUALQUIERA,
) {
    /** Número de filtros activos, sin contar el texto de búsqueda. */
    val activos: Int
        get() = listOf(
            categoria != null,
            lugar != null,
            precio != FiltroPrecio.TODOS,
            fecha != FiltroFecha.CUALQUIERA,
        ).count { it }

    val hayAlgo: Boolean get() = activos > 0 || busqueda.isNotBlank()

    fun limpiar(): FiltrosEventos = FiltrosEventos()

    fun aplicar(eventos: List<Evento>, hoy: LocalDate = LocalDate.now()): List<Evento> =
        eventos.filter { e ->
            coincideTexto(e) && coincideCategoria(e) && coincideLugar(e) &&
                coincidePrecio(e) && coincideFecha(e, hoy)
        }

    private fun coincideTexto(e: Evento): Boolean {
        if (busqueda.isBlank()) return true
        val q = busqueda.trim()
        return e.nombre.contains(q, true) || e.lugar.contains(q, true) ||
            e.direccion.contains(q, true) || e.categoria.contains(q, true)
    }

    private fun coincideCategoria(e: Evento) = categoria == null || e.categoria.equals(categoria, true)

    private fun coincideLugar(e: Evento) = lugar == null || e.lugar.equals(lugar, true)

    private fun coincidePrecio(e: Evento) = when (precio) {
        FiltroPrecio.TODOS -> true
        FiltroPrecio.GRATIS -> !e.esDePago
        FiltroPrecio.DE_PAGO -> e.esDePago
    }

    private fun coincideFecha(e: Evento, hoy: LocalDate): Boolean {
        if (fecha == FiltroFecha.CUALQUIERA) return true
        val f = e.fecha.toLocalDate()
        return when (fecha) {
            FiltroFecha.CUALQUIERA -> true
            FiltroFecha.HOY -> f == hoy
            FiltroFecha.ESTA_SEMANA -> {
                val inicio = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val fin = inicio.plusDays(6)
                !f.isBefore(inicio) && !f.isAfter(fin)
            }
            FiltroFecha.ESTE_MES -> f.year == hoy.year && f.month == hoy.month
            FiltroFecha.PROXIMO_MES -> {
                val siguiente = hoy.plusMonths(1)
                f.year == siguiente.year && f.month == siguiente.month
            }
        }
    }
}
