package com.ckfit.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.ckfit.app.R

/** Nombre del día; dayOfWeek 1 = lunes … 7 = domingo. */
@Composable
fun dayName(dayOfWeek: Int): String = stringArrayResource(R.array.day_names)[(dayOfWeek - 1).coerceIn(0, 6)]

/** Colores que se pueden asignar a un día (los del plan Nivel 2 + algunos más). */
val DayColorPresets = listOf(
    "#9FD33F", "#45D9C5", "#2BA89A", "#F28C28", "#F2C94C", "#E5484D", "#8E6CEF", "#8A9099",
)
