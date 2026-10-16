package com.ckfit.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ckfit.app.R
import com.ckfit.app.domain.usecase.HomeData
import com.ckfit.app.domain.usecase.ProgressCard
import com.ckfit.app.domain.usecase.RoutinesCard
import com.ckfit.app.domain.usecase.TodayCard
import com.ckfit.app.domain.usecase.WeekCard
import com.ckfit.app.ui.common.formatDuration
import com.ckfit.app.ui.common.formatWeight
import com.ckfit.app.ui.common.formatWeightChange
import com.ckfit.app.ui.components.CKFitLogoTopBar
import com.ckfit.app.ui.components.ColorDot
import com.ckfit.app.ui.components.Pill
import com.ckfit.app.ui.components.ProgressRing
import com.ckfit.app.ui.components.TextInputDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenToday: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    var showWeek by rememberSaveable { mutableStateOf(false) }
    var skipPlannedId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { viewModel.openToday.collect { onOpenToday() } }

    Scaffold(
        topBar = {
            val loaded = state as? HomeUiState.Loaded
            CKFitLogoTopBar(
                subtitle = loaded?.today?.let { formatHeaderDate(it) },
                greeting = loaded?.let { greeting(it.data.userName) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is HomeUiState.Loaded -> {
                HomeContent(
                    data = s.data,
                    phraseIndex = s.phraseIndex,
                    onOpenToday = { (s.data.today as? TodayCard.Session)?.let(viewModel::start) },
                    onOpenWeek = { showWeek = true },
                    onSkip = { skipPlannedId = it },
                    onOpenRoutines = onOpenRoutines,
                    onOpenProgress = onOpenProgress,
                    modifier = Modifier.padding(padding),
                )
                if (showWeek) {
                    WeekSheet(
                        week = s.data.week,
                        onDismiss = { showWeek = false },
                        onChoose = {
                            viewModel.choose(it)
                            showWeek = false
                        },
                        onUndoSkip = viewModel::undoSkip,
                        extraDays = s.data.routines.days,
                        onStartExtra = {
                            showWeek = false
                            viewModel.startExtra(it)
                        },
                    )
                }
            }
        }
    }

    skipPlannedId?.let { id ->
        TextInputDialog(
            title = stringResource(R.string.skip_session_title),
            label = stringResource(R.string.skip_session_reason),
            initial = "",
            confirmLabel = stringResource(R.string.action_skip_session),
            allowEmpty = true,
            onDismiss = { skipPlannedId = null },
            onConfirm = {
                viewModel.skip(id, it)
                skipPlannedId = null
            },
        )
    }
}

@Composable
private fun greeting(name: String?): String =
    name?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.home_greeting_name, it) }
        ?: stringResource(R.string.home_greeting)

/** Frase informal elegida al abrir Home; cambia cada vez que se vuelve a abrir la app. */
@Composable
private fun MotivationalPhrase(index: Int) {
    val phrases = stringArrayResource(R.array.motivational_phrases)
    Text(
        text = phrases[index.mod(phrases.size)],
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
}

private fun formatHeaderDate(date: LocalDate): String {
    val locale = Locale.forLanguageTag("es")
    return DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale).format(date)
        .replaceFirstChar { it.titlecase(locale) }
}

@Composable
private fun HomeContent(
    data: HomeData,
    phraseIndex: Int,
    onOpenToday: () -> Unit,
    onOpenWeek: () -> Unit,
    onSkip: (Long) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MotivationalPhrase(phraseIndex)
        if (data.week.mandatoryTotal + data.week.optionalTotal > 0) {
            WeekProgressCard(data.week, onOpenWeek)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_today_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (data.week.items.isNotEmpty()) {
                TextButton(onClick = onOpenWeek) {
                    Text(stringResource(R.string.home_see_week), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        TodayCardView(data.today, onOpenToday, onOpenWeek, onSkip)
        RoutinesCardView(data.routines, onOpenRoutines)
        ProgressCardView(data.progress, onOpenProgress)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HomeCard(
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val shape = RoundedCornerShape(24.dp)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, colors = colors) { inner() }
    } else {
        Card(modifier = Modifier.fillMaxWidth(), shape = shape, colors = colors) { inner() }
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WeekProgressCard(week: WeekCard, onOpenWeek: () -> Unit) {
    HomeCard(onClick = onOpenWeek) {
        CardLabel(stringResource(R.string.home_week_progress_label))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = if (week.mandatoryTotal > 0) week.mandatoryDone.toFloat() / week.mandatoryTotal else 0f,
                size = 116.dp,
                strokeWidth = 12.dp,
            ) {
                Text(
                    text = stringResource(R.string.fraction, week.mandatoryDone, week.mandatoryTotal),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    CardLabel(stringResource(R.string.home_week_pending))
                    Text(
                        text = pluralStringResource(R.plurals.session_count, week.mandatoryPending, week.mandatoryPending),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (week.optionalTotal > 0) {
                    Column {
                        CardLabel(stringResource(R.string.home_week_optional))
                        Text(
                            text = stringResource(R.string.fraction, week.optionalDone, week.optionalTotal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCardView(card: TodayCard, onOpenToday: () -> Unit, onOpenWeek: () -> Unit, onSkip: (Long) -> Unit) {
    HomeCard(onClick = if (card is TodayCard.Session && card.inProgress) onOpenToday else null) {
        when (card) {
            is TodayCard.Session -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(card.colorHex)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (card.isOptional) {
                        Spacer(Modifier.width(8.dp))
                        Pill(stringResource(R.string.home_today_optional), MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(
                    text = stringResource(
                        R.string.home_today_summary,
                        pluralStringResource(R.plurals.exercise_count, card.exerciseCount, card.exerciseCount),
                        formatDuration(card.estimatedSec),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (card.total > 0) card.finished.toFloat() / card.total else 0f },
                        modifier = Modifier.weight(1f).height(6.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.fraction, card.finished, card.total),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onOpenToday, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(
                        stringResource(if (card.inProgress) R.string.action_view_routine else R.string.action_start_workout),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!card.inProgress) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onOpenWeek) {
                            Text(stringResource(R.string.action_choose_other_session), color = MaterialTheme.colorScheme.secondary)
                        }
                        card.plannedId?.let { id ->
                            TextButton(onClick = { onSkip(id) }) {
                                Text(stringResource(R.string.action_skip_session), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            is TodayCard.NothingPending -> {
                Text(
                    text = stringResource(
                        if (card.isRestDay) R.string.home_today_rest_day else R.string.home_today_nothing_pending,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onOpenWeek, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_train_other_session))
                }
            }
            TodayCard.NoActivePlan -> Text(
                text = stringResource(R.string.home_no_active_plan),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun RoutinesCardView(card: RoutinesCard, onOpenRoutines: () -> Unit) {
    HomeCard(onClick = onOpenRoutines) {
        CardLabel(stringResource(R.string.home_routines_label))
        Text(
            text = card.routineName ?: stringResource(R.string.home_no_active_plan),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (card.routineName != null) {
            Text(
                text = stringResource(
                    R.string.home_routines_days,
                    pluralStringResource(R.plurals.training_day_count, card.trainingDays, card.trainingDays),
                    pluralStringResource(R.plurals.optional_day_count, card.optionalDays, card.optionalDays),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressCardView(card: ProgressCard, onOpenProgress: () -> Unit) {
    HomeCard(onClick = onOpenProgress) {
        CardLabel(stringResource(R.string.home_progress_label))
        val summary = card.summary
        if (summary == null) {
            Text(
                text = stringResource(R.string.home_progress_no_weigh_ins),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_progress_last_weigh_in, formatWeight(summary.latest.weightKg, card.unit)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                summary.change30dKg?.let {
                    Pill(formatWeightChange(it, card.unit), MaterialTheme.colorScheme.secondary)
                }
            }
            Text(
                text = stringResource(
                    if (summary.change30dKg != null) R.string.home_progress_change_30d_label else R.string.home_progress_no_change,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
