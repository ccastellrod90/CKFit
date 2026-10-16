package com.ckfit.app.domain.prescription

import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType

/**
 * Lo que se edita en el editor de ejercicio. Tipo y "por lado" pertenecen al ejercicio del catálogo;
 * el resto a la prescripción del día. Pesos en kg.
 */
data class PrescriptionDraft(
    val measureType: MeasureType,
    val perSide: Boolean,
    /** Del ejercicio del catálogo: false = peso corporal, no se muestra ni se registra peso. */
    val usesWeight: Boolean = true,
    val loadType: LoadType,
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val restSec: Int?,
    val restMaxSec: Int?,
    val weightKg: Double?,
    val weightStepKg: Double?,
    val isIntensitySet: Boolean,
    val intensityNote: String?,
    val notes: String?,
)

/** Valores por defecto al elegir loadType en el editor (sección 3). */
object LoadDefaults {

    fun apply(loadType: LoadType, draft: PrescriptionDraft): PrescriptionDraft {
        val d = draft.copy(loadType = loadType)
        return when (loadType) {
            LoadType.STRENGTH -> d.copy(repsMin = 6, repsMax = 8, restSec = 75, restMaxSec = 90)
            LoadType.VOLUME -> d.copy(repsMin = 10, repsMax = 12, restSec = 45, restMaxSec = 60)
            LoadType.CORE -> d.copy(restSec = 60, restMaxSec = null)
            LoadType.CARDIO -> d.copy(
                measureType = MeasureType.TIME,
                sets = 1,
                repsMin = null,
                repsMax = null,
                restSec = 0,
                restMaxSec = null,
            )
            LoadType.OTHER -> d
        }
    }

    /** loadType inicial al agregar un ejercicio del catálogo a un día. */
    fun guessLoadType(measureType: MeasureType, muscleGroup: String): LoadType = when {
        muscleGroup.equals(CARDIO_GROUP, ignoreCase = true) -> LoadType.CARDIO
        muscleGroup.equals(CORE_GROUP, ignoreCase = true) -> LoadType.CORE
        measureType == MeasureType.TIME -> LoadType.OTHER
        else -> LoadType.VOLUME
    }

    /** Borrador de un ejercicio recién agregado, ya con los defaults de su loadType. */
    fun newDraft(
        measureType: MeasureType,
        perSide: Boolean,
        muscleGroup: String,
        usesWeight: Boolean = true,
        weightKg: Double? = null,
    ): PrescriptionDraft {
        val load = guessLoadType(measureType, muscleGroup)
        val base = PrescriptionDraft(
            measureType = measureType,
            perSide = perSide,
            usesWeight = usesWeight,
            loadType = load,
            sets = if (load == LoadType.CARDIO) 1 else DEFAULT_SETS,
            repsMin = null,
            repsMax = null,
            durationSec = if (measureType == MeasureType.TIME) DEFAULT_DURATION_SEC else null,
            restSec = 60,
            restMaxSec = null,
            weightKg = weightKg,
            weightStepKg = DEFAULT_WEIGHT_STEP_KG,
            isIntensitySet = false,
            intensityNote = null,
            notes = null,
        )
        val withDefaults = apply(load, base)
        return if (measureType == MeasureType.REPS && withDefaults.repsMax == null) {
            withDefaults.copy(repsMin = 10, repsMax = 12)
        } else {
            withDefaults
        }
    }

    const val DEFAULT_SETS = 3
    const val DEFAULT_DURATION_SEC = 30
    const val DEFAULT_WEIGHT_STEP_KG = 2.5
    const val CARDIO_GROUP = "Cardio"
    const val CORE_GROUP = "Core"
}

enum class PrescriptionError {
    SETS,
    REPS_MISSING,
    REPS_RANGE,
    DURATION,
    REST,
    REST_MAX,
    WEIGHT,
    WEIGHT_STEP,
}

object PrescriptionValidator {
    fun validate(d: PrescriptionDraft): Set<PrescriptionError> = buildSet {
        if (d.sets == null || d.sets !in 1..20) add(PrescriptionError.SETS)
        when (d.measureType) {
            MeasureType.REPS -> when {
                d.repsMin == null || d.repsMax == null -> add(PrescriptionError.REPS_MISSING)
                d.repsMin < 1 || d.repsMax > 100 || d.repsMin > d.repsMax -> add(PrescriptionError.REPS_RANGE)
            }
            MeasureType.TIME -> if (d.durationSec == null || d.durationSec !in 1..(4 * 3600)) add(PrescriptionError.DURATION)
        }
        if (d.restSec == null || d.restSec !in 0..900) add(PrescriptionError.REST)
        if (d.restMaxSec != null && d.restSec != null && d.restMaxSec < d.restSec) add(PrescriptionError.REST_MAX)
        if (d.usesWeight) {
            if (d.weightKg != null && d.weightKg < 0) add(PrescriptionError.WEIGHT)
            if (d.weightStepKg == null || d.weightStepKg <= 0) add(PrescriptionError.WEIGHT_STEP)
        }
    }
}
