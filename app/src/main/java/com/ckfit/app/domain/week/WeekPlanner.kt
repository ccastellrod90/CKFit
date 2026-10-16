package com.ckfit.app.domain.week

import com.ckfit.app.domain.model.PlannedStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Día del plan tal como lo necesita la cola semanal. */
data class PlanDay(
    val routineDayId: Long,
    val dayOfWeek: Int,
    val isOptional: Boolean,
    val isRestDay: Boolean,
)

/** Sesión planificada de la semana, con los datos de su día. */
data class PlannedItem(
    val id: Long,
    val routineDayId: Long?,
    val dayOfWeek: Int,
    val isOptional: Boolean,
    val status: PlannedStatus,
    val hasSession: Boolean = false,
)

data class NewPlanned(
    val weekStart: LocalDate,
    val routineDayId: Long,
    val plannedDate: LocalDate,
)

data class WeekSync(
    val deleteIds: List<Long>,
    val add: List<NewPlanned>,
)

/**
 * Semana flexible (sección 6): "Rutina de hoy" es la siguiente sesión pendiente de la semana,
 * no el día del calendario. Kotlin puro para poder probarlo sin Android.
 */
object WeekPlanner {

    /** Lunes de la semana que contiene [date]. */
    fun weekStartOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Una sesión PENDING por cada día que no es de descanso. */
    fun buildWeek(weekStart: LocalDate, days: List<PlanDay>): List<NewPlanned> =
        days.filterNot { it.isRestDay }
            .sortedBy { it.dayOfWeek }
            .map { it.toNewPlanned(weekStart) }

    /**
     * Ajusta la cola de la semana en curso al plan activo (primera apertura, cambio de plan activo
     * o días que pasan a descanso / entrenamiento):
     * - se borran las PENDING sin sesión cuyo día ya no es un día de entrenamiento del plan activo;
     * - se añaden los días de entrenamiento que no tienen fila, salvo que ese día de la semana
     *   ya esté resuelto (hecho, saltado o perdido) con otro plan.
     * Lo ya hecho o saltado nunca se toca.
     */
    fun syncWeek(weekStart: LocalDate, existing: List<PlannedItem>, activeDays: List<PlanDay>): WeekSync {
        val training = activeDays.filterNot { it.isRestDay }
        val trainingIds = training.map { it.routineDayId }.toSet()
        val deleteIds = existing
            .filter { it.status == PlannedStatus.PENDING && !it.hasSession && it.routineDayId !in trainingIds }
            .map { it.id }
        val kept = existing.filterNot { it.id in deleteIds }
        val add = training
            .filter { day ->
                kept.none { it.routineDayId == day.routineDayId } &&
                    kept.none { it.dayOfWeek == day.dayOfWeek && it.status != PlannedStatus.PENDING }
            }
            .sortedBy { it.dayOfWeek }
            .map { it.toNewPlanned(weekStart) }
        return WeekSync(deleteIds, add)
    }

    /**
     * Sugerida: la que está en curso; si no, la elegida con "Elegir otra sesión" (si sigue pendiente);
     * si no, la primera obligatoria PENDING por dayOfWeek; las opcionales solo cuando no quedan obligatorias.
     */
    fun suggest(items: List<PlannedItem>, inProgressPlannedId: Long?, chosenPlannedId: Long? = null): PlannedItem? {
        if (inProgressPlannedId != null) {
            items.firstOrNull { it.id == inProgressPlannedId }?.let { return it }
        }
        val pending = items.filter { it.status == PlannedStatus.PENDING }.sortedBy { it.dayOfWeek }
        if (chosenPlannedId != null) {
            pending.firstOrNull { it.id == chosenPlannedId }?.let { return it }
        }
        return pending.firstOrNull { !it.isOptional } ?: pending.firstOrNull()
    }

    /**
     * Cierre de una semana ya terminada: las obligatorias PENDING pasan a MISSED;
     * las opcionales PENDING se dejan como están (no cuentan como falta).
     */
    fun idsToMarkMissed(items: List<PlannedItem>): List<Long> =
        items.filter { it.status == PlannedStatus.PENDING && !it.isOptional }.map { it.id }

    /** Un salto se puede deshacer mientras su semana siga abierta. */
    fun canUndoSkip(status: PlannedStatus, weekStart: LocalDate, today: LocalDate): Boolean =
        status == PlannedStatus.SKIPPED && weekStart == weekStartOf(today)

    private fun PlanDay.toNewPlanned(weekStart: LocalDate) = NewPlanned(
        weekStart = weekStart,
        routineDayId = routineDayId,
        plannedDate = weekStart.plusDays((dayOfWeek - 1).toLong()),
    )
}
