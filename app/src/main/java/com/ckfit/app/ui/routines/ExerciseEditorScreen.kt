package com.ckfit.app.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.prescription.PrescriptionError
import com.ckfit.app.ui.common.unitLabel
import com.ckfit.app.ui.components.CKFitTopBar

@Composable
fun ExerciseEditorScreen(
    onDone: () -> Unit,
    viewModel: ExerciseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }

    Scaffold(
        topBar = {
            CKFitTopBar(
                title = stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit),
                onBack = onDone,
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        EditorForm(
            state = state,
            onFields = viewModel::onFieldsChange,
            onLoadType = viewModel::onLoadTypeChange,
            onIntensity = viewModel::onIntensityChange,
            onSave = viewModel::save,
            onRemove = viewModel::remove,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun EditorForm(
    state: ExerciseEditorUiState,
    onFields: ((EditorFields) -> EditorFields) -> Unit,
    onLoadType: (LoadType) -> Unit,
    onIntensity: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = state.fields
    val err: (PrescriptionError) -> Boolean = { state.showErrors && it in state.errors }
    val unit = unitLabel(state.unit)

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(state.exerciseName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(state.muscleGroup, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section(stringResource(R.string.editor_load_type)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadType.entries.forEach { type ->
                    FilterChip(
                        selected = f.loadType == type,
                        onClick = { onLoadType(type) },
                        label = { Text(stringResource(loadTypeLabel(type))) },
                    )
                }
            }
            Text(
                stringResource(R.string.editor_load_type_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(stringResource(R.string.editor_measure)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MeasureType.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = f.measureType == t,
                        onClick = { onFields { it.copy(measureType = t) } },
                        shape = SegmentedButtonDefaults.itemShape(index, MeasureType.entries.size),
                    ) { Text(stringResource(if (t == MeasureType.REPS) R.string.measure_reps else R.string.measure_time)) }
                }
            }
            SwitchLine(stringResource(R.string.per_side), f.perSide) { v -> onFields { it.copy(perSide = v) } }
            SwitchLine(stringResource(R.string.uses_weight), f.usesWeight) { v -> onFields { it.copy(usesWeight = v) } }
            if (state.otherUsages > 0) {
                Text(
                    text = pluralStringResource(R.plurals.editor_shared_exercise, state.otherUsages, state.otherUsages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Section(stringResource(R.string.editor_prescription)) {
            NumberField(stringResource(R.string.editor_sets), f.sets, err(PrescriptionError.SETS), R.string.editor_error_sets) { v ->
                onFields { it.copy(sets = v) }
            }
            if (f.measureType == MeasureType.REPS) {
                val repsError = err(PrescriptionError.REPS_MISSING) || err(PrescriptionError.REPS_RANGE)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(stringResource(R.string.editor_reps_min), f.repsMin, repsError, null, Modifier.weight(1f)) { v ->
                        onFields { it.copy(repsMin = v) }
                    }
                    NumberField(stringResource(R.string.editor_reps_max), f.repsMax, repsError, null, Modifier.weight(1f)) { v ->
                        onFields { it.copy(repsMax = v) }
                    }
                }
                if (repsError) ErrorText(R.string.editor_error_reps)
            } else {
                NumberField(
                    stringResource(R.string.editor_duration),
                    f.durationSec,
                    err(PrescriptionError.DURATION),
                    R.string.editor_error_duration,
                    suffix = stringResource(R.string.unit_seconds),
                ) { v -> onFields { it.copy(durationSec = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    stringResource(R.string.editor_rest),
                    f.restSec,
                    err(PrescriptionError.REST),
                    null,
                    Modifier.weight(1f),
                    suffix = stringResource(R.string.unit_seconds),
                ) { v -> onFields { it.copy(restSec = v) } }
                NumberField(
                    stringResource(R.string.editor_rest_max),
                    f.restMaxSec,
                    err(PrescriptionError.REST_MAX),
                    null,
                    Modifier.weight(1f),
                    suffix = stringResource(R.string.unit_seconds),
                ) { v -> onFields { it.copy(restMaxSec = v) } }
            }
            if (err(PrescriptionError.REST) || err(PrescriptionError.REST_MAX)) ErrorText(R.string.editor_error_rest)
        }

        if (f.usesWeight) Section(stringResource(R.string.editor_weight_section)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    stringResource(R.string.editor_weight),
                    f.weight,
                    err(PrescriptionError.WEIGHT),
                    null,
                    Modifier.weight(1f),
                    decimal = true,
                    suffix = unit,
                ) { v -> onFields { it.copy(weight = v) } }
                NumberField(
                    stringResource(R.string.editor_weight_step),
                    f.weightStep,
                    err(PrescriptionError.WEIGHT_STEP),
                    null,
                    Modifier.weight(1f),
                    decimal = true,
                    suffix = unit,
                ) { v -> onFields { it.copy(weightStep = v) } }
            }
            if (err(PrescriptionError.WEIGHT) || err(PrescriptionError.WEIGHT_STEP)) ErrorText(R.string.editor_error_weight)
            Text(
                stringResource(R.string.editor_weight_shared_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(stringResource(R.string.editor_extra)) {
            SwitchLine(stringResource(R.string.editor_intensity), f.isIntensitySet, onIntensity)
            if (f.isIntensitySet) {
                OutlinedTextField(
                    value = f.intensityNote,
                    onValueChange = { v -> onFields { it.copy(intensityNote = v) } },
                    label = { Text(stringResource(R.string.editor_intensity_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = f.notes,
                onValueChange = { v -> onFields { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.editor_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (!state.isNew) {
            TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.editor_remove_from_day), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun loadTypeLabel(type: LoadType) = when (type) {
    LoadType.STRENGTH -> R.string.load_strength
    LoadType.VOLUME -> R.string.load_volume
    LoadType.CORE -> R.string.load_core
    LoadType.CARDIO -> R.string.load_cardio
    LoadType.OTHER -> R.string.load_other
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ErrorText(res: Int) {
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    isError: Boolean,
    errorRes: Int?,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    suffix: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(7)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = isError,
        supportingText = if (isError && errorRes != null) {
            { Text(stringResource(errorRes)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}
