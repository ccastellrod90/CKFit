package com.ckfit.app.domain.estimate

import com.ckfit.app.domain.model.MeasureType
import org.junit.Assert.assertEquals
import org.junit.Test

class EstimateDurationTest {

    private fun reps(sets: Int, max: Int, rest: Int, perSide: Boolean = false) =
        DurationInput(MeasureType.REPS, perSide, sets, repsMin = max - 2, repsMax = max, durationSec = null, restSec = rest)

    private fun time(sets: Int, sec: Int, rest: Int, perSide: Boolean = false) =
        DurationInput(MeasureType.TIME, perSide, sets, repsMin = null, repsMax = null, durationSec = sec, restSec = rest)

    @Test
    fun `REPS usa repsMax por 4 s`() {
        // Prensa 4 × 6–8, 75 s: 4 × (32 + 75) − 75
        assertEquals(353, EstimateDuration.exerciseSeconds(reps(4, 8, 75), switchSec = 5))
    }

    @Test
    fun `REPS por lado duplica el trabajo sin tiempo de cambio`() {
        // Zancada lateral 4 × 10–12 por lado, 45 s: 4 × (96 + 45) − 45
        assertEquals(519, EstimateDuration.exerciseSeconds(reps(4, 12, 45, perSide = true), switchSec = 5))
    }

    @Test
    fun `TIME por lado suma el cambio de lado`() {
        // Plancha lateral 3 × 20 s por lado, 60 s: 3 × (20×2 + 5 + 60) − 60
        assertEquals(255, EstimateDuration.exerciseSeconds(time(3, 20, 60, perSide = true), switchSec = 5))
    }

    @Test
    fun `TIME simple`() {
        assertEquals(260, EstimateDuration.exerciseSeconds(time(4, 20, 60), switchSec = 5))
    }

    @Test
    fun `descanso 0`() {
        assertEquals(2700, EstimateDuration.exerciseSeconds(time(1, 2700, 0), switchSec = 5))
    }

    @Test
    fun `sesion del lunes de Nivel 2`() {
        val monday = listOf(
            reps(4, 8, 75),
            reps(4, 12, 45),
            reps(4, 12, 45, perSide = true),
            reps(4, 20, 45),
            reps(4, 12, 45),
            time(4, 20, 60),
            time(3, 20, 60, perSide = true),
        )
        assertEquals(2496, EstimateDuration.sessionSeconds(monday, switchSec = 5))
    }
}
