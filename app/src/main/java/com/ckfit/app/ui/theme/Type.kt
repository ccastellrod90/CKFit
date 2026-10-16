package com.ckfit.app.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CKFitTypography = Typography()

/** Números del temporizador: sans condensada en negrita, legible a 1 m. */
val TimerTextStyle = TextStyle(
    fontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD)),
    fontWeight = FontWeight.Bold,
    fontSize = 96.sp,
    lineHeight = 100.sp,
)
