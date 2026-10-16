package com.ckfit.app.domain.engine

import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.Side
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.ceil

/**
 * Motor de un ejercicio (sección 7). Kotlin puro: recibe [EngineEvent] con [handle], avanza el tiempo
 * con [tick] (quien lo usa llama a tick periódicamente) y publica [state], [cues] y [completedSets].
 *
 * El tiempo se calcula siempre por diferencia con [timeSource], nunca sumando ticks: un tick tardío
 * (pantalla apagada, pausa del sistema) no desajusta nada, solo retrasa el siguiente aviso.
 *
 * @param startSet serie por la que empezar (para reanudar tras matar la app: la siguiente a la última guardada).
 * @param restoreFrom estado guardado con [snapshot]: el motor sigue exactamente donde iba (sin repetir la intro).
 */
class WorkoutEngine(
    private val spec: ExerciseSpec,
    config: EngineConfig,
    private val timeSource: TimeSource,
    startSet: Int = 1,
    restoreFrom: EngineSnapshot? = null,
) {
    /** Fases internas; los tiempos son absolutos (ms del [timeSource]). */
    private sealed interface Phase {
        data class Waiting(val set: Int) : Phase
        data class Pre(val set: Int, val startAt: Long, val durMs: Long) : Phase
        data class Reps(val set: Int, val side: Side?, val startAt: Long) : Phase
        data class Timed(val set: Int, val side: Side?, val startAt: Long, val durMs: Long) : Phase
        data class Switch(val set: Int, val startAt: Long, val durMs: Long) : Phase
        data class Rest(val nextSet: Int, val startAt: Long, val durMs: Long) : Phase
        data class Paused(val inner: Phase, val pausedAt: Long) : Phase
        data object Done : Phase
    }

    var config: EngineConfig = config
        private set

    private var phase: Phase = Phase.Done // se fija en init
        set(value) {
            field = value
            version++
        }

    /** Cambia con cada transición, pausa o +15 s: momento de guardar un [snapshot]. */
    var version: Int = 0
        private set

    /** Último número de cuenta atrás anunciado en la fase actual (para no repetirlo). */
    private var lastCount = Int.MAX_VALUE
    private var halfwayAnnounced = false

    /** Segundos trabajados por lado en la serie en curso (solo TIME). */
    private val workedSec = mutableMapOf<Side?, Int>()

    private val _state: MutableStateFlow<EngineState>
    val state: StateFlow<EngineState>

    private val cueChannel = Channel<AudioCue>(Channel.UNLIMITED)
    val cues: Flow<AudioCue> = cueChannel.receiveAsFlow()

    private val setChannel = Channel<CompletedSet>(Channel.UNLIMITED)
    val completedSets: Flow<CompletedSet> = setChannel.receiveAsFlow()

    val totalSets: Int get() = spec.sets

    init {
        require(spec.sets >= 1) { "Un ejercicio necesita al menos una serie" }
        if (restoreFrom != null) {
            phase = restoreFrom.toPhase()
            this.config = this.config.copy(autoAdvance = restoreFrom.autoAdvance)
            restoreFrom.workedLeft?.let { workedSec[Side.LEFT] = it }
            restoreFrom.workedRight?.let { workedSec[Side.RIGHT] = it }
        } else if (startSet > spec.sets) {
            phase = Phase.Done
        } else {
            phase = Phase.Waiting(startSet.coerceAtLeast(1))
            emit(
                AudioCue.ExerciseIntro(
                    spec.name, spec.sets, spec.measureType, spec.repsMin, spec.repsMax, spec.durationSec, spec.perSide,
                ),
            )
        }
        _state = MutableStateFlow(render(phase, now()))
        state = _state
    }

    /**
     * Series cerradas pendientes de guardar, sin suspender: quien controla el motor las guarda justo
     * después de cada [handle]/[tick], así ninguna se pierde si cambia de ejercicio en ese momento.
     * (Alternativa a recolectar [completedSets]; usar solo una de las dos.)
     */
    fun pollCompletedSets(): List<CompletedSet> = buildList {
        while (true) add(setChannel.tryReceive().getOrNull() ?: break)
    }

    /** Auto/Manual se puede cambiar en cualquier momento; aplica al próximo fin de descanso. */
    fun setAutoAdvance(value: Boolean) {
        config = config.copy(autoAdvance = value)
    }

    fun handle(event: EngineEvent) {
        val now = now()
        when (event) {
            EngineEvent.Start -> (phase as? Phase.Waiting)?.let { startSet(it.set, now) }
            EngineEvent.Done -> when (val p = phase) {
                is Phase.Reps -> finishSide(p.set, p.side, now, workedSec = null)
                is Phase.Timed -> finishSide(p.set, p.side, now, workedSec = elapsedSec(p.startAt, now))
                else -> Unit
            }
            EngineEvent.Skip -> when (val p = phase) {
                is Phase.Waiting -> afterSet(p.set, now, logged = false, fromWaiting = true)
                is Phase.Pre -> afterSet(p.set, now, logged = false)
                is Phase.Reps -> afterSet(p.set, now, logged = false)
                is Phase.Timed -> afterSet(p.set, now, logged = false)
                is Phase.Switch -> afterSet(p.set, now, logged = false)
                is Phase.Rest -> endRest(p.nextSet, now)
                else -> Unit
            }
            is EngineEvent.AddRest -> (phase as? Phase.Rest)?.let {
                enter(it.copy(durMs = it.durMs + event.sec * 1000L), resetCounts = true)
            }
            EngineEvent.Pause -> when (val p = phase) {
                is Phase.Waiting, is Phase.Paused, Phase.Done -> Unit
                else -> phase = Phase.Paused(p, now)
            }
            EngineEvent.Resume -> (phase as? Phase.Paused)?.let {
                phase = it.inner.shiftedBy(now - it.pausedAt)
            }
            EngineEvent.Next -> Unit
            EngineEvent.EndExercise -> if (phase != Phase.Done) {
                // La serie a medias (si la hay) no se registra.
                workedSec.clear()
                enter(Phase.Done)
                emit(AudioCue.ExerciseFinished(spec.nextExerciseName))
            }
        }
        advance(now)
    }

    /** Estado actual para guardarlo y restaurarlo con el constructor ([restoreFrom]). */
    fun snapshot(): EngineSnapshot {
        val paused = phase as? Phase.Paused
        val base = (paused?.inner ?: phase).toSnapshot()
        return base.copy(
            pausedAt = paused?.pausedAt,
            autoAdvance = config.autoAdvance,
            workedLeft = workedSec[Side.LEFT],
            workedRight = workedSec[Side.RIGHT],
        )
    }

    /** Avanza el reloj: transiciones por tiempo y cuentas atrás. */
    fun tick() = advance(now())

    private fun advance(now: Long) {
        // Varias transiciones pueden encadenarse en un mismo instante (p. ej. descanso de 0 s).
        var guard = 0
        while (guard++ < MAX_CHAINED_TRANSITIONS) {
            if (!step(now)) break
        }
        _state.value = render(phase, now)
    }

    /** Devuelve true si hubo una transición. */
    private fun step(now: Long): Boolean {
        when (val p = phase) {
            is Phase.Pre -> {
                val rem = p.startAt + p.durMs - now
                if (rem <= 0) {
                    emit(AudioCue.Go)
                    enterTimed(p.set, firstSide(), now)
                    return true
                }
                countdown(rem, config.preCountdownSec)
            }
            is Phase.Timed -> {
                val rem = p.startAt + p.durMs - now
                if (rem <= 0) {
                    finishSide(p.set, p.side, now, workedSec = (p.durMs / 1000).toInt())
                    return true
                }
                if (config.halfwayCue && !halfwayAnnounced && rem <= p.durMs / 2) {
                    halfwayAnnounced = true
                    emit(AudioCue.Halfway(ceilSec(p.durMs / 2)))
                }
                countdown(rem, FINAL_COUNT_SEC)
            }
            is Phase.Switch -> {
                val rem = p.startAt + p.durMs - now
                if (rem <= 0) {
                    startSecondSide(p.set, now)
                    return true
                }
                countdown(rem, FINAL_COUNT_SEC)
            }
            is Phase.Rest -> {
                val rem = p.startAt + p.durMs - now
                if (rem <= 0) {
                    endRest(p.nextSet, now)
                    return true
                }
                // Cuenta final desde N s; si el descanso es más corto, desde su duración.
                countdown(rem, minOf(config.restCountdownSec, (p.durMs / 1000).toInt()))
            }
            else -> Unit
        }
        return false
    }

    private fun startSet(set: Int, now: Long) {
        workedSec.clear()
        when (spec.measureType) {
            MeasureType.REPS -> {
                if (set >= 2) emit(AudioCue.SetStart(set))
                enter(Phase.Reps(set, firstSide(), now))
            }
            MeasureType.TIME ->
                if (config.preCountdownSec > 0) {
                    enter(Phase.Pre(set, now, config.preCountdownSec * 1000L))
                } else {
                    emit(AudioCue.Go)
                    enterTimed(set, firstSide(), now)
                }
        }
    }

    private fun enterTimed(set: Int, side: Side?, now: Long) {
        halfwayAnnounced = false
        enter(Phase.Timed(set, side, now, (spec.durationSec ?: 0) * 1000L))
    }

    /** Cierre de un lado (o de la serie si no es por lado). */
    private fun finishSide(set: Int, side: Side?, now: Long, workedSec: Int?) {
        if (workedSec != null) this.workedSec[side] = workedSec
        if (spec.perSide && side == Side.LEFT) {
            emit(AudioCue.SwitchSide)
            if (config.switchSec > 0) {
                enter(Phase.Switch(set, now, config.switchSec * 1000L))
            } else {
                startSecondSide(set, now)
            }
        } else {
            afterSet(set, now, logged = true)
        }
    }

    private fun startSecondSide(set: Int, now: Long) {
        emit(AudioCue.Go)
        when (spec.measureType) {
            MeasureType.REPS -> enter(Phase.Reps(set, Side.RIGHT, now))
            MeasureType.TIME -> enterTimed(set, Side.RIGHT, now)
        }
    }

    /**
     * Tras una serie: se registra (si se hizo) y se pasa al descanso, a la siguiente serie o al final.
     * Saltar desde "esperando" no tiene descanso: la siguiente serie también queda esperando.
     */
    private fun afterSet(set: Int, now: Long, logged: Boolean, fromWaiting: Boolean = false) {
        if (logged) {
            val sides = if (spec.perSide) listOf(Side.LEFT, Side.RIGHT) else listOf(null)
            sides.forEach { side ->
                setChannel.trySend(
                    CompletedSet(
                        setNumber = set,
                        side = side,
                        durationSec = if (spec.measureType == MeasureType.TIME) workedSec[side] ?: 0 else null,
                    ),
                )
            }
        }
        workedSec.clear()
        when {
            set >= spec.sets -> {
                enter(Phase.Done)
                emit(AudioCue.ExerciseFinished(spec.nextExerciseName))
            }
            fromWaiting -> enter(Phase.Waiting(set + 1))
            spec.restSec > 0 -> {
                enter(Phase.Rest(set + 1, now, spec.restSec * 1000L))
                emit(AudioCue.Beep(BeepType.SHORT))
            }
            else -> endRest(set + 1, now)
        }
    }

    private fun endRest(nextSet: Int, now: Long) {
        if (config.autoAdvance) {
            startSet(nextSet, now)
        } else {
            enter(Phase.Waiting(nextSet))
            emit(AudioCue.Beep(BeepType.DOUBLE))
        }
    }

    private fun enter(next: Phase, resetCounts: Boolean = true) {
        phase = next
        if (resetCounts) lastCount = Int.MAX_VALUE
    }

    /** Anuncia cada segundo entero de los últimos [fromSec] (una vez cada uno). */
    private fun countdown(remMs: Long, fromSec: Int) {
        val s = ceilSec(remMs)
        if (s in 1..fromSec && s < lastCount) {
            lastCount = s
            emit(AudioCue.Count(s))
        }
    }

    private fun firstSide(): Side? = if (spec.perSide) Side.LEFT else null

    private fun emit(cue: AudioCue) {
        cueChannel.trySend(cue)
    }

    private fun now() = timeSource.nowMs()

    private fun render(p: Phase, now: Long): EngineState = when (p) {
        is Phase.Waiting -> EngineState.Waiting(p.set, spec.sets)
        is Phase.Pre -> EngineState.PreCountdown(ceilSec(p.startAt + p.durMs - now).coerceAtLeast(0))
        is Phase.Reps -> EngineState.WorkingReps(p.set, p.side, (now - p.startAt).coerceAtLeast(0))
        is Phase.Timed -> EngineState.WorkingTimed(p.set, p.side, (p.startAt + p.durMs - now).coerceAtLeast(0))
        is Phase.Switch -> EngineState.SwitchSide(p.set, (p.startAt + p.durMs - now).coerceAtLeast(0))
        is Phase.Rest -> EngineState.Resting(p.nextSet, (p.startAt + p.durMs - now).coerceAtLeast(0), p.durMs)
        is Phase.Paused -> EngineState.Paused(render(p.inner, p.pausedAt))
        Phase.Done -> EngineState.ExerciseDone
    }

    private fun Phase.toSnapshot(): EngineSnapshot = when (this) {
        is Phase.Waiting -> EngineSnapshot(EngineSnapshot.Phase.WAITING, set)
        is Phase.Pre -> EngineSnapshot(EngineSnapshot.Phase.PRE, set, startAt = startAt, durMs = durMs)
        is Phase.Reps -> EngineSnapshot(EngineSnapshot.Phase.REPS, set, side, startAt)
        is Phase.Timed -> EngineSnapshot(EngineSnapshot.Phase.TIMED, set, side, startAt, durMs)
        is Phase.Switch -> EngineSnapshot(EngineSnapshot.Phase.SWITCH, set, startAt = startAt, durMs = durMs)
        is Phase.Rest -> EngineSnapshot(EngineSnapshot.Phase.REST, nextSet, startAt = startAt, durMs = durMs)
        is Phase.Paused -> inner.toSnapshot()
        Phase.Done -> EngineSnapshot(EngineSnapshot.Phase.DONE, spec.sets)
    }

    private fun EngineSnapshot.toPhase(): Phase {
        val inner = when (phase) {
            EngineSnapshot.Phase.WAITING -> Phase.Waiting(set)
            EngineSnapshot.Phase.PRE -> Phase.Pre(set, startAt, durMs)
            EngineSnapshot.Phase.REPS -> Phase.Reps(set, side, startAt)
            EngineSnapshot.Phase.TIMED -> Phase.Timed(set, side, startAt, durMs)
            EngineSnapshot.Phase.SWITCH -> Phase.Switch(set, startAt, durMs)
            EngineSnapshot.Phase.REST -> Phase.Rest(set, startAt, durMs)
            EngineSnapshot.Phase.DONE -> Phase.Done
        }
        return if (pausedAt != null && inner !is Phase.Waiting && inner != Phase.Done) Phase.Paused(inner, pausedAt) else inner
    }

    private fun Phase.shiftedBy(ms: Long): Phase = when (this) {
        is Phase.Pre -> copy(startAt = startAt + ms)
        is Phase.Reps -> copy(startAt = startAt + ms)
        is Phase.Timed -> copy(startAt = startAt + ms)
        is Phase.Switch -> copy(startAt = startAt + ms)
        is Phase.Rest -> copy(startAt = startAt + ms)
        else -> this
    }

    private companion object {
        const val FINAL_COUNT_SEC = 3
        const val MAX_CHAINED_TRANSITIONS = 16

        fun ceilSec(ms: Long): Int = ceil(ms / 1000.0).toInt()

        fun elapsedSec(startAt: Long, now: Long): Int = ((now - startAt) / 1000).toInt()
    }
}
