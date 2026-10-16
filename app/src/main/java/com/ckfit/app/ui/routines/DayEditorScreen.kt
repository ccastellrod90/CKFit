package com.ckfit.app.ui.routines

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.data.db.dao.DayExerciseWithExercise
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.ui.common.DayColorPresets
import com.ckfit.app.ui.common.dayName
import com.ckfit.app.ui.common.formatPrescription
import com.ckfit.app.ui.common.parseHexColor
import com.ckfit.app.ui.components.CKFitTopBar
import com.ckfit.app.ui.components.LoadTypeChip
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DayEditorScreen(
    onBack: () -> Unit,
    onOpenPrescription: (Long) -> Unit,
    onNewPrescription: (dayId: Long, exerciseId: Long) -> Unit,
    viewModel: DayEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DayEditorEvent.OpenNewPrescription -> {
                    showPicker = false
                    onNewPrescription(event.dayId, event.exerciseId)
                }
                is DayEditorEvent.Archived -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.day_exercise_removed, event.name),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.dayExerciseId)
                }
            }
        }
    }

    val s = state
    Scaffold(
        topBar = { CKFitTopBar(s?.let { dayName(it.day.dayOfWeek) }.orEmpty(), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        DayEditorContent(
            state = s,
            onTitleChange = viewModel::onTitleChange,
            onColorChange = viewModel::onColorChange,
            onOptionalChange = viewModel::onOptionalChange,
            onRestDayChange = { value ->
                viewModel.onRestDayChange(value, context.resources.getStringArray(R.array.day_names)[s.day.dayOfWeek - 1], context.getString(R.string.day_rest_title))
            },
            onReorder = viewModel::reorder,
            onOpen = { onOpenPrescription(it.dayExercise.id) },
            onArchive = viewModel::archive,
            onAdd = { showPicker = true },
            modifier = Modifier.padding(padding),
        )
    }

    if (showPicker) {
        val catalog by viewModel.catalog.collectAsStateWithLifecycle()
        val query by viewModel.searchQuery.collectAsStateWithLifecycle()
        ExercisePickerSheet(
            catalog = catalog,
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onPick = viewModel::pick,
            onCreate = viewModel::createExercise,
            onDismiss = {
                showPicker = false
                viewModel.onQueryChange("")
            },
        )
    }
}

@Composable
private fun DayEditorContent(
    state: DayEditorUiState,
    onTitleChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onOptionalChange: (Boolean) -> Unit,
    onRestDayChange: (Boolean) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onOpen: (DayExerciseWithExercise) -> Unit,
    onArchive: (DayExerciseWithExercise) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = state.day
    var title by rememberSaveable(day.id) { mutableStateOf(day.title) }
    // Lo que el campo ya mandó a guardar (el guardado va con retraso y puede llegar mientras se escribe).
    val typedTitles = remember(day.id) { mutableSetOf(day.title) }
    // El título puede cambiar desde fuera (p. ej. al pasar de descanso a entrenamiento).
    LaunchedEffect(day.title) { if (day.title !in typedTitles) title = day.title }
    // Copia local para que el arrastre sea fluido; se sincroniza con la BD cuando no se está arrastrando.
    var items by remember { mutableStateOf(state.exercises) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.exercises) { if (!dragging) items = state.exercises }

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = items.indexOfFirst { it.dayExercise.id == from.key }
        val toIndex = items.indexOfFirst { it.dayExercise.id == to.key }
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
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        typedTitles += it.trim()
                        onTitleChange(it)
                    },
                    label = { Text(stringResource(R.string.day_title_label)) },
                    isError = title.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorPresetRow(selected = day.colorHex, onSelect = onColorChange)
                SwitchRow(stringResource(R.string.day_rest_label), day.isRestDay, onRestDayChange)
                if (!day.isRestDay) {
                    SwitchRow(stringResource(R.string.day_optional_label), day.isOptional, onOptionalChange)
                }
                Text(
                    text = stringResource(if (day.isRestDay) R.string.day_rest_hint else R.string.day_exercises_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(items, key = { it.dayExercise.id }) { item ->
            ReorderableItem(reorderState, key = item.dayExercise.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                Surface(shadowElevation = elevation, shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
                    DayExerciseRow(
                        item = item,
                        unit = state.unit,
                        onClick = { onOpen(item) },
                        onArchive = { onArchive(item) },
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
                                        onReorder(items.map { it.dayExercise.id })
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
        item(key = "add") {
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.day_add_exercise), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun DayExerciseRow(
    item: DayExerciseWithExercise,
    unit: WeightUnit,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    handle: @Composable () -> Unit,
) {
    val p = item.dayExercise
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            handle()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (p.isIntensitySet) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.intensity_mark), color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadTypeChip(p.loadType)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatPrescription(p, item.exercise, unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onArchive) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorPresetRow(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.day_color_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DayColorPresets.forEach { hex ->
                val isSelected = hex.equals(selected, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(parseHexColor(hex, Color.Gray), CircleShape)
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                        )
                        .clickable { onSelect(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
