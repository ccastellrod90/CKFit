package com.ckfit.app.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.domain.engine.EngineEvent
import com.ckfit.app.data.db.dao.SessionExerciseDetail
import com.ckfit.app.domain.engine.EngineState
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.Sex
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.Side
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.model.kgTo
import com.ckfit.app.service.ActiveExercise
import com.ckfit.app.service.VoiceStatus
import com.ckfit.app.service.WorkoutState
import com.ckfit.app.ui.common.formatDurationShort
import com.ckfit.app.ui.common.formatNumber
import com.ckfit.app.ui.common.formatRange
import com.ckfit.app.ui.common.unitLabel
import com.ckfit.app.ui.components.CKFitTopBar
import com.ckfit.app.ui.components.LoadTypeChip
import com.ckfit.app.ui.components.Pill
import com.ckfit.app.ui.components.ProgressRing
import com.ckfit.app.ui.components.TextInputDialog
import com.ckfit.app.ui.components.VoiceHelpSheet
import com.ckfit.app.ui.theme.TimerTextStyle
import kotlin.math.ceil

@Composable
fun ExerciseFocusScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onSessionClosed: () -> Unit,
    viewModel: ExerciseFocusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var chooseOther by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showVoiceHelp by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()
    val ready = state as? FocusUiState.Ready
    AskWorkoutPermissionsOnce(askMicrophone = ready?.voiceEnabled == true, onMicrophoneGranted = viewModel::onMicrophoneGranted)

    LaunchedEffect(Unit) {
        viewModel.events.collect {
            when (it) {
                is FocusEvent.OpenExercise -> onOpenExercise(it.sessionExerciseId)
                FocusEvent.SessionClosed -> onSessionClosed()
                is FocusEvent.WeightBelowReference -> {
                    val phrases = context.resources.getStringArray(R.array.weight_down_phrases)
                    val ending = context.getString(
                        when (it.sex) {
                            Sex.MALE -> R.string.gender_ending_male
                            Sex.FEMALE -> R.string.gender_ending_female
                            null -> R.string.gender_ending_neutral
                        },
                    )
                    snackbar.showSnackbar(phrases[it.phraseIndex.mod(phrases.size)].replace(GENDER_PLACEHOLDER, ending))
                }
            }
        }
    }

    val resting = ready?.workout?.engine.let { it is EngineState.Resting || (it as? EngineState.Paused)?.previous is EngineState.Resting }
    // Fondo turquesa tenue durante el descanso (sección 2: AccentSoft), también detrás de la barra superior.
    val background by animateColorAsState(
        if (resting) {
            MaterialTheme.colorScheme.secondaryContainer.compositeOver(MaterialTheme.colorScheme.background)
        } else {
            MaterialTheme.colorScheme.background
        },
        label = "background",
    )

    Scaffold(
        containerColor = background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CKFitTopBar(
                title = "",
                onBack = onBack,
                containerColor = Color.Transparent,
                actions = {
                    ready?.let { MicIndicator(it.voice, it.voiceEnabled, onClick = { showVoiceHelp = true }) }
                    if (ready != null) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (ready.others.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.focus_choose_other)) },
                                        onClick = { menu = false; chooseOther = true },
                                    )
                                }
                                if (ready.workout.engine != EngineState.ExerciseDone) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.focus_end_exercise)) },
                                        onClick = { menu = false; confirmEnd = true },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.voice_help_title)) },
                                    onClick = { menu = false; showVoiceHelp = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                FocusUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is FocusUiState.Ready -> FocusContent(
                    workout = s.workout,
                    unit = s.unit,
                    hasOthers = s.others.isNotEmpty(),
                    onEvent = viewModel::send,
                    onAutoAdvance = viewModel::setAutoAdvance,
                    onStepWeight = viewModel::stepWeight,
                    onSetWeight = { viewModel.setWeight(it, s.unit) },
                    onNext = viewModel::openNext,
                    onChooseOther = { chooseOther = true },
                    onFinish = viewModel::finishSession,
                )
            }
        }
    }

    if (confirmEnd && ready != null) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.focus_end_exercise_title)) },
            text = { Text(stringResource(R.string.focus_end_exercise_text, ready.workout.exercise.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    viewModel.endExercise()
                }) { Text(stringResource(R.string.focus_end_exercise)) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showVoiceHelp) VoiceHelpSheet(onDismiss = { showVoiceHelp = false })
    if (chooseOther && ready != null) {
        OtherExercisesSheet(
            items = ready.others,
            onDismiss = { chooseOther = false },
            onPick = {
                chooseOther = false
                viewModel.open(it)
            },
        )
    }
}

/** Indicador de micrófono: escuchando / apagado; oculto sin permiso o con la voz desactivada. */
@Composable
private fun MicIndicator(status: VoiceStatus, enabled: Boolean, onClick: () -> Unit) {
    if (!enabled || status == VoiceStatus.NO_PERMISSION) return
    val listening = status == VoiceStatus.LISTENING
    // Tocar el micrófono abre la ayuda con los comandos.
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (listening) R.drawable.ic_mic else R.drawable.ic_mic_off),
            contentDescription = stringResource(if (listening) R.string.voice_listening else R.string.voice_off),
            tint = if (listening) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherExercisesSheet(items: List<SessionExerciseDetail>, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.focus_choose_other),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(item.item.id) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.dayExercise?.let { LoadTypeChip(it.loadType) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.exercise?.name.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.item.status == SessionExerciseStatus.IN_PROGRESS) {
                        Pill(stringResource(R.string.exercise_status_in_progress), MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusContent(
    workout: WorkoutState,
    unit: WeightUnit,
    hasOthers: Boolean,
    onEvent: (EngineEvent) -> Unit,
    onAutoAdvance: (Boolean) -> Unit,
    onStepWeight: (Int) -> Unit,
    onSetWeight: (String) -> Unit,
    onNext: () -> Unit,
    onChooseOther: () -> Unit,
    onFinish: () -> Unit,
) {
    val ex = workout.exercise
    val engine = workout.engine
    val shown = (engine as? EngineState.Paused)?.previous ?: engine
    val paused = engine is EngineState.Paused

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(ex, shown)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (engine == EngineState.ExerciseDone) {
                DonePanel(ex, hasOthers, onNext, onChooseOther, onFinish)
            } else {
                PhasePanel(shown, ex, paused)
            }
        }
        if (engine != EngineState.ExerciseDone) {
            // Sin peso en descanso, cambio de lado o cuenta previa, ni en ejercicios de peso corporal.
            val showWeight = ex.usesWeight &&
                (shown is EngineState.Waiting || shown is EngineState.WorkingReps || shown is EngineState.WorkingTimed)
            if (showWeight) {
                WeightRow(ex, unit, onStepWeight, onSetWeight)
                Spacer(Modifier.height(16.dp))
            }
            Controls(shown, paused, onEvent)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onAutoAdvance(!workout.autoAdvance) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_next_set_label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (workout.autoAdvance) R.string.mode_auto else R.string.mode_manual),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = workout.autoAdvance, onCheckedChange = onAutoAdvance)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(ex: ActiveExercise, shown: EngineState) {
    val set = when (shown) {
        is EngineState.Waiting -> shown.set
        is EngineState.WorkingReps -> shown.set
        is EngineState.WorkingTimed -> shown.set
        is EngineState.SwitchSide -> shown.set
        is EngineState.Resting -> shown.nextSet
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            ex.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadTypeChip(ex.loadType)
            Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(
                    set?.let { stringResource(R.string.focus_set_of, it, ex.sets) },
                    target(ex),
                ).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // La serie ◆ es la última: se recuerda la técnica justo antes.
        if (ex.isIntensitySet && set == ex.sets && !ex.intensityNote.isNullOrBlank()) {
            Text(
                stringResource(R.string.intensity_mark) + " " + ex.intensityNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun target(ex: ActiveExercise): String {
    val base = when (ex.measureType) {
        MeasureType.REPS -> stringResource(R.string.focus_target_reps, formatRange(ex.repsMin, ex.repsMax))
        MeasureType.TIME -> formatDurationShort(ex.durationSec ?: 0)
    }
    return if (ex.perSide) base + " " + stringResource(R.string.per_side_short) else base
}

@Composable
private fun PhasePanel(shown: EngineState, ex: ActiveExercise, paused: Boolean) {
    val accent = MaterialTheme.colorScheme.secondary
    val primary = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (paused) PhaseLabel(stringResource(R.string.focus_paused), MaterialTheme.colorScheme.onSurfaceVariant)
        when (shown) {
            is EngineState.Waiting -> {
                PhaseLabel(stringResource(R.string.focus_waiting), MaterialTheme.colorScheme.onSurfaceVariant)
                BigText(stringResource(R.string.focus_set_number, shown.set), MaterialTheme.colorScheme.onSurface, 64)
            }
            is EngineState.PreCountdown -> {
                PhaseLabel(stringResource(R.string.focus_get_ready), accent)
                BigText(shown.remaining.toString(), accent, 140)
            }
            is EngineState.WorkingReps -> {
                PhaseLabel(workingLabel(shown.side), primary)
                BigText(clock(shown.elapsedMs, roundUp = false), MaterialTheme.colorScheme.onSurface, 96)
            }
            is EngineState.WorkingTimed -> {
                PhaseLabel(workingLabel(shown.side), primary)
                val total = (ex.durationSec ?: 1) * 1000L
                ProgressRing(progress = shown.remainingMs.toFloat() / total, size = 260.dp, strokeWidth = 16.dp, color = primary) {
                    BigText(clock(shown.remainingMs, roundUp = true), MaterialTheme.colorScheme.onSurface, 72)
                }
            }
            is EngineState.SwitchSide -> {
                PhaseLabel(stringResource(R.string.focus_switch_side), accent)
                BigText(ceilSec(shown.remainingMs).toString(), accent, 140)
            }
            is EngineState.Resting -> {
                PhaseLabel(stringResource(R.string.focus_resting), accent)
                ProgressRing(
                    progress = shown.remainingMs.toFloat() / shown.totalMs.coerceAtLeast(1),
                    size = 260.dp,
                    strokeWidth = 16.dp,
                    color = accent,
                ) {
                    BigText(clock(shown.remainingMs, roundUp = true), MaterialTheme.colorScheme.onSurface, 72)
                }
                Text(
                    stringResource(R.string.focus_next_set, shown.nextSet),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EngineState.ExerciseDone, is EngineState.Paused -> Unit
        }
    }
}

@Composable
private fun workingLabel(side: Side?): String = when (side) {
    Side.LEFT -> stringResource(R.string.focus_working_left)
    Side.RIGHT -> stringResource(R.string.focus_working_right)
    null -> stringResource(R.string.focus_working)
}

@Composable
private fun DonePanel(
    ex: ActiveExercise,
    hasOthers: Boolean,
    onNext: () -> Unit,
    onChooseOther: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BigText(stringResource(R.string.focus_exercise_done), MaterialTheme.colorScheme.primary, 44)
        if (ex.nextName != null) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text(
                    stringResource(R.string.focus_next_exercise, ex.nextName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (hasOthers) {
                OutlinedButton(onClick = onChooseOther, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.focus_choose_other))
                }
            }
            TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_finish_workout), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                stringResource(R.string.focus_all_done),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text(stringResource(R.string.action_finish_workout), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WeightRow(ex: ActiveExercise, unit: WeightUnit, onStep: (Int) -> Unit, onSet: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val label = unitLabel(unit)
    val text = ex.weightKg?.let { formatNumber(it.kgTo(unit)) + " " + label } ?: stringResource(R.string.focus_no_weight)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = { onStep(-1) }, enabled = (ex.weightKg ?: 0.0) > 0.0, modifier = Modifier.size(56.dp)) {
            Text(stringResource(R.string.minus_sign), style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clickable { editing = true }.padding(vertical = 8.dp),
        )
        FilledTonalButton(onClick = { onStep(1) }, modifier = Modifier.size(56.dp)) {
            Text(stringResource(R.string.plus_sign), style = MaterialTheme.typography.headlineSmall)
        }
    }
    if (editing) {
        TextInputDialog(
            title = stringResource(R.string.focus_weight_title),
            label = stringResource(R.string.editor_weight),
            initial = ex.weightKg?.let { formatNumber(it.kgTo(unit)) }.orEmpty(),
            confirmLabel = stringResource(R.string.action_save),
            allowEmpty = true,
            keyboardType = KeyboardType.Decimal,
            suffix = label,
            onDismiss = { editing = false },
            onConfirm = {
                editing = false
                onSet(it)
            },
        )
    }
}

@Composable
private fun Controls(shown: EngineState, paused: Boolean, onEvent: (EngineEvent) -> Unit) {
    val (primaryLabel, primaryEvent) = when (shown) {
        is EngineState.Waiting -> R.string.action_start_set to EngineEvent.Start
        is EngineState.WorkingReps, is EngineState.WorkingTimed -> R.string.action_done_set to EngineEvent.Done
        else -> null to null
    }
    Button(
        onClick = { primaryEvent?.let(onEvent) },
        enabled = primaryEvent != null && !paused,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(
            stringResource(primaryLabel ?: R.string.action_done_set),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onEvent(EngineEvent.Skip) },
            enabled = !paused && shown != EngineState.ExerciseDone,
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text(stringResource(R.string.action_skip)) }
        OutlinedButton(
            onClick = { onEvent(EngineEvent.AddRest(ADD_REST_SEC)) },
            enabled = !paused && shown is EngineState.Resting,
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text(stringResource(R.string.action_add_rest, ADD_REST_SEC)) }
        val canPause = shown !is EngineState.Waiting && shown != EngineState.ExerciseDone
        OutlinedButton(
            onClick = { onEvent(if (paused) EngineEvent.Resume else EngineEvent.Pause) },
            enabled = canPause,
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text(stringResource(if (paused) R.string.action_resume else R.string.action_pause)) }
    }
}

@Composable
private fun PhaseLabel(text: String, color: Color) {
    Text(text.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, letterSpacing = 2.sp)
}

@Composable
private fun BigText(text: String, color: Color, sizeSp: Int) {
    Text(
        text,
        style = TimerTextStyle.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.05f).sp),
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** mm:ss; las cuentas atrás redondean hacia arriba (a 0,3 s del final aún se ve 0:01). */
private fun clock(ms: Long, roundUp: Boolean): String {
    val total = if (roundUp) ceilSec(ms) else (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

private fun ceilSec(ms: Long): Int = ceil(ms / 1000.0).toInt()

/** FLAG_KEEP_SCREEN_ON mientras el Modo ejercicio está visible. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Al abrir el primer ejercicio: notificaciones (Android 13+) y, si la voz está activada, micrófono.
 * Sin ellos la app funciona igual (solo botones, sin notificación visible).
 */
@Composable
private fun AskWorkoutPermissionsOnce(askMicrophone: Boolean, onMicrophoneGranted: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) onMicrophoneGranted()
    }
    LaunchedEffect(askMicrophone) {
        if (asked) return@LaunchedEffect
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (askMicrophone) add(Manifest.permission.RECORD_AUDIO)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            asked = true
            launcher.launch(missing.toTypedArray())
        }
    }
}

/** Marcador de la terminación de género en las frases de "menos peso" (ridícul@ → ridículo/ridícula). */
private const val GENDER_PLACEHOLDER = "{g}"

private const val ADD_REST_SEC = 15
