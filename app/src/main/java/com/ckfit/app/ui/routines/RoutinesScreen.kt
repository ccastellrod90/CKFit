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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.data.db.dao.RoutineSummary
import com.ckfit.app.data.seed.SeedParser
import com.ckfit.app.ui.components.CKFitTopBar
import com.ckfit.app.ui.components.Pill
import com.ckfit.app.ui.components.TextInputDialog

private sealed interface RoutineDialog {
    data object Create : RoutineDialog
    data class Rename(val id: Long, val name: String) : RoutineDialog
    data class Duplicate(val id: Long, val name: String) : RoutineDialog
}

@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onOpenRoutine: (Long) -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<RoutineDialog?>(null) }

    LaunchedEffect(Unit) { viewModel.opened.collect(onOpenRoutine) }

    Scaffold(
        topBar = { CKFitTopBar(stringResource(R.string.routines_title), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = RoutineDialog.Create },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.routines_new)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        val list = routines
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.routine.id }) { summary ->
                    RoutineCard(
                        summary = summary,
                        onOpen = { onOpenRoutine(summary.routine.id) },
                        onActivate = { viewModel.activate(summary.routine.id) },
                        onRename = { dialog = RoutineDialog.Rename(summary.routine.id, summary.routine.name) },
                        onDuplicate = { dialog = RoutineDialog.Duplicate(summary.routine.id, summary.routine.name) },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        RoutineDialog.Create -> {
            val dayNames = stringArrayResource(R.array.day_names).toList()
            TextInputDialog(
                title = stringResource(R.string.routines_new),
                label = stringResource(R.string.routines_name_label),
                initial = "",
                confirmLabel = stringResource(R.string.action_create),
                onDismiss = { dialog = null },
                onConfirm = {
                    viewModel.create(it, dayNames, SeedParser.REST_DAY_COLOR)
                    dialog = null
                },
            )
        }
        is RoutineDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.routines_rename),
            label = stringResource(R.string.routines_name_label),
            initial = d.name,
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.rename(d.id, it)
                dialog = null
            },
        )
        is RoutineDialog.Duplicate -> TextInputDialog(
            title = stringResource(R.string.routines_duplicate),
            label = stringResource(R.string.routines_name_label),
            initial = stringResource(R.string.routines_copy_name, d.name),
            confirmLabel = stringResource(R.string.routines_duplicate),
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.duplicate(d.id, it)
                dialog = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun RoutineCard(
    summary: RoutineSummary,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary.routine.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (summary.routine.isActive) {
                        Spacer(Modifier.width(10.dp))
                        Pill(stringResource(R.string.routines_active), MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = stringResource(
                        R.string.home_routines_days,
                        pluralStringResource(R.plurals.training_day_count, summary.trainingDays, summary.trainingDays),
                        pluralStringResource(R.plurals.optional_day_count, summary.optionalDays, summary.optionalDays),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!summary.routine.isActive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routines_activate)) },
                            onClick = { menu = false; onActivate() },
                        )
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.routines_rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.routines_duplicate)) }, onClick = { menu = false; onDuplicate() })
                }
            }
        }
    }
}
