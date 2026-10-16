package com.ckfit.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ckfit.app.data.repo.WeekRepository
import com.ckfit.app.domain.usecase.AppInitializer
import com.ckfit.app.domain.usecase.HomeData
import com.ckfit.app.domain.usecase.ObserveHomeUseCase
import com.ckfit.app.domain.usecase.TodayCard
import com.ckfit.app.service.SessionCoordinator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val today: LocalDate, val data: HomeData, val phraseIndex: Int) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeHome: ObserveHomeUseCase,
    private val initializer: AppInitializer,
    private val weekRepository: WeekRepository,
    private val coordinator: SessionCoordinator,
    private val clock: Clock,
) : ViewModel() {

    private val _openToday = Channel<Unit>(Channel.BUFFERED)

    /** Sesión creada (o ya en curso): hay que abrir "Rutina de hoy". */
    val openToday: Flow<Unit> = _openToday.receiveAsFlow()

    /** Frase motivadora de esta visita (una distinta cada vez que se abre la app). */
    private val phraseIndex = Random.nextInt(PHRASE_SEED_RANGE)

    /** null hasta que el seed y la cola de la semana están listos. */
    private val today = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<HomeUiState> = today
        .flatMapLatest { date ->
            if (date == null) flowOf(HomeUiState.Loading)
            else observeHome(date).map { HomeUiState.Loaded(date, it, phraseIndex) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Al volver a la app puede haber empezado una semana nueva. */
    fun onResume() {
        viewModelScope.launch {
            initializer.prepare()
            today.value = LocalDate.now(clock)
        }
    }

    /** "Iniciar entrenamiento" con la sesión sugerida (o "Continuar" si ya hay una en curso). */
    fun start(card: TodayCard.Session) {
        viewModelScope.launch {
            if (!card.inProgress) coordinator.start(card.routineDayId, card.plannedId)
            _openToday.send(Unit)
        }
    }

    /** Sesión extra con cualquier día del plan; no cuenta en la semana. */
    fun startExtra(routineDayId: Long) {
        viewModelScope.launch {
            coordinator.start(routineDayId, plannedId = null)
            _openToday.send(Unit)
        }
    }

    fun choose(plannedId: Long) {
        viewModelScope.launch { weekRepository.choose(plannedId) }
    }

    fun skip(plannedId: Long, reason: String?) {
        viewModelScope.launch { weekRepository.skip(plannedId, reason) }
    }

    fun undoSkip(plannedId: Long) {
        viewModelScope.launch { weekRepository.undoSkip(plannedId, LocalDate.now(clock)) }
    }

    private companion object {
        const val PHRASE_SEED_RANGE = 10_000
    }
}
