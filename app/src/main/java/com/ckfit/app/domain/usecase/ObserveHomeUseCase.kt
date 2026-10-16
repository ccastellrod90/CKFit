package com.ckfit.app.domain.usecase

import com.ckfit.app.data.db.dao.DayExerciseWithExercise
import com.ckfit.app.data.db.dao.PlannedSessionWithDay
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.data.repo.BodyRepository
import com.ckfit.app.data.repo.ProfileRepository
import com.ckfit.app.data.repo.RoutineRepository
import com.ckfit.app.data.repo.SessionRepository
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.data.repo.WeekRepository
import com.ckfit.app.data.repo.toItem
import com.ckfit.app.domain.body.WeighIn
import com.ckfit.app.domain.body.WeightSummary
import com.ckfit.app.domain.body.WeightTrend
import com.ckfit.app.domain.estimate.DurationInput
import com.ckfit.app.domain.estimate.EstimateDuration
import com.ckfit.app.domain.model.PlannedStatus
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.week.WeekPlanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

sealed interface TodayCard {
    data class Session(
        /** null si es una sesión en curso fuera de la cola semanal. */
        val plannedId: Long?,
        val routineDayId: Long,
        val title: String,
        val colorHex: String,
        val isOptional: Boolean,
        val exerciseCount: Int,
        val estimatedSec: Int,
        val finished: Int,
        val total: Int,
        val inProgress: Boolean,
    ) : TodayCard

    /** No queda nada pendiente esta semana; [isRestDay] si hoy es día de descanso en el plan. */
    data class NothingPending(val isRestDay: Boolean) : TodayCard

    data object NoActivePlan : TodayCard
}

data class WeekItem(
    val plannedId: Long,
    val dayOfWeek: Int,
    val title: String?,
    val colorHex: String?,
    val isOptional: Boolean,
    val status: PlannedStatus,
    val skipReason: String?,
    val canUndoSkip: Boolean,
    val isSuggested: Boolean,
)

/** Progreso semanal: obligatorias hechas sobre planificadas; opcionales aparte. */
data class WeekCard(
    val mandatoryDone: Int,
    val mandatoryTotal: Int,
    val mandatoryPending: Int,
    val skipped: Int,
    val optionalDone: Int,
    val optionalTotal: Int,
    val items: List<WeekItem>,
)

data class RoutinesCard(
    val routineName: String?,
    val trainingDays: Int,
    val optionalDays: Int,
    /** Días de entrenamiento del plan activo, para "Entrenar otra sesión" (sesión extra). */
    val days: List<RoutineDayEntity> = emptyList(),
)

data class ProgressCard(
    val summary: WeightSummary?,
    val unit: WeightUnit,
)

data class HomeData(
    /** Para el saludo; null si no lo ha puesto en Ajustes. */
    val userName: String?,
    val today: TodayCard,
    val week: WeekCard,
    val routines: RoutinesCard,
    val progress: ProgressCard,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveHomeUseCase @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val weekRepository: WeekRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val bodyRepository: BodyRepository,
    private val profileRepository: ProfileRepository,
) {
    private data class WeekState(
        val week: List<PlannedSessionWithDay>,
        val suggestedPlannedId: Long?,
        val suggestedDayId: Long?,
        val inProgressSessionId: Long?,
    )

    operator fun invoke(today: LocalDate): Flow<HomeData> {
        val weekState = observeWeekState(today)
        return combine(
            profileRepository.observe().map { it.name },
            weekState.flatMapLatest { observeToday(today, it) },
            weekState.map { weekCard(it, today) },
            observeRoutines(),
            observeProgress(),
            ::HomeData,
        )
    }

    private fun observeWeekState(today: LocalDate): Flow<WeekState> = combine(
        weekRepository.observeWeek(WeekPlanner.weekStartOf(today)),
        sessionRepository.observeInProgress(),
        weekRepository.chosenPlannedId,
    ) { week, inProgress, chosen ->
        val suggested = WeekPlanner.suggest(week.map { it.toItem() }, inProgress?.plannedSessionId, chosen)
        WeekState(
            week = week,
            suggestedPlannedId = suggested?.id,
            // Si hay una sesión en curso, esa (aunque esté fuera de la cola); si no, la sugerida.
            suggestedDayId = inProgress?.routineDayId ?: suggested?.routineDayId,
            inProgressSessionId = inProgress?.id,
        )
    }

    private fun weekCard(state: WeekState, today: LocalDate): WeekCard {
        val mandatory = state.week.filter { it.dayOptional != true }
        val optional = state.week.filter { it.dayOptional == true }
        return WeekCard(
            mandatoryDone = mandatory.count { it.planned.status == PlannedStatus.DONE },
            mandatoryTotal = mandatory.size,
            mandatoryPending = mandatory.count { it.planned.status == PlannedStatus.PENDING },
            skipped = state.week.count { it.planned.status == PlannedStatus.SKIPPED },
            optionalDone = optional.count { it.planned.status == PlannedStatus.DONE },
            optionalTotal = optional.size,
            items = state.week.map {
                WeekItem(
                    plannedId = it.planned.id,
                    dayOfWeek = it.dayOfWeek ?: it.planned.plannedDate.dayOfWeek.value,
                    title = it.dayTitle,
                    colorHex = it.dayColorHex,
                    isOptional = it.dayOptional == true,
                    status = it.planned.status,
                    skipReason = it.planned.skipReason,
                    canUndoSkip = WeekPlanner.canUndoSkip(it.planned.status, it.planned.weekStart, today),
                    isSuggested = it.planned.id == state.suggestedPlannedId,
                )
            },
        )
    }

    private fun observeRoutines(): Flow<RoutinesCard> =
        routineRepository.observeActiveRoutine().flatMapLatest { routine ->
            if (routine == null) {
                flowOf(RoutinesCard(null, 0, 0))
            } else {
                routineRepository.observeDays(routine.id).map { days ->
                    val training = days.filterNot { it.isRestDay }
                    RoutinesCard(routine.name, training.size, training.count { it.isOptional }, training)
                }
            }
        }

    private fun observeProgress(): Flow<ProgressCard> =
        combine(bodyRepository.observeAllDesc(), profileRepository.observe()) { list, profile ->
            ProgressCard(
                summary = WeightTrend.summarize(list.map { WeighIn(it.date, it.weightKg) }),
                unit = profile.unit,
            )
        }

    private fun observeToday(today: LocalDate, state: WeekState): Flow<TodayCard> =
        routineRepository.observeActiveRoutine().flatMapLatest { routine ->
            if (routine == null) return@flatMapLatest flowOf(TodayCard.NoActivePlan)
            routineRepository.observeDays(routine.id).flatMapLatest { days ->
                val day = state.suggestedDayId?.let { id -> days.firstOrNull { it.id == id } }
                if (day == null) {
                    val todayDay = days.firstOrNull { it.dayOfWeek == today.dayOfWeek.value }
                    flowOf(TodayCard.NothingPending(isRestDay = todayDay?.isRestDay ?: false))
                } else {
                    sessionCard(day, state.suggestedPlannedId, state.inProgressSessionId)
                }
            }
        }

    private fun sessionCard(day: RoutineDayEntity, plannedId: Long?, sessionId: Long?): Flow<TodayCard> {
        val progress = if (sessionId != null) {
            sessionRepository.observeProgress(sessionId).map { it.finished to it.total }
        } else {
            flowOf(null)
        }
        return combine(
            routineRepository.observeDayExercises(day.id),
            settingsRepository.switchSideSec,
            progress,
        ) { exercises, switchSec, sessionProgress ->
            TodayCard.Session(
                plannedId = plannedId,
                routineDayId = day.id,
                title = day.title,
                colorHex = day.colorHex,
                isOptional = day.isOptional,
                exerciseCount = exercises.size,
                estimatedSec = EstimateDuration.sessionSeconds(exercises.map { it.toDurationInput() }, switchSec),
                finished = sessionProgress?.first ?: 0,
                total = sessionProgress?.second?.takeIf { it > 0 } ?: exercises.size,
                inProgress = sessionId != null,
            )
        }
    }
}

fun DayExerciseWithExercise.toDurationInput() = DurationInput(
    measureType = exercise.measureType,
    perSide = exercise.perSide,
    sets = dayExercise.sets,
    repsMin = dayExercise.repsMin,
    repsMax = dayExercise.repsMax,
    durationSec = dayExercise.durationSec,
    restSec = dayExercise.restSec,
)
