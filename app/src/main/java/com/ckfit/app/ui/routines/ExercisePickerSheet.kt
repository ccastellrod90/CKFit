package com.ckfit.app.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ckfit.app.R
import com.ckfit.app.data.db.entity.ExerciseEntity
import com.ckfit.app.domain.model.MeasureType

/** "Agregar ejercicio": buscador del catálogo o crear uno nuevo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    catalog: List<ExerciseEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (ExerciseEntity) -> Unit,
    onCreate: (name: String, group: String, type: MeasureType, perSide: Boolean, usesWeight: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.picker_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.padding(top = 8.dp)) {
                if (query.isNotBlank() && !catalog.hasExactName(query)) {
                    item(key = "create") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { creating = query.trim() }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.picker_create, query.trim()),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                if (query.isBlank()) {
                    item(key = "create-empty") {
                        TextButton(onClick = { creating = "" }, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.picker_create_new), color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                items(catalog, key = { it.id }) { exercise ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(exercise) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = listOfNotNull(
                                exercise.muscleGroup,
                                stringResource(if (exercise.measureType == MeasureType.REPS) R.string.measure_reps else R.string.measure_time),
                                if (exercise.perSide) stringResource(R.string.per_side) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }

    creating?.let { initialName ->
        NewExerciseDialog(
            initialName = initialName,
            onDismiss = { creating = null },
            onConfirm = { name, group, type, perSide, usesWeight ->
                creating = null
                onCreate(name, group, type, perSide, usesWeight)
            },
        )
    }
}

@Composable
private fun NewExerciseDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, MeasureType, Boolean, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var group by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(MeasureType.REPS) }
    var perSide by rememberSaveable { mutableStateOf(false) }
    var usesWeight by rememberSaveable { mutableStateOf(true) }
    val defaultGroup = stringResource(R.string.new_exercise_group_default)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_exercise_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_exercise_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text(stringResource(R.string.new_exercise_group)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MeasureType.entries.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(index, MeasureType.entries.size),
                        ) { Text(stringResource(if (t == MeasureType.REPS) R.string.measure_reps else R.string.measure_time)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.per_side), modifier = Modifier.weight(1f))
                    Switch(checked = perSide, onCheckedChange = { perSide = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.uses_weight), modifier = Modifier.weight(1f))
                    Switch(checked = usesWeight, onCheckedChange = { usesWeight = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, group.ifBlank { defaultGroup }, type, perSide, usesWeight) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
