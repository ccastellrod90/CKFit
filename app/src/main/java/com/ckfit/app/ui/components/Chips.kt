package com.ckfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ckfit.app.R
import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.ui.common.parseHexColor

/** Píldora del mockup: fondo tenue del color y texto en el color ("+5 kg"). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, background: Color = color.copy(alpha = 0.15f)) {
    Box(
        modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Una sola línea: si falta espacio, quien la contiene debe ceder (weight(fill = false)), no la píldora.
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Chip de tipo de carga: F verde, V turquesa, Core gris (sección 2). */
@Composable
fun LoadTypeChip(loadType: LoadType, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when (loadType) {
        LoadType.STRENGTH -> R.string.load_short_strength to scheme.primary
        LoadType.VOLUME -> R.string.load_short_volume to scheme.secondary
        LoadType.CORE -> R.string.load_short_core to scheme.onSurfaceVariant
        LoadType.CARDIO -> R.string.load_short_cardio to scheme.onSurface
        LoadType.OTHER -> R.string.load_short_other to scheme.onSurfaceVariant
    }
    Box(
        modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(stringResource(label), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlannedStatusPill(status: PlannedStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when (status) {
        PlannedStatus.PENDING -> R.string.planned_status_pending to scheme.onSurfaceVariant
        PlannedStatus.DONE -> R.string.planned_status_done to scheme.primary
        PlannedStatus.SKIPPED -> R.string.planned_status_skipped to scheme.secondary
        PlannedStatus.MISSED -> R.string.planned_status_missed to scheme.error
    }
    Pill(stringResource(label), color, modifier)
}

@Composable
fun ColorDot(hex: String?, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val color = hex?.let { parseHexColor(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.size(size).background(color, CircleShape))
}
