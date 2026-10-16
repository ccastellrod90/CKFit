package com.ckfit.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ckfit.app.R
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.model.kgTo
import kotlin.math.roundToInt

@Composable
fun formatDuration(seconds: Int): String {
    val totalMin = (seconds / 60.0).roundToInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) stringResource(R.string.duration_hours_minutes, h, m)
    else stringResource(R.string.duration_minutes, m)
}

@Composable
fun unitLabel(unit: WeightUnit): String = stringResource(
    when (unit) {
        WeightUnit.KG -> R.string.unit_kg
        WeightUnit.LB -> R.string.unit_lb
    },
)

/** Peso guardado en kg mostrado en la unidad del perfil. */
@Composable
fun formatWeight(kg: Double, unit: WeightUnit): String =
    stringResource(R.string.weight_value, kg.kgTo(unit), unitLabel(unit))

@Composable
fun formatWeightChange(kg: Double, unit: WeightUnit): String =
    stringResource(R.string.weight_change_value, kg.kgTo(unit), unitLabel(unit))

fun parseHexColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
