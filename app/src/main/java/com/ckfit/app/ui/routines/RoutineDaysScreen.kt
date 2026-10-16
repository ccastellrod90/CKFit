package com.ckfit.app.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.data.db.dao.RoutineDayWithCount
import com.ckfit.app.ui.common.dayName
import com.ckfit.app.ui.components.CKFitTopBar
import com.ckfit.app.ui.components.ColorDot
import com.ckfit.app.ui.components.Pill
import com.ckfit.app.ui.components.TextInputDialog

/** Plan → 7 filas lunes–domingo. */
@Composable
fun RoutineDaysScreen(
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    viewModel: RoutineDaysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CKFitTopBar(
                title = state?.routine?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { renaming = true }, enabled = state != null) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.routines_rename))
                    }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    if (s.routine.isActive) {
                        Pill(stringResource(R.string.routines_active), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.routines_active_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedButton(onClick = viewModel::activate) { Text(stringResource(R.string.routines_activate)) }
                    }
                }
            }
            items(s.days, key = { it.day.id }) { day -> DayRow(day, onClick = { onOpenDay(day.day.id) }) }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.routines_rename),
            label = stringResource(R.string.routines_name_label),
            initial = state?.routine?.name.orEmpty(),
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }
}

@Composable
private fun DayRow(item: RoutineDayWithCount, onClick: () -> Unit) {
    val day = item.day
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(day.colorHex, size = 14.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = dayName(day.dayOfWeek),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = day.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!day.isRestDay) {
                    Text(
                        text = pluralStringResource(R.plurals.exercise_count, item.exerciseCount, item.exerciseCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                day.isRestDay -> Pill(stringResource(R.string.day_rest_pill), MaterialTheme.colorScheme.onSurfaceVariant)
                day.isOptional -> Pill(stringResource(R.string.home_today_optional), MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
