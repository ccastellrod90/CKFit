package com.ckfit.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ckfit.app.R
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.usecase.WeekCard
import com.ckfit.app.domain.usecase.WeekItem
import com.ckfit.app.ui.common.dayName
import com.ckfit.app.ui.components.ColorDot
import com.ckfit.app.ui.components.Pill
import com.ckfit.app.ui.components.PlannedStatusPill

/** Cola de la semana: elegir otra sesión pendiente y deshacer saltos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekSheet(
    week: WeekCard,
    onDismiss: () -> Unit,
    onChoose: (Long) -> Unit,
    onUndoSkip: (Long) -> Unit,
    extraDays: List<RoutineDayEntity>,
    onStartExtra: (Long) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.week_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = stringResource(R.string.week_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.padding(bottom = 16.dp)) {
                items(week.items, key = { it.plannedId }) { item ->
                    WeekRow(item, onChoose, onUndoSkip)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 24.dp))
                }
                if (extraDays.isNotEmpty()) {
                    item(key = "extra-header") {
                        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp)) {
                            Text(
                                text = stringResource(R.string.week_extra_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.week_extra_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(extraDays, key = { "extra-" + it.id }) { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStartExtra(day.id) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(day.colorHex)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = day.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.action_train), color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(item: WeekItem, onChoose: (Long) -> Unit, onUndoSkip: (Long) -> Unit) {
    val choosable = item.status == PlannedStatus.PENDING && !item.isSuggested
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = choosable) { onChoose(item.plannedId) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(item.colorHex)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = dayName(item.dayOfWeek) +
                    if (item.isOptional) " · " + stringResource(R.string.home_today_optional) else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.title ?: stringResource(R.string.week_day_removed),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.skipReason?.let {
                Text(
                    text = stringResource(R.string.week_skip_reason, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            item.isSuggested -> Pill(stringResource(R.string.week_suggested), MaterialTheme.colorScheme.primary)
            choosable -> TextButton(onClick = { onChoose(item.plannedId) }) {
                Text(stringResource(R.string.action_choose), color = MaterialTheme.colorScheme.secondary)
            }
            item.canUndoSkip -> TextButton(onClick = { onUndoSkip(item.plannedId) }) {
                Text(stringResource(R.string.action_undo_skip), color = MaterialTheme.colorScheme.secondary)
            }
            else -> PlannedStatusPill(item.status)
        }
    }
}
