package com.ckfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Solo oscuro, sin dynamicColor (sección 2).
private val CKFitColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Accent,
    onSecondary = OnPrimary,
    tertiary = Accent,
    onTertiary = OnPrimary,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Card,
    onSurface = TextPrimary,
    surfaceContainer = Card,
    surfaceContainerLow = NavBar,
    onSurfaceVariant = TextSecondary,
    outlineVariant = Track,
)

@Composable
fun CKFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CKFitColorScheme,
        typography = CKFitTypography,
        content = content,
    )
}
