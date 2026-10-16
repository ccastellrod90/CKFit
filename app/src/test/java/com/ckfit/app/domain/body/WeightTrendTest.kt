package com.ckfit.app.domain.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeightTrendTest {

    private val d = LocalDate.of(2026, 9, 26)

    @Test
    fun `sin pesajes no hay resumen`() {
        assertNull(WeightTrend.summarize(emptyList()))
    }

    @Test
    fun `un solo pesaje no tiene variacion`() {
        val s = WeightTrend.summarize(listOf(WeighIn(d, 80.0)))!!
        assertEquals(80.0, s.latest.weightKg, 0.0)
        assertNull(s.change30dKg)
    }

    @Test
    fun `compara con el pesaje mas reciente de hace 30 dias o mas`() {
        val s = WeightTrend.summarize(
            listOf(
                WeighIn(d.minusDays(60), 85.0),
                WeighIn(d.minusDays(31), 83.0),
                WeighIn(d.minusDays(10), 81.0),
                WeighIn(d, 80.0),
            ),
        )!!
        assertEquals(-3.0, s.change30dKg!!, 1e-9)
    }

    @Test
    fun `sin pesajes antiguos usa el mas antiguo de la ventana`() {
        val s = WeightTrend.summarize(listOf(WeighIn(d.minusDays(12), 81.5), WeighIn(d, 80.0)))!!
        assertEquals(-1.5, s.change30dKg!!, 1e-9)
    }
}
