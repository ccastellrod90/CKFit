package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ckfit.app.domain.model.LoadType

/** Prescripción de un ejercicio en un día del plan. Se archiva en vez de borrarse. */
@Entity(
    tableName = "day_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("dayId"), Index("exerciseId")],
)
data class DayExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: Long,
    val position: Int,
    val loadType: LoadType,
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val durationSec: Int? = null,
    val restSec: Int,
    val restMaxSec: Int? = null,
    /** Peso actual del ejercicio: igual en todas las prescripciones del mismo [exerciseId] (v4). */
    val currentWeightKg: Double? = null,
    val weightStepKg: Double = DEFAULT_WEIGHT_STEP_KG,
    val isIntensitySet: Boolean = false,
    val intensityNote: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
) {
    companion object {
        const val DEFAULT_WEIGHT_STEP_KG = 2.5
    }
}
