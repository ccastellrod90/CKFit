package com.ckfit.app.ui.focus

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ckfit.app.data.db.dao.SessionExerciseDetail
import com.ckfit.app.data.repo.ProfileRepository
import com.ckfit.app.data.repo.SessionRepository
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.domain.engine.EngineEvent
import com.ckfit.app.domain.engine.EngineState
import com.ckfit.app.domain.model.SessionExerciseStatus
import com.ckfit.app.domain.model.Sex
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.model.toKg
import com.ckfit.app.service.SessionCoordinator
import com.ckfit.app.service.VoiceStatus
import com.ckfit.app.service.VoskCommandSource
import com.ckfit.app.service.WorkoutController
import com.ckfit.app.service.WorkoutService
import com.ckfit.app.service.WorkoutState
import com.ckfit.app.ui.common.parseNumber
import com.ckfit.app.ui.navigation.ExerciseFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

sealed interface FocusUiState {
    data object Loading : FocusUiState
    data class Ready(
        val workout: WorkoutState,
        val unit: WeightUnit,
        val voice: VoiceStatus,
        val voiceEnabled: Boolean,
        /** Otros ejercicios de la sesión sin terminar, para "Elegir otro ejercicio". */
        val others: List<SessionExerciseDetail>,
    ) : FocusUiState
}

sealed interface FocusEvent {
    data class OpenExercise(val sessionExerciseId: Long) : FocusEvent
    data object SessionClosed : FocusEvent

    /** Bajó por debajo del peso de la vez anterior: aviso con humor. */
    data class WeightBelowReference(val phraseIndex: Int, val sex: Sex?) : FocusEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseFocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val controller: WorkoutController,
    private val coordinator: SessionCoordinator,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val voice: VoskCommandSource,
    private val profile: ProfileRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExerciseFocus>()
    private val sessionExerciseId = route.sessionExerciseId
    private var autoStartPending = route.autoStart

    private val others: Flow<List<SessionExerciseDetail>> = controller.state.filterNotNull()
        .map { it.exercise.sessionId }
        .flatMapLatest { sessions.observeExercises(it) }
        .map { list ->
            list.filter {
                it.item.id != sessionExerciseId && it.dayExercise != null &&
                    it.item.status != SessionExerciseStatus.DONE && it.item.status != SessionExerciseStatus.SKIPPED
            }
        }

    val state: StateFlow<FocusUiState> = combine(
        controller.state,
        profile.observe(),
        voice.status,
        settings.audio.map { it.voiceCommands },
        others,
    ) { workout, p, voiceStatus, voiceEnabled, others ->
        if (workout?.exercise?.sessionExerciseId == sessionExerciseId) {
            FocusUiState.Ready(workout, p.unit, voiceStatus, voiceEnabled, others)
        } else {
            FocusUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState.Loading)

    private val _events = Channel<FocusEvent>(Channel.BUFFERED)
    val events: Flow<FocusEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            controller.open(sessionExerciseId)
            // "Comenzar" desde Rutina de hoy: la serie arranca sola al abrir.
            if (autoStartPending && controller.state.value?.engine is EngineState.Waiting) {
                controller.send(EngineEvent.Start)
            }
            autoStartPending = false
        }
        // Si el controlador pasa a otro ejercicio (p. ej. "siguiente" por voz), la pantalla lo sigue.
        viewModelScope.launch {
            var seenOwn = false
            controller.state.filterNotNull().collect { s ->
                val id = s.exercise.sessionExerciseId
                if (id == sessionExerciseId) {
                    seenOwn = true
                } else if (seenOwn) {
                    seenOwn = false
                    _events.send(FocusEvent.OpenExercise(id))
                }
            }
        }
    }

    fun send(event: EngineEvent) = controller.send(event)

    fun setAutoAdvance(value: Boolean) = controller.setAutoAdvance(value)

    fun stepWeight(direction: Int) {
        val current = controller.state.value?.exercise ?: return
        val next = ((current.weightKg ?: 0.0) + direction * current.weightStepKg).coerceAtLeast(0.0)
        controller.stepWeight(direction)
        if (direction < 0) checkBelowReference(next)
    }

    /** Peso escrito en la unidad del perfil; vacío = sin peso. */
    fun setWeight(text: String, unit: WeightUnit) {
        if (text.isBlank()) {
            controller.setWeight(null)
            return
        }
        parseNumber(text)?.let {
            val kg = it.toKg(unit)
            controller.setWeight(kg)
            checkBelowReference(kg)
        }
    }

    private fun checkBelowReference(newKg: Double) {
        val reference = controller.state.value?.exercise?.referenceWeightKg ?: return
        if (newKg < reference - WEIGHT_EPSILON) {
            viewModelScope.launch {
                val sex = profile.observe().first().sex
                _events.send(FocusEvent.WeightBelowReference(Random.nextInt(PHRASES_RANGE), sex))
            }
        }
    }

    /** Micrófono concedido con el entrenamiento en marcha: el servicio pasa a escuchar. */
    fun onMicrophoneGranted() = WorkoutService.refresh(context)

    fun openNext() {
        val next = (state.value as? FocusUiState.Ready)?.workout?.exercise?.nextSessionExerciseId ?: return
        open(next)
    }

    fun open(sessionExerciseId: Long) {
        viewModelScope.launch { _events.send(FocusEvent.OpenExercise(sessionExerciseId)) }
    }

    fun endExercise() = controller.send(EngineEvent.EndExercise)

    fun finishSession() {
        val sessionId = (state.value as? FocusUiState.Ready)?.workout?.exercise?.sessionId ?: return
        viewModelScope.launch {
            coordinator.finish(sessionId)
            _events.send(FocusEvent.SessionClosed)
        }
    }

    private companion object {
        const val WEIGHT_EPSILON = 0.001
        const val PHRASES_RANGE = 10_000
    }
}
