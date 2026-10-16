package com.ckfit.app.ui.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ckfit.app.data.db.dao.DayExerciseWithExercise
import com.ckfit.app.data.db.entity.ExerciseEntity
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.data.repo.ProfileRepository
import com.ckfit.app.data.repo.RoutineRepository
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.ui.navigation.DayEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

data class DayEditorUiState(
    val day: RoutineDayEntity,
    val exercises: List<DayExerciseWithExercise>,
    val unit: WeightUnit,
)

sealed interface DayEditorEvent {
    data class Archived(val dayExerciseId: Long, val name: String) : DayEditorEvent
    data class OpenNewPrescription(val dayId: Long, val exerciseId: Long) : DayEditorEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DayEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RoutineRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val dayId = savedStateHandle.toRoute<DayEditor>().dayId

    val state: StateFlow<DayEditorUiState?> = combine(
        repository.observeDay(dayId),
        repository.observeDayExercises(dayId),
        profileRepository.observe(),
    ) { day, exercises, profile -> day?.let { DayEditorUiState(it, exercises, profile.unit) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    /** Catálogo filtrado por nombre o grupo, sin distinguir tildes ni mayúsculas. */
    val catalog: StateFlow<List<ExerciseEntity>> = combine(repository.observeCatalog(), query) { list, q ->
        val needle = q.normalized()
        if (needle.isEmpty()) list
        else list.filter { it.name.normalized().contains(needle) || it.muscleGroup.normalized().contains(needle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<DayEditorEvent>(Channel.BUFFERED)
    val events: Flow<DayEditorEvent> = _events.receiveAsFlow()

    /** El título se guarda con un pequeño retraso para no escribir en cada tecla. */
    private val titleInput = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            titleInput.filterNotNull().debounce(TITLE_DEBOUNCE_MS).collect { title ->
                if (title.isNotBlank()) updateDay { it.copy(title = title) }
            }
        }
    }

    fun onTitleChange(title: String) {
        titleInput.value = title
    }

    fun onColorChange(hex: String) = updateDay { it.copy(colorHex = hex) }

    fun onOptionalChange(value: Boolean) = updateDay { it.copy(isOptional = value) }

    /** Si un día titulado "Descanso" pasa a ser de entrenamiento, toma el nombre del día como título. */
    fun onRestDayChange(value: Boolean, dayName: String, restTitle: String) = updateDay {
        val title = if (!value && it.title.trim().equals(restTitle, ignoreCase = true)) dayName else it.title
        it.copy(isRestDay = value, title = title)
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorder(orderedIds) }
    }

    fun archive(item: DayExerciseWithExercise) {
        viewModelScope.launch {
            repository.archiveDayExercise(item.dayExercise.id)
            _events.send(DayEditorEvent.Archived(item.dayExercise.id, item.exercise.name))
        }
    }

    fun restore(dayExerciseId: Long) {
        viewModelScope.launch { repository.restoreDayExercise(dayExerciseId) }
    }

    fun pick(exercise: ExerciseEntity) {
        viewModelScope.launch { _events.send(DayEditorEvent.OpenNewPrescription(dayId, exercise.id)) }
    }

    fun createExercise(name: String, muscleGroup: String, measureType: MeasureType, perSide: Boolean, usesWeight: Boolean) {
        viewModelScope.launch {
            val id = repository.createExercise(name, muscleGroup, measureType, perSide, usesWeight)
            _events.send(DayEditorEvent.OpenNewPrescription(dayId, id))
        }
    }

    private fun updateDay(transform: (RoutineDayEntity) -> RoutineDayEntity) {
        viewModelScope.launch {
            val current = repository.getDay(dayId) ?: return@launch
            repository.updateDay(transform(current))
        }
    }

    private companion object {
        const val TITLE_DEBOUNCE_MS = 400L
    }
}

private val DIACRITICS = "\\p{Mn}+".toRegex()

fun String.normalized(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

/** Para ofrecer "Crear «texto»" solo si no existe ya un ejercicio con ese nombre. */
fun List<ExerciseEntity>.hasExactName(name: String): Boolean = any { it.name.normalized() == name.normalized() }
