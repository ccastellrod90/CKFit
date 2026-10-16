package com.ckfit.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.data.repo.TrainingSettings
import com.ckfit.app.domain.model.Sex
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.ui.common.unitLabel
import com.ckfit.app.ui.components.CKFitTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val training by viewModel.training.collectAsStateWithLifecycle()
    val audio by viewModel.audio.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val lastHeard by viewModel.lastHeard.collectAsStateWithLifecycle()
    val micTest by viewModel.micTest.collectAsStateWithLifecycle()
    val ttsStatus by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Contactos solo se pide al tocar "Tomar del teléfono".
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.importNameFromPhone()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.PhoneNameNotFound -> snackbar.showSnackbar(context.getString(R.string.settings_name_not_found))
            }
        }
    }

    Scaffold(
        topBar = { CKFitTopBar(stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (profile.loaded) {
                ProfileSection(
                    state = profile,
                    onNameChange = viewModel::onNameChange,
                    onImportName = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                    onHeightChange = viewModel::onHeightChange,
                    onBirthDateChange = viewModel::onBirthDateChange,
                    onSexChange = viewModel::onSexChange,
                    onUnitChange = viewModel::onUnitChange,
                )
                TrainingSection(
                    settings = training,
                    onAutoAdvanceChange = viewModel::onAutoAdvanceChange,
                    onSwitchSideSecChange = viewModel::onSwitchSideSecChange,
                )
                VoiceSection(
                    audio = audio,
                    status = voiceStatus,
                    lastHeard = lastHeard,
                    micTest = micTest,
                    micTestAvailable = viewModel.micTestAvailable,
                    onChange = viewModel::updateAudio,
                    onMicTest = viewModel::setMicTest,
                )
                AudioSection(
                    audio = audio,
                    ttsStatus = ttsStatus,
                    onChange = viewModel::updateAudio,
                    onTestVoice = viewModel::testVoice,
                )
                CountdownSection(
                    training = training,
                    onRestCountdown = viewModel::onRestCountdownChange,
                    onPreCountdown = viewModel::onPreCountdownChange,
                    onHalfway = viewModel::onHalfwayCueChange,
                )
                BatterySection()
            }
        }
    }
}

@Composable
private fun ProfileSection(
    state: ProfileUiState,
    onNameChange: (String) -> Unit,
    onImportName: () -> Unit,
    onHeightChange: (String) -> Unit,
    onBirthDateChange: (LocalDate?) -> Unit,
    onSexChange: (Sex?) -> Unit,
    onUnitChange: (WeightUnit) -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_profile_section), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.nameText,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.settings_name_label)) },
                supportingText = { Text(stringResource(R.string.settings_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onImportName) {
                Text(stringResource(R.string.settings_name_from_phone), color = MaterialTheme.colorScheme.secondary)
            }

            OutlinedTextField(
                value = state.heightText,
                onValueChange = onHeightChange,
                label = { Text(stringResource(R.string.settings_height_label)) },
                suffix = { Text(stringResource(R.string.unit_cm)) },
                isError = state.heightError,
                supportingText = if (state.heightError) {
                    { Text(stringResource(R.string.settings_height_error)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.settings_birth_date_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        text = state.birthDate
                            ?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.forLanguageTag("es")).format(it) }
                            ?: stringResource(R.string.settings_birth_date_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                state.age?.let {
                    Text(
                        stringResource(R.string.settings_age, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_sex_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val options = listOf(
                    null to R.string.settings_sex_unspecified,
                    Sex.MALE to R.string.settings_sex_male,
                    Sex.FEMALE to R.string.settings_sex_female,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.sex == value,
                            onClick = { onSexChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_unit_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val units = WeightUnit.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    units.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = state.unit == unit,
                            onClick = { onUnitChange(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index, units.size),
                        ) { Text(unitLabel(unit)) }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initial = state.birthDate,
            today = state.today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                onBirthDateChange(it)
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initial: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?) -> Unit,
) {
    // DatePicker trabaja con milisegundos UTC a medianoche.
    val todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val selectable = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        yearRange = 1920..today.year,
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    },
                )
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun TrainingSection(
    settings: TrainingSettings,
    onAutoAdvanceChange: (Boolean) -> Unit,
    onSwitchSideSecChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_training_section), style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_next_set_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val modes = listOf(true to R.string.mode_auto, false to R.string.mode_manual)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (auto, label) ->
                        SegmentedButton(
                            selected = settings.autoAdvance == auto,
                            onClick = { onAutoAdvanceChange(auto) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(stringResource(label)) }
                    }
                }
                Text(
                    stringResource(if (settings.autoAdvance) R.string.settings_next_set_auto_hint else R.string.settings_next_set_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_switch_side_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSwitchSideSecChange(settings.switchSideSec - 1) }, enabled = settings.switchSideSec > 0) {
                    Text(stringResource(R.string.minus_sign), style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.seconds_value, settings.switchSideSec),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { onSwitchSideSecChange(settings.switchSideSec + 1) }, enabled = settings.switchSideSec < 60) {
                    Text(stringResource(R.string.plus_sign), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
