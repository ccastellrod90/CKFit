package com.ckfit.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ckfit.app.R
import com.ckfit.app.data.db.entity.DayExerciseEntity
import com.ckfit.app.data.db.entity.ExerciseEntity
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.model.kgTo
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** "4 × 6–8 · 75 s · 120 kg" (sección 5). */
@Composable
fun formatPrescription(p: DayExerciseEntity, exercise: ExerciseEntity, unit: WeightUnit): String {
    val target = when (exercise.measureType) {
        MeasureType.REPS -> formatRange(p.repsMin, p.repsMax)
        MeasureType.TIME -> formatDurationShort(p.durationSec ?: 0)
    }
    val perSide = if (exercise.perSide) " " + stringResource(R.string.per_side_short) else ""
    val parts = mutableListOf(stringResource(R.string.prescription_sets_target, p.sets, target) + perSide)
    if (p.restSec > 0) {
        parts += stringResource(R.string.seconds_value, p.restSec)
    }
    if (exercise.usesWeight) p.currentWeightKg?.let { parts += formatNumber(it.kgTo(unit)) + " " + unitLabel(unit) }
    return parts.joinToString(" · ")
}

fun formatRange(min: Int?, max: Int?): String = when {
    min == null && max == null -> ""
    min == null || min == max -> "${max ?: min}"
    max == null -> "$min"
    else -> "$min–$max"
}

@Composable
fun formatDurationShort(seconds: Int): String = when {
    seconds >= 60 && seconds % 60 == 0 -> stringResource(R.string.minutes_value, seconds / 60)
    else -> stringResource(R.string.seconds_value, seconds)
}

private val numberFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("es")))

/** 2.5 → "2,5"; 120.0 → "120". */
fun formatNumber(value: Double): String = numberFormat.format(value)

/** Acepta coma o punto decimal. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
