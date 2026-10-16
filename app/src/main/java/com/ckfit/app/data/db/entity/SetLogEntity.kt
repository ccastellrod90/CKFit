package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ckfit.app.domain.model.Side
import java.time.Instant

/** Una fila por serie (y por lado). Peso en kg. */
@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setNumber: Int,
    val side: Side? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val completedAt: Instant,
)
