package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ckfit.app.domain.model.SessionExerciseStatus

/**
 * Orden real del día (el drag & drop toca esta tabla, no el plan).
 * [exerciseId] (v2) guarda qué ejercicio fue aunque la prescripción desaparezca del plan;
 * es nullable solo por la migración: las filas nuevas siempre lo llevan.
 */
@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DayExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayExerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("dayExerciseId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val dayExerciseId: Long?,
    val exerciseId: Long?,
    val position: Int,
    val status: SessionExerciseStatus,
)
