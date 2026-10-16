package com.ckfit.app.domain.engine

import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.Side
import kotlinx.serialization.Serializable

/** Reloj monotónico en ms. En Android: SystemClock.elapsedRealtime(); en tests, un reloj virtual. */
fun interface TimeSource {
    fun nowMs(): Long
}

sealed interface EngineEvent {
    data object Start : EngineEvent       // "empezar"
    data object Done : EngineEvent        // "listo"
    data object Skip : EngineEvent        // "saltar" (descanso o serie)
    data class AddRest(val sec: Int = 15) : EngineEvent
    data object Pause : EngineEvent
    data object Resume : EngineEvent
    data object Next : EngineEvent        // "siguiente" (lo gestiona quien controla la sesión)
    /** "Terminar ejercicio" antes de hacer todas las series (p. ej. 3 de 4). La serie a medias no se registra. */
    data object EndExercise : EngineEvent
}

/**
 * Estado del motor para sobrevivir a la muerte del proceso: los tiempos son absolutos en la base del
 * [TimeSource] (elapsedRealtime), así al restaurarlo el descanso o la serie siguen donde iban.
 */
@Serializable
data class EngineSnapshot(
    val phase: Phase,
    val set: Int,
    val side: Side? = null,
    val startAt: Long = 0,
    val durMs: Long = 0,
    val pausedAt: Long? = null,
    val autoAdvance: Boolean = true,
    /** Segundos trabajados por lado en la serie en curso (TIME por lado). */
    val workedLeft: Int? = null,
    val workedRight: Int? = null,
) {
    enum class Phase { WAITING, PRE, REPS, TIMED, SWITCH, REST, DONE }

    /** Traslada todos los instantes (tras reiniciar el teléfono, elapsedRealtime vuelve a cero). */
    fun shiftedBy(ms: Long) = copy(startAt = startAt + ms, pausedAt = pausedAt?.plus(ms))
}

sealed interface EngineState {
    data class Waiting(val set: Int, val total: Int) : EngineState
    data class PreCountdown(val remaining: Int) : EngineState         // solo TIME
    data class WorkingReps(val set: Int, val side: Side?, val elapsedMs: Long) : EngineState
    data class WorkingTimed(val set: Int, val side: Side?, val remainingMs: Long) : EngineState
    data class SwitchSide(val set: Int, val remainingMs: Long) : EngineState
    data class Resting(val nextSet: Int, val remainingMs: Long, val totalMs: Long) : EngineState
    data class Paused(val previous: EngineState) : EngineState
    data object ExerciseDone : EngineState
}

enum class BeepType { SHORT, DOUBLE }

/**
 * Avisos semánticos; el servicio los traduce a TTS/bips (Fase 4). Nunca llevan palabras de comando:
 * el texto hablado se arma a partir de estos datos ("Prensa, 4 series de 6 a 8", "Serie 2, vamos"…).
 */
sealed interface AudioCue {
    data class ExerciseIntro(
        val name: String,
        val sets: Int,
        val measureType: MeasureType,
        val repsMin: Int?,
        val repsMax: Int?,
        val durationSec: Int?,
        val perSide: Boolean,
    ) : AudioCue

    /** Inicio de una serie REPS a partir de la 2.ª: "Serie 2, vamos". */
    data class SetStart(val set: Int) : AudioCue

    /** Número de una cuenta atrás: "tres", "dos", "uno"… */
    data class Count(val n: Int) : AudioCue

    /** Fin de la cuenta previa: "ya". */
    data object Go : AudioCue

    /** "cambio" (seguido de su cuenta 3·2·1). */
    data object SwitchSide : AudioCue

    /** Aviso a mitad de una serie por tiempo, con los segundos que quedan ("diez"). */
    data class Halfway(val secondsLeft: Int) : AudioCue

    data class Beep(val type: BeepType) : AudioCue

    /** "Ejercicio terminado. Siguiente: <nombre>". */
    data class ExerciseFinished(val nextName: String?) : AudioCue
}

/** Lo que el motor necesita saber del ejercicio. */
data class ExerciseSpec(
    val name: String,
    val measureType: MeasureType,
    val perSide: Boolean,
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val restSec: Int,
    val nextExerciseName: String?,
)

data class EngineConfig(
    val autoAdvance: Boolean = true,
    val switchSec: Int = 5,
    val restCountdownSec: Int = 5,
    val preCountdownSec: Int = 3,
    val halfwayCue: Boolean = false,
)

/**
 * Serie cerrada (una por lado si es por lado). [durationSec] es el tiempo real trabajado en TIME;
 * null en REPS (las reps se registran al objetivo en el MVP).
 */
data class CompletedSet(
    val setNumber: Int,
    val side: Side?,
    val durationSec: Int?,
)
