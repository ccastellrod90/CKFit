package com.ckfit.app.service

import android.content.Context
import com.ckfit.app.data.db.dao.SessionExerciseDetail
import com.ckfit.app.data.db.entity.DayExerciseEntity
import com.ckfit.app.data.repo.ActiveWorkout
import com.ckfit.app.data.repo.ActiveWorkoutStore
import com.ckfit.app.data.repo.SessionRepository
import com.ckfit.app.data.repo.SetToLog
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.di.ApplicationScope
import com.ckfit.app.domain.engine.AudioCue
import com.ckfit.app.domain.engine.CompletedSet
import com.ckfit.app.domain.engine.EngineConfig
import com.ckfit.app.domain.engine.EngineEvent
import com.ckfit.app.domain.engine.EngineSnapshot
import com.ckfit.app.domain.engine.EngineState
import com.ckfit.app.domain.engine.ExerciseSpec
import com.ckfit.app.domain.engine.TimeSource
import com.ckfit.app.domain.engine.WorkoutEngine
import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.SessionExerciseStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que muestran el Modo ejercicio y la notificación. Pesos en kg. */
data class ActiveExercise(
    val sessionId: Long,
    val sessionExerciseId: Long,
    val dayExerciseId: Long?,
    val exerciseId: Long,
    val name: String,
    /** false = peso corporal: no se muestra ni se registra peso. */
    val usesWeight: Boolean,
    val loadType: LoadType,
    val measureType: MeasureType,
    val perSide: Boolean,
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val restSec: Int,
    val isIntensitySet: Boolean,
    val intensityNote: String?,
    val notes: String?,
    val weightKg: Double?,
    val weightStepKg: Double,
    /** Peso con el que se hizo la vez anterior (o el del plan si es la primera): bajar de ahí tiene aviso. */
    val referenceWeightKg: Double?,
    val nextSessionExerciseId: Long?,
    val nextName: String?,
)

data class WorkoutState(
    val exercise: ActiveExercise,
    val engine: EngineState,
    val autoAdvance: Boolean,
)

/** Recibe los avisos del motor (TTS, bips y vibración: [AudioCuePlayer]). */
interface CueSink {
    fun play(cue: AudioCue)
}

/**
 * Dueño del [WorkoutEngine] del ejercicio abierto. Vive lo que el proceso (no depende de pantallas):
 * la UI, la notificación y la voz envían los mismos [EngineEvent] por [send].
 * Todo lo del motor corre en un único hilo lógico ([engineDispatcher]): el motor no es concurrente.
 * El estado del motor se guarda en cada transición: si el proceso muere, [restoreIfNeeded] lo retoma
 * con el tiempo corriendo desde donde iba.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WorkoutController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val store: ActiveWorkoutStore,
    private val timeSource: TimeSource,
    private val clock: Clock,
    private val cueSink: CueSink,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** El ejercicio abierto: su motor, sus datos y su bucle de ticks. */
    private class Running(val engine: WorkoutEngine, var exercise: ActiveExercise) {
        var job: Job? = null
        var savedVersion = -1
    }

    private var running: Running? = null

    private val _state = MutableStateFlow<WorkoutState?>(null)
    val state: StateFlow<WorkoutState?> = _state

    /** Último evento o cambio de fase (ms del [timeSource]); el servicio apaga el micro tras 10 min sin nada. */
    @Volatile
    var lastActivityAt: Long = timeSource.nowMs()
        private set

    /** Abre (o reabre) un ejercicio de la sesión; retoma su estado guardado o empieza tras la última serie. */
    suspend fun open(sessionExerciseId: Long) = withContext(engineDispatcher) {
        if (running?.exercise?.sessionExerciseId == sessionExerciseId) return@withContext
        val saved = store.load()?.takeIf { it.sessionExerciseId == sessionExerciseId }
        openInternal(sessionExerciseId, saved)
    }

    /**
     * Al abrir la app (o si el sistema reinicia el servicio): si había un ejercicio en marcha, lo retoma.
     * Devuelve true si quedó un ejercicio corriendo (serie o descanso) y hace falta el servicio.
     */
    suspend fun restoreIfNeeded(): Boolean = withContext(engineDispatcher) {
        val saved = store.load() ?: return@withContext false
        if (running?.exercise?.sessionExerciseId != saved.sessionExerciseId) {
            // Solo si el ejercicio guardado pertenece a la sesión que sigue en curso.
            val inProgress = sessions.getInProgress()
            val item = sessions.getExercise(saved.sessionExerciseId)?.item
            if (inProgress == null || item?.sessionId != inProgress.id) {
                store.clear()
                return@withContext false
            }
            openInternal(saved.sessionExerciseId, saved)
        }
        val engineState = _state.value?.engine
        engineState != null && engineState !is EngineState.Waiting && engineState != EngineState.ExerciseDone
    }

    private suspend fun openInternal(sessionExerciseId: Long, saved: ActiveWorkout?) {
        val detail = sessions.getExercise(sessionExerciseId) ?: return
        val prescription = detail.dayExercise ?: return
        val exercise = detail.exercise ?: return
        val next = nextOf(detail)
        val training = settings.training.first()
        val active = ActiveExercise(
            sessionId = detail.item.sessionId,
            sessionExerciseId = sessionExerciseId,
            dayExerciseId = prescription.id,
            exerciseId = exercise.id,
            name = exercise.name,
            usesWeight = exercise.usesWeight,
            loadType = prescription.loadType,
            measureType = exercise.measureType,
            perSide = exercise.perSide,
            sets = prescription.sets,
            repsMin = prescription.repsMin,
            repsMax = prescription.repsMax,
            durationSec = prescription.durationSec,
            restSec = prescription.restSec,
            isIntensitySet = prescription.isIntensitySet,
            intensityNote = prescription.intensityNote,
            notes = prescription.notes,
            weightKg = prescription.currentWeightKg,
            weightStepKg = prescription.weightStepKg.takeIf { it > 0 } ?: DayExerciseEntity.DEFAULT_WEIGHT_STEP_KG,
            referenceWeightKg = sessions.previousWeight(exercise.id, detail.item.sessionId) ?: prescription.currentWeightKg,
            nextSessionExerciseId = next?.item?.id,
            nextName = next?.exercise?.name,
        )
        val engine = WorkoutEngine(
            spec = ExerciseSpec(
                name = active.name,
                measureType = active.measureType,
                perSide = active.perSide,
                sets = active.sets,
                repsMin = active.repsMin,
                repsMax = active.repsMax,
                durationSec = active.durationSec,
                restSec = active.restSec,
                nextExerciseName = active.nextName,
            ),
            config = EngineConfig(
                autoAdvance = training.autoAdvance,
                switchSec = training.switchSideSec,
                restCountdownSec = training.restCountdownSec,
                preCountdownSec = training.preCountdownSec,
                halfwayCue = training.halfwayCue,
            ),
            timeSource = timeSource,
            startSet = sessions.lastSetNumber(sessionExerciseId) + 1,
            restoreFrom = saved?.let { restoredSnapshot(it) },
        )
        running?.job?.cancel()
        val run = Running(engine, active)
        running = run
        _state.value = WorkoutState(active, engine.state.value, engine.config.autoAdvance)
        lastActivityAt = timeSource.nowMs()
        if (engine.state.value == EngineState.ExerciseDone) markDone(active)
        run.job = scope.launch(engineDispatcher) {
            launch { engine.cues.collect(cueSink::play) }
            while (isActive) {
                engine.tick()
                afterStep(run)
                delay(TICK_MS)
            }
        }
    }

    /**
     * Snapshot guardado → base de tiempo actual. Si el teléfono se reinició, el reloj monotónico volvió a
     * empezar: se traslada según lo que avanzó el reloj de pared.
     */
    private fun restoredSnapshot(saved: ActiveWorkout): EngineSnapshot {
        val nowElapsed = timeSource.nowMs()
        if (nowElapsed >= saved.savedAtElapsedMs) return saved.snapshot
        val wallDelta = (clock.millis() - saved.savedAtWallMs).coerceAtLeast(0)
        return saved.snapshot.shiftedBy(nowElapsed - wallDelta - saved.savedAtElapsedMs)
    }

    /** Botones, notificación y voz disparan exactamente estos eventos. */
    fun send(event: EngineEvent) {
        scope.launch(engineDispatcher) {
            val run = running ?: return@launch
            lastActivityAt = timeSource.nowMs()
            // "Siguiente" con el ejercicio terminado abre el siguiente; la pantalla lo sigue sola.
            if (event == EngineEvent.Next) {
                val next = run.exercise.nextSessionExerciseId
                if (run.engine.state.value == EngineState.ExerciseDone && next != null) openInternal(next, saved = null)
                return@launch
            }
            val before = run.engine.state.value
            run.engine.handle(event)
            afterStep(run)
            if (event == EngineEvent.Start && before is EngineState.Waiting) {
                // Primera serie (o tras reabrir la app): el servicio mantiene vivo el proceso con la pantalla apagada.
                WorkoutService.start(context)
                val item = sessions.getExercise(run.exercise.sessionExerciseId)?.item
                if (item?.status == SessionExerciseStatus.PENDING) {
                    sessions.setExerciseStatus(item.id, SessionExerciseStatus.IN_PROGRESS)
                    sessions.moveStartedToTop(item.sessionId, item.id)
                }
            }
        }
    }

    fun setAutoAdvance(value: Boolean) {
        scope.launch(engineDispatcher) {
            running?.let {
                it.engine.setAutoAdvance(value)
                afterStep(it, forceSave = true)
            }
            _state.value = _state.value?.copy(autoAdvance = value)
            settings.setAutoAdvance(value)
        }
    }

    /** Nuevo peso (kg): queda en el plan (siguiente sesión) y en las siguientes series. */
    fun setWeight(weightKg: Double?) {
        scope.launch(engineDispatcher) { applyWeight(weightKg) }
    }

    fun stepWeight(direction: Int) {
        scope.launch(engineDispatcher) {
            val active = running?.exercise ?: return@launch
            applyWeight(((active.weightKg ?: 0.0) + direction * active.weightStepKg).coerceAtLeast(0.0))
        }
    }

    private suspend fun applyWeight(weightKg: Double?) {
        val run = running ?: return
        val value = weightKg?.coerceAtLeast(0.0)
        run.exercise = run.exercise.copy(weightKg = value)
        _state.value = _state.value?.copy(exercise = run.exercise)
        sessions.setCurrentWeight(run.exercise.exerciseId, value)
    }

    /** Fin o abandono de la sesión: suelta el motor, olvida el estado guardado y para el servicio. */
    suspend fun close() = withContext(engineDispatcher) {
        running?.job?.cancel()
        running = null
        _state.value = null
        store.clear()
        WorkoutService.stop(context)
    }

    /**
     * Tras cada tick o evento: guarda las series cerradas (todas sus filas en una transacción, ligadas al
     * ejercicio que las generó), guarda el estado del motor si cambió de fase y publica el estado.
     */
    private suspend fun afterStep(run: Running, forceSave: Boolean = false) {
        val closed = run.engine.pollCompletedSets()
        if (closed.isNotEmpty()) persist(run.exercise, closed)
        if (running !== run) return
        if (forceSave || run.engine.version != run.savedVersion) {
            run.savedVersion = run.engine.version
            lastActivityAt = timeSource.nowMs()
            store.save(
                ActiveWorkout(
                    sessionExerciseId = run.exercise.sessionExerciseId,
                    snapshot = run.engine.snapshot(),
                    savedAtElapsedMs = timeSource.nowMs(),
                    savedAtWallMs = clock.millis(),
                ),
            )
        }
        val engineState = run.engine.state.value
        val previous = _state.value?.engine
        if (previous != engineState) {
            _state.value = _state.value?.copy(engine = engineState, autoAdvance = run.engine.config.autoAdvance)
            if (engineState == EngineState.ExerciseDone && previous != EngineState.ExerciseDone) markDone(run.exercise)
        }
    }

    private suspend fun persist(active: ActiveExercise, closed: List<CompletedSet>) {
        closed.groupBy { it.setNumber }.forEach { (_, rows) ->
            sessions.logSet(
                active.sessionExerciseId,
                rows.map {
                    SetToLog(
                        setNumber = it.setNumber,
                        side = it.side,
                        weightKg = if (active.usesWeight) active.weightKg else null,
                        // MVP: las series se marcan cumplidas al objetivo (sección 3).
                        reps = if (active.measureType == MeasureType.REPS) active.repsMax ?: active.repsMin else null,
                        durationSec = it.durationSec,
                    )
                },
            )
        }
    }

    private suspend fun markDone(active: ActiveExercise) {
        sessions.setExerciseStatus(active.sessionExerciseId, SessionExerciseStatus.DONE)
    }

    /** Siguiente por hacer después de este en el orden de la sesión; si no hay, el primero pendiente antes. */
    private suspend fun nextOf(current: SessionExerciseDetail): SessionExerciseDetail? {
        val all = sessions.getExercises(current.item.sessionId)
        val index = all.indexOfFirst { it.item.id == current.item.id }
        val open = all.withIndex().filter { (_, it) ->
            it.item.id != current.item.id && it.dayExercise != null &&
                it.item.status in setOf(SessionExerciseStatus.PENDING, SessionExerciseStatus.IN_PROGRESS)
        }
        return (open.firstOrNull { it.index > index } ?: open.firstOrNull())?.value
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
