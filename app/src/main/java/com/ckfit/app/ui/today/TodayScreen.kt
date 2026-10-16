package com.ckfit.app.ui.today

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.ui.common.dayName
import com.ckfit.app.ui.common.formatDuration
import com.ckfit.app.ui.common.formatPrescription
import com.ckfit.app.ui.components.CKFitTopBar
import com.ckfit.app.ui.components.ColorDot
import com.ckfit.app.ui.components.LoadTypeChip
import com.ckfit.app.ui.components.Pill
import com.ckfit.app.ui.components.TextInputDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TodayScreen(
    onBack: () -> Unit,
    onOpenExercise: (id: Long, autoStart: Boolean) -> Unit,
    onSessionClosed: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var confirmSkip by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it == TodayEvent.SessionClosed) onSessionClosed() }
    }

    Scaffold(
        topBar = {
            CKFitTopBar(
                title = stringResource(R.string.today_title),
                onBack = onBack,
                actions = {
                    if (state is TodayUiState.Active) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_choose_other_session)) },
                                    onClick = { menu = false; showOptions = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_skip_session)) },
                                    onClick = { menu = false; confirmSkip = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            TodayUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            TodayUiState.NoSession -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.today_no_session),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is TodayUiState.Active -> TodayContent(
                state = s,
                onOpenExercise = { onOpenExercise(it, false) },
                onStart = { onOpenExercise(it, true) },
                onReorder = viewModel::reorder,
                onSkipExercise = viewModel::skipExercise,
                onRestoreExercise = viewModel::restoreExercise,
                onFinish = { confirmFinish = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    val active = state as? TodayUiState.Active
    if (showOptions && active != null) {
        SessionOptionsSheet(
            options = active.options,
            onDismiss = { showOptions = false },
            onPick = {
                showOptions = false
                viewModel.switchTo(it)
            },
        )
    }
    if (confirmSkip) {
        TextInputDialog(
            title = stringResource(R.string.skip_session_title),
            label = stringResource(R.string.skip_session_reason),
            initial = "",
            confirmLabel = stringResource(R.string.action_skip_session),
            allowEmpty = true,
            onDismiss = { confirmSkip = false },
            onConfirm = {
                confirmSkip = false
                viewModel.skipSession(it)
            },
        )
    }
    if (confirmFinish && active != null) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.finish_workout_title)) },
            text = {
                Text(
                    stringResource(
                        if (active.allFinished) R.string.finish_workout_text_done else R.string.finish_workout_text_pending,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    viewModel.finish()
                }) { Text(stringResource(R.string.action_finish_workout)) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState.Active,
    onOpenExercise: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onSkipExercise: (Long) -> Unit,
    onRestoreExercise: (Long) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Copia local para que el arrastre sea fluido; se sincroniza con la BD cuando no se está arrastrando.
    var items by remember { mutableStateOf(state.items) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.items) { if (!dragging) items = state.items }

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = items.indexOfFirst { it.id == from.key }
        val toIndex = items.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") { SessionHeader(state) }
        // "Comenzar" arranca el primero de la lista que quede por hacer; si ya hay uno en curso, lo retoma.
        items.firstOrNull { !it.finished && it.detail.dayExercise != null }?.let { first ->
            item(key = "start") {
                val inProgress = first.status == SessionExerciseStatus.IN_PROGRESS
                Button(
                    onClick = { if (inProgress) onOpenExercise(first.id) else onStart(first.id) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        stringResource(
                            if (inProgress) R.string.today_continue_exercise else R.string.today_start_exercise,
                            first.detail.exercise?.name.orEmpty(),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        items(items, key = { it.id }) { item ->
            ReorderableItem(reorderState, key = item.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                Surface(shadowElevation = elevation, shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
                    ExerciseRow(
                        item = item,
                        unit = state.unit,
                        onClick = { onOpenExercise(item.id) },
                        onSkip = { onSkipExercise(item.id) },
                        onRestore = { onRestoreExercise(item.id) },
                        handle = {
                            IconButton(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        dragging = true
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        dragging = false
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        onReorder(items.map { it.id })
                                    },
                                ),
                                onClick = {},
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = stringResource(R.string.action_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
        item(key = "finish") {
            val modifierButton = Modifier.fillMaxWidth().height(52.dp)
            if (state.allFinished) {
                Button(onClick = onFinish, modifier = modifierButton) {
                    Text(stringResource(R.string.action_finish_workout), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = onFinish, modifier = modifierButton) {
                    Text(stringResource(R.string.action_finish_workout))
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(state: TodayUiState.Active) {
    val done = state.items.count { it.finished }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(state.colorHex)
                Spacer(Modifier.width(10.dp))
                Text(state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.today_time_summary, formatDuration(state.totalSec), formatDuration(state.remainingSec)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { if (state.items.isEmpty()) 0f else done.toFloat() / state.items.size },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.fraction, done, state.items.size), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    item: TodayItem,
    unit: WeightUnit,
    onClick: () -> Unit,
    onSkip: () -> Unit,
    onRestore: () -> Unit,
    handle: @Composable () -> Unit,
) {
    val prescription = item.detail.dayExercise
    val exercise = item.detail.exercise
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        enabled = prescription != null && exercise != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            handle()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = exercise?.name ?: stringResource(R.string.today_exercise_removed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (prescription?.isIntensitySet == true) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.intensity_mark), color = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (prescription != null && exercise != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadTypeChip(prescription.loadType)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatPrescription(prescription, exercise, unit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(item.status)
                    if (prescription != null && item.setsDone > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.today_sets_done, item.setsDone, prescription.sets),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (item.status == SessionExerciseStatus.SKIPPED || item.status == SessionExerciseStatus.DONE) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_mark_pending)) },
                            onClick = { menu = false; onRestore() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_skip_exercise)) },
                            onClick = { menu = false; onSkip() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: SessionExerciseStatus) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when (status) {
        SessionExerciseStatus.PENDING -> R.string.exercise_status_pending to scheme.onSurfaceVariant
        SessionExerciseStatus.IN_PROGRESS -> R.string.exercise_status_in_progress to scheme.secondary
        SessionExerciseStatus.DONE -> R.string.exercise_status_done to scheme.primary
        SessionExerciseStatus.SKIPPED -> R.string.exercise_status_skipped to scheme.onSurfaceVariant
    }
    Pill(stringResource(label), color)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionOptionsSheet(
    options: List<SessionOption>,
    onDismiss: () -> Unit,
    onPick: (SessionOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            item {
                Text(
                    stringResource(R.string.action_choose_other_session),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    stringResource(R.string.today_switch_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            items(options, key = { "${it.plannedId}-${it.routineDayId}" }) { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(option) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(option.colorHex)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            dayName(option.dayOfWeek),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(option.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (option.plannedId == null) {
                        Pill(stringResource(R.string.today_extra_session), MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Pill(stringResource(R.string.planned_status_pending), MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
