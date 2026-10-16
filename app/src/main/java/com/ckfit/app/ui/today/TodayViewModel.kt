package com.ckfit.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ckfit.app.data.db.dao.SessionExerciseDetail
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.data.repo.ProfileRepository
import com.ckfit.app.data.repo.RoutineRepository
import com.ckfit.app.data.repo.SessionRepository
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.data.repo.WeekRepository
import com.ckfit.app.data.repo.toItem
import com.ckfit.app.domain.estimate.DurationInput
import com.ckfit.app.domain.estimate.EstimateDuration
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.week.WeekPlanner
import com.ckfit.app.service.SessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TodayItem(
    val detail: SessionExerciseDetail,
    val setsDone: Int,
) {
    val id get() = detail.item.id
    val status get() = detail.item.status
    val finished get() = status == SessionExerciseStatus.DONE || status == SessionExerciseStatus.SKIPPED
}

/** Otra sesión a la que cambiar: pendiente de la semana o extra (plannedId null). */
data class SessionOption(
    val routineDayId: Long,
    val plannedId: Long?,
    val dayOfWeek: Int,
    val title: String,
    val colorHex: String,
)

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoSession : TodayUiState
    data class Active(
        val sessionId: Long,
        val plannedId: Long?,
        val title: String,
        val colorHex: String?,
        val items: List<TodayItem>,
        val totalSec: Int,
        val remainingSec: Int,
        val unit: WeightUnit,
        val options: List<SessionOption>,
    ) : TodayUiState {
        val allFinished get() = items.isNotEmpty() && items.all { it.finished }
    }
}

sealed interface TodayEvent {
    data object SessionClosed : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val weeks: WeekRepository,
    private val settings: SettingsRepository,
    private val profile: ProfileRepository,
    private val coordinator: SessionCoordinator,
    private val clock: Clock,
) : ViewModel() {

    private val _events = Channel<TodayEvent>(Channel.BUFFERED)
    val events: Flow<TodayEvent> = _events.receiveAsFlow()

    val state: StateFlow<TodayUiState> = sessions.observeInProgress().flatMapLatest { session ->
        if (session == null) return@flatMapLatest flowOf(TodayUiState.NoSession)
        val day = session.routineDayId?.let { routines.observeDay(it) } ?: flowOf(null)
        combine(
            combine(sessions.observeExercises(session.id), sessions.observeSetCounts(session.id)) { list, counts ->
                val bySession = counts.associate { it.sessionExerciseId to it.sets }
                list.map { TodayItem(it, bySession[it.item.id] ?: 0) }
            },
            day,
            settings.switchSideSec,
            profile.observe().map { it.unit },
            observeOptions(),
        ) { items, dayEntity, switchSec, unit, options ->
            TodayUiState.Active(
                sessionId = session.id,
                plannedId = session.plannedSessionId,
                title = dayEntity?.title.orEmpty(),
                colorHex = dayEntity?.colorHex,
                items = items,
                totalSec = EstimateDuration.sessionSeconds(items.mapNotNull { it.durationInput(fullSets = true) }, switchSec),
                remainingSec = EstimateDuration.sessionSeconds(
                    items.filterNot { it.finished }.mapNotNull { it.durationInput(fullSets = false) },
                    switchSec,
                ),
                unit = unit,
                options = options.filterNot { it.plannedId != null && it.plannedId == session.plannedSessionId },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    /** Pendientes de la semana primero; luego cualquier día del plan como sesión extra. */
    private fun observeOptions(): Flow<List<SessionOption>> {
        val weekStart = WeekPlanner.weekStartOf(LocalDate.now(clock))
        return combine(
            weeks.observeWeek(weekStart),
            routines.observeActiveRoutine().flatMapLatest { r ->
                if (r == null) flowOf(emptyList<RoutineDayEntity>()) else routines.observeDays(r.id)
            },
        ) { week, days ->
            val pending = week
                .filter { it.planned.status == PlannedStatus.PENDING && it.planned.routineDayId != null }
                .sortedBy { it.toItem().dayOfWeek }
                .map {
                    SessionOption(
                        routineDayId = it.planned.routineDayId!!,
                        plannedId = it.planned.id,
                        dayOfWeek = it.toItem().dayOfWeek,
                        title = it.dayTitle.orEmpty(),
                        colorHex = it.dayColorHex.orEmpty(),
                    )
                }
            val extra = days.filterNot { it.isRestDay }.map {
                SessionOption(it.id, plannedId = null, dayOfWeek = it.dayOfWeek, title = it.title, colorHex = it.colorHex)
            }
            pending + extra
        }
    }

    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { sessions.reorder(orderedIds) }
    }

    fun skipExercise(id: Long) {
        viewModelScope.launch { sessions.setExerciseStatus(id, SessionExerciseStatus.SKIPPED) }
    }

    fun restoreExercise(id: Long) {
        viewModelScope.launch { sessions.setExerciseStatus(id, SessionExerciseStatus.PENDING) }
    }

    fun finish() = closeWith { coordinator.finish(it.sessionId) }

    fun skipSession(reason: String?) = closeWith { coordinator.skip(it.sessionId, reason) }

    fun switchTo(option: SessionOption) {
        val active = state.value as? TodayUiState.Active ?: return
        viewModelScope.launch { coordinator.switchTo(active.sessionId, option.routineDayId, option.plannedId) }
    }

    private fun closeWith(action: suspend (TodayUiState.Active) -> Unit) {
        val active = state.value as? TodayUiState.Active ?: return
        viewModelScope.launch {
            action(active)
            _events.send(TodayEvent.SessionClosed)
        }
    }
}

/** Para el tiempo restante, las series ya hechas no cuentan. */
private fun TodayItem.durationInput(fullSets: Boolean): DurationInput? {
    val p = detail.dayExercise ?: return null
    val e = detail.exercise ?: return null
    val sets = if (fullSets) p.sets else (p.sets - setsDone).coerceAtLeast(0)
    return DurationInput(e.measureType, e.perSide, sets, p.repsMin, p.repsMax, p.durationSec, p.restSec)
}
