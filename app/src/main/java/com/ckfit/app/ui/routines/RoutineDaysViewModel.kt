package com.ckfit.app.ui.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ckfit.app.data.db.dao.RoutineDayWithCount
import com.ckfit.app.data.db.entity.RoutineEntity
import com.ckfit.app.data.repo.RoutineRepository
import com.ckfit.app.ui.navigation.RoutineDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineDaysUiState(
    val routine: RoutineEntity,
    val days: List<RoutineDayWithCount>,
)

@HiltViewModel
class RoutineDaysViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RoutineRepository,
) : ViewModel() {

    private val routineId = savedStateHandle.toRoute<RoutineDays>().routineId

    val state: StateFlow<RoutineDaysUiState?> = combine(
        repository.observeRoutine(routineId),
        repository.observeDaysWithCount(routineId),
    ) { routine, days -> routine?.let { RoutineDaysUiState(it, days) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        viewModelScope.launch { repository.renameRoutine(routineId, name) }
    }

    fun activate() {
        viewModelScope.launch { repository.activateRoutine(routineId) }
    }
}
