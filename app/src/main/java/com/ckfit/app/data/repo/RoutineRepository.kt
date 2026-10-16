package com.ckfit.app.data.repo

import androidx.room.withTransaction
import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.db.dao.DayExerciseWithExercise
import com.ckfit.app.data.db.dao.RoutineDayWithCount
import com.ckfit.app.data.db.dao.RoutineSummary
import com.ckfit.app.data.db.entity.DayExerciseEntity
import com.ckfit.app.data.db.entity.ExerciseEntity
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.data.db.entity.RoutineEntity
import com.ckfit.app.domain.model.MeasureType
import com.ckfit.app.domain.prescription.PrescriptionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val db: CKFitDatabase,
    private val weekRepository: WeekRepository,
    private val clock: Clock,
) {
    private val routineDao get() = db.routineDao()
    private val dayExerciseDao get() = db.dayExerciseDao()
    private val exerciseDao get() = db.exerciseDao()

    // --- Lectura ---

    fun observeActiveRoutine(): Flow<RoutineEntity?> = routineDao.observeActive()

    fun observeSummaries(): Flow<List<RoutineSummary>> = routineDao.observeSummaries()

    fun observeRoutine(id: Long): Flow<RoutineEntity?> = routineDao.observeRoutine(id)

    fun observeDays(routineId: Long): Flow<List<RoutineDayEntity>> = routineDao.observeDays(routineId)

    fun observeDaysWithCount(routineId: Long): Flow<List<RoutineDayWithCount>> = routineDao.observeDaysWithCount(routineId)

    fun observeDay(dayId: Long): Flow<RoutineDayEntity?> = routineDao.observeDay(dayId)

    fun observeDayExercises(dayId: Long): Flow<List<DayExerciseWithExercise>> = dayExerciseDao.observeForDay(dayId)

    /** Catálogo ordenado alfabéticamente en español (sin distinguir tildes ni mayúsculas). */
    fun observeCatalog(): Flow<List<ExerciseEntity>> {
        val collator = Collator.getInstance(Locale.forLanguageTag("es")).apply { strength = Collator.PRIMARY }
        return exerciseDao.observeAll().map { list -> list.sortedWith(compareBy(collator) { it.name }) }
    }

    suspend fun getDayExercise(id: Long): DayExerciseEntity? = dayExerciseDao.get(id)

    suspend fun getExercise(id: Long): ExerciseEntity? = exerciseDao.get(id)

    suspend fun getDay(id: Long): RoutineDayEntity? = routineDao.getDay(id)

    /** Cuántas prescripciones vivas usan el ejercicio (para avisar si se cambia su tipo). */
    suspend fun exerciseUsages(exerciseId: Long): Int = exerciseDao.countUsages(exerciseId)

    /** Peso actual del ejercicio (el mismo en todos los días que lo usan). */
    suspend fun exerciseWeight(exerciseId: Long): Double? = dayExerciseDao.exerciseWeight(exerciseId)

    // --- Planes ---

    /**
     * Plan nuevo: cada día se titula con su nombre ([dayNames], lunes primero) y empieza como descanso,
     * así activar un plan vacío no llena la semana de sesiones sin ejercicios.
     */
    suspend fun createRoutine(name: String, dayNames: List<String>, dayColor: String): Long = db.withTransaction {
        require(dayNames.size == 7) { "Se esperan 7 nombres de día" }
        val id = routineDao.insertRoutine(RoutineEntity(name = name.trim(), isActive = false, createdAt = clock.instant()))
        (1..7).forEach { dow ->
            routineDao.insertDay(
                RoutineDayEntity(
                    routineId = id,
                    dayOfWeek = dow,
                    title = dayNames[dow - 1],
                    colorHex = dayColor,
                    isOptional = false,
                    isRestDay = true,
                ),
            )
        }
        id
    }

    /** Copia profunda (días y prescripciones vivas, mismos ejercicios del catálogo); queda inactiva. */
    suspend fun duplicateRoutine(sourceId: Long, name: String): Long = db.withTransaction {
        val newId = routineDao.insertRoutine(RoutineEntity(name = name.trim(), isActive = false, createdAt = clock.instant()))
        routineDao.getDays(sourceId).forEach { day ->
            val newDayId = routineDao.insertDay(day.copy(id = 0, routineId = newId))
            dayExerciseDao.getForDay(day.id).forEachIndexed { index, item ->
                dayExerciseDao.insert(item.dayExercise.copy(id = 0, dayId = newDayId, position = index))
            }
        }
        newId
    }

    suspend fun renameRoutine(id: Long, name: String) = routineDao.rename(id, name.trim())

    suspend fun activateRoutine(id: Long) = db.withTransaction {
        routineDao.activate(id)
        weekRepository.syncCurrentWeek(today())
    }

    // --- Días ---

    suspend fun updateDay(day: RoutineDayEntity) = db.withTransaction {
        routineDao.updateDay(day.copy(title = day.title.trim()))
        // Pasar a descanso / entrenamiento u opcional cambia la cola de la semana si el plan está activo.
        if (routineDao.getRoutine(day.routineId)?.isActive == true) weekRepository.syncCurrentWeek(today())
    }

    // --- Prescripciones ---

    suspend fun addDayExercise(dayId: Long, exerciseId: Long, draft: PrescriptionDraft): Long = db.withTransaction {
        updateExerciseType(exerciseId, draft)
        val position = (dayExerciseDao.maxPosition(dayId) ?: -1) + 1
        val id = dayExerciseDao.insert(
            draft.toEntity(DayExerciseEntity(dayId = dayId, exerciseId = exerciseId, position = position, loadType = draft.loadType, sets = 1, restSec = 0)),
        )
        if (draft.usesWeight) dayExerciseDao.setExerciseWeight(exerciseId, draft.weightKg)
        id
    }

    suspend fun updateDayExercise(id: Long, draft: PrescriptionDraft) = db.withTransaction {
        val current = dayExerciseDao.get(id) ?: return@withTransaction
        updateExerciseType(current.exerciseId, draft)
        dayExerciseDao.update(draft.toEntity(current))
        // El peso es del ejercicio: mismo peso en todos los días y planes que lo usan.
        if (draft.usesWeight) dayExerciseDao.setExerciseWeight(current.exerciseId, draft.weightKg)
    }

    /** Quitar un ejercicio de un día lo archiva: el historial sigue apuntando a él. */
    suspend fun archiveDayExercise(id: Long) = dayExerciseDao.setArchived(id, true)

    suspend fun restoreDayExercise(id: Long) = db.withTransaction {
        val item = dayExerciseDao.get(id) ?: return@withTransaction
        dayExerciseDao.setArchived(id, false)
        dayExerciseDao.setPosition(id, (dayExerciseDao.maxPosition(item.dayId) ?: -1) + 1)
    }

    /** Guarda el orden de la lista tal como quedó tras arrastrar. */
    suspend fun reorder(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> dayExerciseDao.setPosition(id, index) }
    }

    // --- Catálogo ---

    suspend fun createExercise(
        name: String,
        muscleGroup: String,
        measureType: MeasureType,
        perSide: Boolean,
        usesWeight: Boolean = true,
    ): Long =
        exerciseDao.insert(
            ExerciseEntity(
                name = name.trim(),
                muscleGroup = muscleGroup.trim(),
                measureType = measureType,
                perSide = perSide,
                isCustom = true,
                usesWeight = usesWeight,
            ),
        )

    /** Tipo, "por lado" y "lleva peso" son del ejercicio del catálogo: aplican en todos los días que lo usan. */
    private suspend fun updateExerciseType(exerciseId: Long, draft: PrescriptionDraft) {
        val exercise = exerciseDao.get(exerciseId) ?: return
        if (exercise.measureType != draft.measureType || exercise.perSide != draft.perSide || exercise.usesWeight != draft.usesWeight) {
            exerciseDao.update(
                exercise.copy(measureType = draft.measureType, perSide = draft.perSide, usesWeight = draft.usesWeight),
            )
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}

/** Copia los campos del borrador sobre la prescripción; limpia lo que no aplica al tipo. */
private fun PrescriptionDraft.toEntity(base: DayExerciseEntity): DayExerciseEntity {
    val isReps = measureType == MeasureType.REPS
    return base.copy(
        loadType = loadType,
        sets = sets ?: base.sets,
        repsMin = if (isReps) repsMin else null,
        repsMax = if (isReps) repsMax else null,
        durationSec = if (isReps) null else durationSec,
        restSec = restSec ?: base.restSec,
        restMaxSec = restMaxSec,
        currentWeightKg = if (usesWeight) weightKg else base.currentWeightKg,
        weightStepKg = weightStepKg ?: base.weightStepKg,
        isIntensitySet = isIntensitySet,
        intensityNote = if (isIntensitySet) intensityNote?.trim()?.takeIf { it.isNotEmpty() } else null,
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    )
}
