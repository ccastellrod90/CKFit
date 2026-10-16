package com.ckfit.app.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ckfit.app.data.db.dao.RoutineSummary
import com.ckfit.app.data.repo.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repository: RoutineRepository,
) : ViewModel() {

    val routines: StateFlow<List<RoutineSummary>?> = repository.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _opened = Channel<Long>(Channel.BUFFERED)

    /** Plan recién creado o duplicado: se abre para editarlo. */
    val opened: Flow<Long> = _opened.receiveAsFlow()

    fun create(name: String, dayNames: List<String>, dayColor: String) {
        viewModelScope.launch { _opened.send(repository.createRoutine(name, dayNames, dayColor)) }
    }

    fun duplicate(sourceId: Long, name: String) {
        viewModelScope.launch { _opened.send(repository.duplicateRoutine(sourceId, name)) }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repository.renameRoutine(id, name) }
    }

    fun activate(id: Long) {
        viewModelScope.launch { repository.activateRoutine(id) }
    }
}
