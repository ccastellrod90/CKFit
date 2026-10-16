package com.ckfit.app.domain.body

import java.time.LocalDate

data class WeighIn(val date: LocalDate, val weightKg: Double)

data class WeightSummary(
    val latest: WeighIn,
    /** Cambio en kg respecto a hace ~30 días; null si no hay un pesaje anterior con qué comparar. */
    val change30dKg: Double?,
)

object WeightTrend {
    const val WINDOW_DAYS = 30L

    /**
     * Compara el último pesaje con el más reciente de hace 30 días o más; si no lo hay,
     * con el más antiguo dentro de esa ventana (así se ve algo desde el segundo pesaje).
     */
    fun summarize(weighIns: List<WeighIn>): WeightSummary? {
        if (weighIns.isEmpty()) return null
        val sorted = weighIns.sortedBy { it.date }
        val latest = sorted.last()
        val boundary = latest.date.minusDays(WINDOW_DAYS)
        val earlier = sorted.dropLast(1)
        val baseline = earlier.lastOrNull { !it.date.isAfter(boundary) } ?: earlier.firstOrNull()
        return WeightSummary(latest, baseline?.let { latest.weightKg - it.weightKg })
    }
}
