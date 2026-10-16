package com.ckfit.app.domain.prescription

import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionTest {

    private val base = PrescriptionDraft(
        measureType = MeasureType.REPS,
        perSide = false,
        loadType = LoadType.OTHER,
        sets = 4,
        repsMin = 8,
        repsMax = 10,
        durationSec = null,
        restSec = 30,
        restMaxSec = null,
        weightKg = 50.0,
        weightStepKg = 2.5,
        isIntensitySet = false,
        intensityNote = null,
        notes = "nota",
    )

    @Test
    fun `defaults de fuerza`() {
        val d = LoadDefaults.apply(LoadType.STRENGTH, base)
        assertEquals(6, d.repsMin)
        assertEquals(8, d.repsMax)
        assertEquals(75, d.restSec)
        assertEquals(90, d.restMaxSec)
        assertEquals(4, d.sets) // las series no se tocan
        assertEquals(50.0, d.weightKg!!, 0.0) // ni el peso
    }

    @Test
    fun `defaults de volumen`() {
        val d = LoadDefaults.apply(LoadType.VOLUME, base)
        assertEquals(10 to 12, d.repsMin to d.repsMax)
        assertEquals(45 to 60, d.restSec to d.restMaxSec)
    }

    @Test
    fun `defaults de core solo tocan el descanso`() {
        val d = LoadDefaults.apply(LoadType.CORE, base)
        assertEquals(8 to 10, d.repsMin to d.repsMax)
        assertEquals(60, d.restSec)
        assertNull(d.restMaxSec)
    }

    @Test
    fun `defaults de cardio pasan a TIME con una serie y sin descanso`() {
        val d = LoadDefaults.apply(LoadType.CARDIO, base)
        assertEquals(MeasureType.TIME, d.measureType)
        assertEquals(1, d.sets)
        assertEquals(0, d.restSec)
        assertNull(d.repsMax)
    }

    @Test
    fun `nuevo ejercicio adivina el tipo de carga`() {
        assertEquals(LoadType.CARDIO, LoadDefaults.newDraft(MeasureType.TIME, false, "Cardio").loadType)
        assertEquals(LoadType.CORE, LoadDefaults.newDraft(MeasureType.TIME, false, "Core").loadType)
        val squat = LoadDefaults.newDraft(MeasureType.REPS, false, "Cuádriceps")
        assertEquals(LoadType.VOLUME, squat.loadType)
        assertEquals(10 to 12, squat.repsMin to squat.repsMax)
        assertTrue(PrescriptionValidator.validate(squat).isEmpty())
        val walk = LoadDefaults.newDraft(MeasureType.TIME, false, "Agarre")
        assertTrue(PrescriptionValidator.validate(walk).isEmpty())
    }

    @Test
    fun `validacion`() {
        assertTrue(PrescriptionValidator.validate(base).isEmpty())
        assertEquals(setOf(PrescriptionError.SETS), PrescriptionValidator.validate(base.copy(sets = 0)))
        assertEquals(setOf(PrescriptionError.REPS_RANGE), PrescriptionValidator.validate(base.copy(repsMin = 12, repsMax = 10)))
        assertEquals(setOf(PrescriptionError.REPS_MISSING), PrescriptionValidator.validate(base.copy(repsMax = null)))
        assertEquals(
            setOf(PrescriptionError.DURATION),
            PrescriptionValidator.validate(base.copy(measureType = MeasureType.TIME, durationSec = null)),
        )
        assertEquals(setOf(PrescriptionError.REST_MAX), PrescriptionValidator.validate(base.copy(restSec = 60, restMaxSec = 45)))
        assertEquals(setOf(PrescriptionError.WEIGHT_STEP), PrescriptionValidator.validate(base.copy(weightStepKg = 0.0)))
        assertTrue(PrescriptionValidator.validate(base.copy(weightKg = null)).isEmpty())
    }
}
