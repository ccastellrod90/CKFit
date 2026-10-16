package com.ckfit.app.data.repo

import androidx.room.withTransaction
import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.db.dao.PlannedSessionWithDay
import com.ckfit.app.data.db.entity.PlannedSessionEntity
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.week.PlanDay
import com.ckfit.app.domain.week.PlannedItem
import com.ckfit.app.domain.week.WeekPlanner
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeekRepository @Inject constructor(
    private val db: CKFitDatabase,
    private val settings: SettingsRepository,
) {
    private val plannedDao get() = db.plannedSessionDao()
    private val routineDao get() = db.routineDao()

    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedSessionWithDay>> =
        plannedDao.observeWeek(weekStart)

    /** Sesión elegida con "Elegir otra sesión" (se ignora si ya no está pendiente). */
    val chosenPlannedId: Flow<Long?> get() = settings.chosenPlannedId

    /**
     * Al abrir la app: cierra las semanas anteriores (obligatorias PENDING → MISSED)
     * y ajusta la cola de la semana actual al plan activo.
     */
    suspend fun ensureCurrentWeek(today: LocalDate) {
        val weekStart = WeekPlanner.weekStartOf(today)
        db.withTransaction {
            val pendingBefore = plannedDao.getPendingBefore(weekStart).map { it.toItem() }
            val missed = WeekPlanner.idsToMarkMissed(pendingBefore)
            if (missed.isNotEmpty()) plannedDao.setStatus(missed, PlannedStatus.MISSED)
            syncCurrentWeek(today)
        }
    }

    /** Tras cambiar el plan activo o sus días, la semana en curso refleja el plan nuevo. */
    suspend fun syncCurrentWeek(today: LocalDate) {
        val weekStart = WeekPlanner.weekStartOf(today)
        db.withTransaction {
            val routine = routineDao.getActive()
            val days = routine?.let { r ->
                routineDao.getDays(r.id).map { PlanDay(it.id, it.dayOfWeek, it.isOptional, it.isRestDay) }
            }.orEmpty()
            val existing = plannedDao.getWeek(weekStart).map { it.toItem() }
            val sync = WeekPlanner.syncWeek(weekStart, existing, days)
            if (sync.deleteIds.isNotEmpty()) plannedDao.delete(sync.deleteIds)
            if (sync.add.isNotEmpty()) {
                plannedDao.insertAll(
                    sync.add.map {
                        PlannedSessionEntity(
                            weekStart = it.weekStart,
                            routineDayId = it.routineDayId,
                            plannedDate = it.plannedDate,
                            status = PlannedStatus.PENDING,
                        )
                    },
                )
            }
        }
    }

    /** "Elegir otra sesión": pasa a ser la sugerida mientras siga pendiente. */
    suspend fun choose(plannedId: Long) = settings.setChosenPlannedId(plannedId)

    /** "Saltar sesión": no se vuelve a sugerir. */
    suspend fun skip(plannedId: Long, reason: String?) {
        val planned = plannedDao.get(plannedId) ?: return
        if (planned.status != PlannedStatus.PENDING) return
        plannedDao.setStatusAndReason(plannedId, PlannedStatus.SKIPPED, reason?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** "Deshacer salto", solo si la semana de esa sesión sigue abierta. */
    suspend fun undoSkip(plannedId: Long, today: LocalDate): Boolean {
        val planned = plannedDao.get(plannedId) ?: return false
        if (!WeekPlanner.canUndoSkip(planned.status, planned.weekStart, today)) return false
        plannedDao.setStatusAndReason(plannedId, PlannedStatus.PENDING, null)
        return true
    }
}

/** Si el día ya no existe se trata como obligatorio y se usa el día de la fecha planificada. */
fun PlannedSessionWithDay.toItem(): PlannedItem = PlannedItem(
    id = planned.id,
    routineDayId = planned.routineDayId,
    dayOfWeek = dayOfWeek ?: planned.plannedDate.dayOfWeek.value,
    isOptional = dayOptional ?: false,
    status = planned.status,
    hasSession = planned.sessionId != null,
)
