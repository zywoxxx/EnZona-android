package com.uv.enzona.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/**
 * Regla de negocio de cancelación de eventos (RF-14, v3).
 *
 * Única fuente de verdad para la UI (diálogo) y el repositorio: la pantalla
 * muestra lo que esta función calcula y el repositorio vuelve a evaluarla
 * antes de aplicar la cancelación.
 *
 * Regla:
 *  - Un evento que ya comenzó o terminó (fecha de inicio ≤ ahora, REALIZADO o
 *    ya CANCELADO) no se cancela por ningún rol; solo se marca como finalizado.
 *  - Borrador: se cancela sin comisión.
 *  - Gratuito (ningún tipo de boleto con precio > 0): se cancela, se anulan las
 *    confirmaciones y se avisa a los asistentes. Comisión 0.
 *  - De pago con órdenes aprobadas: comisión del 10 % sobre el importe cobrado
 *    (órdenes PAGADA), redondeo HALF_UP a 2 decimales; los asistentes reciben el
 *    100 % de reembolso y la comisión se carga a la cuenta del organizador.
 *  - De pago sin órdenes aprobadas: procede como gratuito, comisión 0.
 *  - Administrativa (RF-18): reembolso 100 %, motivo obligatorio, sin comisión.
 */
enum class TipoCancelacion {
    BORRADOR,
    GRATUITO,
    DE_PAGO_SIN_VENTAS,
    DE_PAGO_CON_COMISION,
    ADMINISTRATIVA,
}

data class PoliticaCancelacion(
    val permitida: Boolean,
    val tipo: TipoCancelacion?,
    /** Importe total cobrado en órdenes aprobadas del evento (MXN). */
    val importeCobrado: BigDecimal,
    /** Comisión de cancelación a cargo del organizador (MXN). */
    val comision: BigDecimal,
    /** Importe que se reembolsa a los asistentes (100 % de lo cobrado). */
    val reembolso: BigDecimal,
    /** Boletos vigentes que quedarán cancelados. */
    val boletosAfectados: Int,
    /** Motivo cuando `permitida` es false. */
    val motivoBloqueo: String?,
) {
    val cobraComision: Boolean get() = comision.signum() > 0
    val requiereMotivo: Boolean get() = tipo == TipoCancelacion.ADMINISTRATIVA
}

/** Resultado de una cancelación aplicada. */
data class ResultadoCancelacion(
    val eventoId: Long,
    val boletosAnulados: Int,
    val importeReembolsado: BigDecimal,
    val comision: BigDecimal,
    val asistentesNotificados: Int,
)

const val PORCENTAJE_COMISION_CANCELACION = 10

/** Escala monetaria MXN: 2 decimales, HALF_UP. */
fun BigDecimal.aMxn(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

/**
 * Evalúa la política de cancelación de un evento. Función pura.
 *
 * @param tipos tipos de boleto del evento (para saber si es de pago).
 * @param ordenesAprobadas órdenes PAGADA del evento (su `total` es lo cobrado).
 * @param boletosVigentes número de boletos VALIDO o USADO que se anularían.
 * @param ahora momento de la evaluación (zona del evento).
 * @param administrativa true cuando cancela un administrador (moderación).
 */
fun politicaCancelacion(
    evento: Evento,
    tipos: List<TipoBoleto>,
    ordenesAprobadas: List<Orden>,
    boletosVigentes: Int,
    ahora: LocalDateTime,
    administrativa: Boolean = false,
): PoliticaCancelacion {
    val cobrado = ordenesAprobadas
        .filter { it.estado == EstadoOrden.PAGADA }
        .fold(BigDecimal.ZERO) { acc, o -> acc + BigDecimal.valueOf(o.total) }
        .aMxn()

    fun bloqueada(motivo: String) = PoliticaCancelacion(
        permitida = false, tipo = null, importeCobrado = cobrado, comision = BigDecimal.ZERO.aMxn(),
        reembolso = BigDecimal.ZERO.aMxn(), boletosAfectados = boletosVigentes, motivoBloqueo = motivo,
    )

    when (evento.estado) {
        EstadoEvento.CANCELADO -> return bloqueada("El evento ya está cancelado.")
        EstadoEvento.REALIZADO -> return bloqueada("El evento ya se realizó; solo puede quedar como finalizado.")
        else -> Unit
    }
    if (!evento.fecha.isAfter(ahora)) {
        return bloqueada("El evento ya comenzó. No se puede cancelar; márcalo como finalizado.")
    }

    val esDePago = evento.esDePago && tipos.any { it.precio > 0.0 }
    val tipo = when {
        administrativa -> TipoCancelacion.ADMINISTRATIVA
        evento.estado == EstadoEvento.BORRADOR -> TipoCancelacion.BORRADOR
        !esDePago -> TipoCancelacion.GRATUITO
        cobrado.signum() == 0 -> TipoCancelacion.DE_PAGO_SIN_VENTAS
        else -> TipoCancelacion.DE_PAGO_CON_COMISION
    }
    val comision = if (tipo == TipoCancelacion.DE_PAGO_CON_COMISION) {
        cobrado.multiply(BigDecimal(PORCENTAJE_COMISION_CANCELACION)).divide(BigDecimal(100)).aMxn()
    } else BigDecimal.ZERO.aMxn()

    return PoliticaCancelacion(
        permitida = true,
        tipo = tipo,
        importeCobrado = cobrado,
        comision = comision,
        reembolso = cobrado,
        boletosAfectados = boletosVigentes,
        motivoBloqueo = null,
    )
}

/** Cargo pendiente en la cuenta del organizador (comisión de cancelación). */
data class CargoOrganizador(
    val id: Long,
    val organizadorId: Long,
    val eventoId: Long,
    val concepto: String,
    val importe: Double,
    val fecha: LocalDateTime,
    val pagado: Boolean = false,
)
