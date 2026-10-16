package com.ckfit.app.domain.estimate

import com.ckfit.app.domain.model.MeasureType

/** Datos mínimos de una prescripción para estimar su duración. */
data class DurationInput(
    val measureType: MeasureType,
    val perSide: Boolean,
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val restSec: Int,
)

/**
 * Tiempo estimado (sección 5):
 * ejercicio = sets × (trabajo + restSec) − restSec.
 * Trabajo REPS = repsMax × 4 s (× 2 si es por lado).
 * Trabajo TIME = durationSec × (2 si es por lado) + switchSec (solo si es por lado).
 */
object EstimateDuration {
    const val SECONDS_PER_REP = 4

    fun workSeconds(input: DurationInput, switchSec: Int): Int {
        val sides = if (input.perSide) 2 else 1
        return when (input.measureType) {
            MeasureType.REPS -> (input.repsMax ?: input.repsMin ?: 0) * SECONDS_PER_REP * sides
            MeasureType.TIME -> (input.durationSec ?: 0) * sides + if (input.perSide) switchSec else 0
        }
    }

    fun exerciseSeconds(input: DurationInput, switchSec: Int): Int {
        if (input.sets <= 0) return 0
        val work = workSeconds(input, switchSec)
        return input.sets * (work + input.restSec) - input.restSec
    }

    fun sessionSeconds(inputs: List<DurationInput>, switchSec: Int): Int =
        inputs.sumOf { exerciseSeconds(it, switchSec) }
}
