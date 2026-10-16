package com.ckfit.app.data.repo

import androidx.room.withTransaction
import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.db.dao.SessionExerciseDetail
import com.ckfit.app.data.db.dao.SessionProgress
import com.ckfit.app.data.db.dao.SetCount
import com.ckfit.app.data.db.entity.SessionExerciseEntity
import com.ckfit.app.data.db.entity.SetLogEntity
import com.ckfit.app.data.db.entity.WorkoutSessionEntity
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.SessionStatus
import com.ckfit.app.domain.model.Side
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Serie a guardar (una por lado si es por lado). Peso en kg. */
data class SetToLog(
    val setNumber: Int,
    val side: Side?,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
)

@Singleton
class SessionRepository @Inject constructor(
    private val db: CKFitDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val sessionDao get() = db.workoutSessionDao()
    private val itemDao get() = db.sessionExerciseDao()
    private val setLogDao get() = db.setLogDao()
    private val plannedDao get() = db.plannedSessionDao()

    fun observeInProgress(): Flow<WorkoutSessionEntity?> = sessionDao.observeInProgress()

    suspend fun getInProgress(): WorkoutSessionEntity? = sessionDao.getInProgress()

    fun observeSession(id: Long): Flow<WorkoutSessionEntity?> = sessionDao.observe(id)

    fun observeProgress(sessionId: Long): Flow<SessionProgress> = itemDao.observeProgress(sessionId)

    fun observeExercises(sessionId: Long): Flow<List<SessionExerciseDetail>> = itemDao.observeDetails(sessionId)

    fun observeSetCounts(sessionId: Long): Flow<List<SetCount>> = setLogDao.observeSetCounts(sessionId)

    suspend fun getExercises(sessionId: Long): List<SessionExerciseDetail> = itemDao.getDetails(sessionId)

    suspend fun getExercise(sessionExerciseId: Long): SessionExerciseDetail? = itemDao.getDetail(sessionExerciseId)

    suspend fun lastSetNumber(sessionExerciseId: Long): Int = setLogDao.lastSetNumber(sessionExerciseId) ?: 0

    suspend fun previousWeight(exerciseId: Long, currentSessionId: Long): Double? =
        setLogDao.previousWeight(exerciseId, currentSessionId)

    /**
     * Empieza una sesión con los ejercicios vivos del día, en el orden del plan.
     * [plannedId] null = sesión extra, fuera de la cola semanal. Si ya hay una en curso, devuelve esa.
     */
    suspend fun start(routineDayId: Long, plannedId: Long?): Long = db.withTransaction {
        sessionDao.getInProgress()?.let { return@withTransaction it.id }
        val sessionId = sessionDao.insert(
            WorkoutSessionEntity(
                plannedSessionId = plannedId,
                routineDayId = routineDayId,
                date = LocalDate.now(clock),
                startedAt = clock.instant(),
                status = SessionStatus.IN_PROGRESS,
            ),
        )
        itemDao.insertAll(
            db.dayExerciseDao().getForDay(routineDayId).mapIndexed { index, it ->
                SessionExerciseEntity(
                    sessionId = sessionId,
                    dayExerciseId = it.dayExercise.id,
                    exerciseId = it.exercise.id,
                    position = index,
                    status = SessionExerciseStatus.PENDING,
                )
            },
        )
        // Enlazada desde ya: la cola semanal no la toca mientras está en curso.
        plannedId?.let { plannedDao.setSession(it, sessionId) }
        sessionId
    }.also { settings.setChosenPlannedId(null) }

    /** Guarda una serie cerrada (todas sus filas a la vez) y marca el ejercicio en curso. */
    suspend fun logSet(sessionExerciseId: Long, rows: List<SetToLog>) = db.withTransaction {
        setLogDao.insertAll(
            rows.map {
                SetLogEntity(
                    sessionExerciseId = sessionExerciseId,
                    setNumber = it.setNumber,
                    side = it.side,
                    weightKg = it.weightKg,
                    reps = it.reps,
                    durationSec = it.durationSec,
                    completedAt = clock.instant(),
                )
            },
        )
    }

    suspend fun setExerciseStatus(sessionExerciseId: Long, status: SessionExerciseStatus) =
        itemDao.setStatus(sessionExerciseId, status)

    /** Drag & drop de "Rutina de hoy": toca el orden de la sesión, nunca el plan. */
    suspend fun reorder(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> itemDao.setPosition(id, index) }
    }

    /**
     * Al empezar un ejercicio, sube en la lista: primero lo ya empezado o resuelto (en el orden en que se
     * hizo), luego este, luego lo pendiente en su orden. Así la lista refleja el orden real del día.
     */
    suspend fun moveStartedToTop(sessionId: Long, sessionExerciseId: Long) = db.withTransaction {
        val items = itemDao.getDetails(sessionId).map { it.item }
        val others = items.filter { it.id != sessionExerciseId }
        val started = others.filter { it.status != SessionExerciseStatus.PENDING }
        val pending = others.filter { it.status == SessionExerciseStatus.PENDING }
        val ordered = started.map { it.id } + sessionExerciseId + pending.map { it.id }
        if (ordered != items.map { it.id }) {
            ordered.forEachIndexed { index, id -> itemDao.setPosition(id, index) }
        }
    }

    /** "Terminar entrenamiento": la sesión queda hecha y su día de la semana, cumplido. */
    suspend fun finish(sessionId: Long) = db.withTransaction {
        val session = sessionDao.get(sessionId) ?: return@withTransaction
        itemDao.skipPending(sessionId)
        sessionDao.close(sessionId, SessionStatus.DONE, clock.instant())
        session.plannedSessionId?.let { plannedId ->
            plannedDao.setSession(plannedId, sessionId)
            plannedDao.setStatus(listOf(plannedId), PlannedStatus.DONE)
        }
    }

    /**
     * Deja la sesión en curso sin terminarla ("Elegir otra sesión" / "Saltar sesión").
     * Sin ninguna serie guardada se borra (no aporta historial); si no, queda como abandonada.
     * La planificada vuelve a pendiente, o pasa a saltada si [skipPlanned].
     */
    suspend fun abandon(sessionId: Long, skipPlanned: Boolean, skipReason: String?) = db.withTransaction {
        val session = sessionDao.get(sessionId) ?: return@withTransaction
        val plannedId = session.plannedSessionId
        if (setLogDao.countForSession(sessionId) == 0) {
            sessionDao.delete(sessionId)
        } else {
            sessionDao.close(sessionId, SessionStatus.ABANDONED, clock.instant())
            plannedId?.let { plannedDao.setSession(it, null) }
        }
        if (plannedId != null && skipPlanned) {
            plannedDao.setStatusAndReason(plannedId, PlannedStatus.SKIPPED, skipReason?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    /** Cambiar el peso actualiza el ejercicio en todos sus días y planes (siguientes sesiones). */
    suspend fun setCurrentWeight(exerciseId: Long, weightKg: Double?) =
        db.dayExerciseDao().setExerciseWeight(exerciseId, weightKg)
}
