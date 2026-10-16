package com.ckfit.app.data.seed

import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Formato de assets/seed/nivel2.json (sección 4). rest = [restSec, restMaxSec].
@Serializable
private data class SeedFile(val routine: String, val days: List<SeedDay>)

@Serializable
private data class SeedDay(
    val dayOfWeek: Int,
    val title: String,
    val color: String? = null,
    val optional: Boolean = false,
    val restDay: Boolean = false,
    val exercises: List<SeedExercise> = emptyList(),
)

@Serializable
private data class SeedExercise(
    val name: String,
    val group: String,
    val type: MeasureType,
    val load: LoadType,
    val perSide: Boolean = false,
    val sets: Int,
    val reps: List<Int>? = null,
    val durationSec: Int? = null,
    val rest: List<Int> = listOf(0),
    val intensity: Boolean = false,
    val notes: String? = null,
)

// Formato de assets/seed/catalog.json: ejercicios del catálogo sin plan.
@Serializable
private data class SeedCatalogExercise(
    val name: String,
    val group: String,
    val type: MeasureType,
    val perSide: Boolean = false,
)

data class ParsedExercise(
    val name: String,
    val muscleGroup: String,
    val measureType: MeasureType,
    val perSide: Boolean,
)

data class ParsedPrescription(
    val exerciseName: String,
    val loadType: LoadType,
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val restSec: Int,
    val restMaxSec: Int?,
    val isIntensitySet: Boolean,
    val intensityNote: String?,
    val notes: String?,
)

data class ParsedDay(
    val dayOfWeek: Int,
    val title: String,
    val colorHex: String,
    val isOptional: Boolean,
    val isRestDay: Boolean,
    val prescriptions: List<ParsedPrescription>,
)

/** Plan listo para insertar; [exercises] ya viene sin repetidos (una fila por nombre). */
data class ParsedPlan(
    val name: String,
    val days: List<ParsedDay>,
    val exercises: List<ParsedExercise>,
)

object SeedParser {
    const val DEFAULT_INTENSITY_NOTE = "Última serie: rest-pause o drop set (alternar semanal)"
    const val REST_DAY_COLOR = "#8A9099"

    private val json = Json { ignoreUnknownKeys = false }

    fun parsePlan(text: String): ParsedPlan {
        val file = json.decodeFromString<SeedFile>(text)
        val exercises = LinkedHashMap<String, ParsedExercise>()
        val days = file.days.sortedBy { it.dayOfWeek }.map { day ->
            require(day.dayOfWeek in 1..7) { "dayOfWeek fuera de rango: ${day.dayOfWeek}" }
            ParsedDay(
                dayOfWeek = day.dayOfWeek,
                title = day.title,
                colorHex = day.color ?: REST_DAY_COLOR,
                isOptional = day.optional,
                isRestDay = day.restDay,
                prescriptions = day.exercises.map { ex ->
                    val parsed = ParsedExercise(ex.name, ex.group, ex.type, ex.perSide)
                    val previous = exercises.putIfAbsent(ex.name, parsed)
                    require(previous == null || previous == parsed) {
                        "El ejercicio '${ex.name}' aparece con definiciones distintas"
                    }
                    ex.toPrescription()
                },
            )
        }
        require(days.map { it.dayOfWeek }.toSet().size == days.size) { "Días repetidos en el plan" }
        return ParsedPlan(file.routine, days, exercises.values.toList())
    }

    fun parseCatalog(text: String): List<ParsedExercise> =
        json.decodeFromString<List<SeedCatalogExercise>>(text)
            .map { ParsedExercise(it.name, it.group, it.type, it.perSide) }

    private fun SeedExercise.toPrescription(): ParsedPrescription {
        require(rest.size in 1..2) { "rest debe ser [restSec] o [restSec, restMaxSec] en '$name'" }
        val isReps = type == MeasureType.REPS
        if (isReps) require(reps?.size == 2) { "reps debe ser [min, max] en '$name'" }
        else require(durationSec != null) { "durationSec es obligatorio en '$name'" }
        return ParsedPrescription(
            exerciseName = name,
            loadType = load,
            sets = sets,
            repsMin = if (isReps) reps!![0] else null,
            repsMax = if (isReps) reps!![1] else null,
            durationSec = if (isReps) null else durationSec,
            restSec = rest[0],
            restMaxSec = rest.getOrNull(1),
            isIntensitySet = intensity,
            intensityNote = if (intensity) DEFAULT_INTENSITY_NOTE else null,
            notes = notes,
        )
    }
}
