package com.ckfit.app.ui.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ckfit.app.data.repo.ProfileRepository
import com.ckfit.app.data.repo.RoutineRepository
import com.ckfit.app.data.seed.SeedParser
import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.model.kgTo
import com.ckfit.app.domain.model.toKg
import com.ckfit.app.domain.prescription.LoadDefaults
import com.ckfit.app.domain.prescription.PrescriptionDraft
import com.ckfit.app.domain.prescription.PrescriptionError
import com.ckfit.app.domain.prescription.PrescriptionValidator
import com.ckfit.app.ui.common.formatNumber
import com.ckfit.app.ui.common.parseNumber
import com.ckfit.app.ui.navigation.ExerciseEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Campos del formulario tal como se escriben; los pesos en la unidad del perfil. */
data class EditorFields(
    val measureType: MeasureType = MeasureType.REPS,
    val perSide: Boolean = false,
    val usesWeight: Boolean = true,
    val loadType: LoadType = LoadType.VOLUME,
    val sets: String = "",
    val repsMin: String = "",
    val repsMax: String = "",
    val durationSec: String = "",
    val restSec: String = "",
    val restMaxSec: String = "",
    val weight: String = "",
    val weightStep: String = "",
    val isIntensitySet: Boolean = false,
    val intensityNote: String = "",
    val notes: String = "",
)

data class ExerciseEditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val exerciseName: String = "",
    val muscleGroup: String = "",
    val unit: WeightUnit = WeightUnit.KG,
    /** Otras prescripciones que usan el mismo ejercicio: cambiar tipo o "por lado" las afecta. */
    val otherUsages: Int = 0,
    val fields: EditorFields = EditorFields(),
    val errors: Set<PrescriptionError> = emptySet(),
    val showErrors: Boolean = false,
)

@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RoutineRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ExerciseEditor>()

    private val _state = MutableStateFlow(ExerciseEditorUiState())
    val state: StateFlow<ExerciseEditorUiState> = _state

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val unit = profileRepository.observe().first().unit
        val existing = route.dayExerciseId?.let { repository.getDayExercise(it) }
        val exerciseId = existing?.exerciseId ?: route.exerciseId ?: return
        val exercise = repository.getExercise(exerciseId) ?: return
        val draft = if (existing != null) {
            PrescriptionDraft(
                measureType = exercise.measureType,
                perSide = exercise.perSide,
                usesWeight = exercise.usesWeight,
                loadType = existing.loadType,
                sets = existing.sets,
                repsMin = existing.repsMin,
                repsMax = existing.repsMax,
                durationSec = existing.durationSec,
                restSec = existing.restSec,
                restMaxSec = existing.restMaxSec,
                weightKg = existing.currentWeightKg,
                weightStepKg = existing.weightStepKg,
                isIntensitySet = existing.isIntensitySet,
                intensityNote = existing.intensityNote,
                notes = existing.notes,
            )
        } else {
            // Nuevo en este día: empieza con el peso que el ejercicio ya tenga en otros días.
            LoadDefaults.newDraft(
                exercise.measureType,
                exercise.perSide,
                exercise.muscleGroup,
                usesWeight = exercise.usesWeight,
                weightKg = repository.exerciseWeight(exercise.id),
            )
        }
        val usages = repository.exerciseUsages(exerciseId) - if (existing != null) 1 else 0
        _state.value = ExerciseEditorUiState(
            loaded = true,
            isNew = existing == null,
            exerciseName = exercise.name,
            muscleGroup = exercise.muscleGroup,
            unit = unit,
            otherUsages = usages.coerceAtLeast(0),
            fields = draft.toFields(unit),
        ).revalidate()
    }

    fun onFieldsChange(transform: (EditorFields) -> EditorFields) {
        _state.update { it.copy(fields = transform(it.fields)).revalidate() }
    }

    /** Elegir loadType prellena los valores por defecto de la sección 3. */
    fun onLoadTypeChange(loadType: LoadType) {
        _state.update { s ->
            val draft = LoadDefaults.apply(loadType, s.fields.toDraft(s.unit))
            s.copy(fields = draft.toFields(s.unit, keepTextOf = s.fields)).revalidate()
        }
    }

    fun onIntensityChange(value: Boolean) = onFieldsChange {
        it.copy(
            isIntensitySet = value,
            intensityNote = if (value && it.intensityNote.isBlank()) SeedParser.DEFAULT_INTENSITY_NOTE else it.intensityNote,
        )
    }

    fun save() {
        val s = _state.value
        if (s.errors.isNotEmpty()) {
            _state.value = s.copy(showErrors = true)
            return
        }
        viewModelScope.launch {
            val draft = s.fields.toDraft(s.unit)
            val existingId = route.dayExerciseId
            if (existingId != null) {
                repository.updateDayExercise(existingId, draft)
            } else {
                val dayId = route.dayId ?: return@launch
                val exerciseId = route.exerciseId ?: return@launch
                repository.addDayExercise(dayId, exerciseId, draft)
            }
            _done.send(Unit)
        }
    }

    /** Quitar del día = archivar (el historial no se toca). */
    fun remove() {
        val id = route.dayExerciseId ?: return
        viewModelScope.launch {
            repository.archiveDayExercise(id)
            _done.send(Unit)
        }
    }

    private fun ExerciseEditorUiState.revalidate() = copy(errors = PrescriptionValidator.validate(fields.toDraft(unit)))
}

private fun String.intOrNull(): Int? = trim().toIntOrNull()

fun EditorFields.toDraft(unit: WeightUnit) = PrescriptionDraft(
    measureType = measureType,
    perSide = perSide,
    usesWeight = usesWeight,
    loadType = loadType,
    sets = sets.intOrNull(),
    repsMin = repsMin.intOrNull(),
    repsMax = repsMax.intOrNull(),
    durationSec = durationSec.intOrNull(),
    restSec = restSec.intOrNull(),
    restMaxSec = restMaxSec.intOrNull(),
    weightKg = parseNumber(weight)?.toKg(unit),
    weightStepKg = parseNumber(weightStep)?.toKg(unit),
    isIntensitySet = isIntensitySet,
    intensityNote = intensityNote,
    notes = notes,
)

/** [keepTextOf]: conserva lo escrito en los campos que los defaults no tocan (evita reformatear pesos). */
fun PrescriptionDraft.toFields(unit: WeightUnit, keepTextOf: EditorFields? = null) = EditorFields(
    measureType = measureType,
    perSide = perSide,
    usesWeight = usesWeight,
    loadType = loadType,
    sets = sets?.toString().orEmpty(),
    repsMin = repsMin?.toString().orEmpty(),
    repsMax = repsMax?.toString().orEmpty(),
    durationSec = durationSec?.toString().orEmpty(),
    restSec = restSec?.toString().orEmpty(),
    restMaxSec = restMaxSec?.toString().orEmpty(),
    weight = keepTextOf?.weight ?: weightKg?.let { formatNumber(it.kgTo(unit)) }.orEmpty(),
    weightStep = keepTextOf?.weightStep ?: weightStepKg?.let { formatNumber(it.kgTo(unit)) }.orEmpty(),
    isIntensitySet = isIntensitySet,
    intensityNote = intensityNote.orEmpty(),
    notes = notes.orEmpty(),
)
