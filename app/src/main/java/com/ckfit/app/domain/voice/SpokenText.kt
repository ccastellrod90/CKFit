package com.ckfit.app.domain.voice

import com.ckfit.app.domain.engine.AudioCue
import com.ckfit.app.domain.model.MeasureType

/**
 * Texto que se pronuncia para cada aviso (sección 7). Nunca contiene palabras de la gramática de
 * comandos ni sus sinónimos ("vamos", "ya", "siguiente"…): el micrófono podría oír al propio teléfono.
 * Por eso "Serie 2, vamos" → "Serie 2, a tope"; "ya" → "arriba"; "Siguiente: X" → "Ahora toca X".
 */
object SpokenText {

    /** null = el aviso no se habla (bips). */
    fun of(cue: AudioCue): String? = when (cue) {
        is AudioCue.ExerciseIntro -> intro(cue)
        is AudioCue.SetStart -> "Serie ${cue.set}, a tope"
        is AudioCue.Count -> number(cue.n)
        AudioCue.Go -> "Arriba"
        AudioCue.SwitchSide -> "Cambio de lado"
        is AudioCue.Halfway -> "Mitad, quedan ${number(cue.secondsLeft)}"
        is AudioCue.ExerciseFinished ->
            cue.nextName?.let { "Ejercicio terminado. Ahora toca $it" } ?: "Ejercicio terminado. Buen trabajo"
        is AudioCue.Beep -> null
    }

    private fun intro(c: AudioCue.ExerciseIntro): String {
        val series = if (c.sets == 1) "una serie" else "${number(c.sets)} series"
        val target = when (c.measureType) {
            MeasureType.REPS -> when {
                c.repsMin != null && c.repsMax != null && c.repsMin != c.repsMax ->
                    "de ${number(c.repsMin)} a ${number(c.repsMax)}"
                else -> (c.repsMax ?: c.repsMin)?.let { "de ${number(it)}" }
            }
            MeasureType.TIME -> c.durationSec?.let { "de ${duration(it)}" }
        }
        val perSide = if (c.perSide) " por lado" else ""
        return listOfNotNull(c.name, "$series${target?.let { " $it" }.orEmpty()}$perSide").joinToString(", ")
    }

    private fun duration(sec: Int): String = when {
        sec >= 60 && sec % 60 == 0 -> if (sec == 60) "un minuto" else "${number(sec / 60)} minutos"
        else -> "${number(sec)} segundos"
    }

    private val WORDS = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
        "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte",
    )

    /** En palabras hasta 20 (cuentas atrás claras); a partir de ahí la voz lee bien los dígitos. */
    fun number(n: Int): String = WORDS.getOrNull(n) ?: n.toString()
}
