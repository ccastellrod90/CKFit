package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ckfit.app.domain.model.SessionStatus
import java.time.Instant
import java.time.LocalDate

/** Sesión real. Cambiar o borrar el plan no la borra (SET NULL). */
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = PlannedSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("plannedSessionId"), Index("routineDayId"), Index("status")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plannedSessionId: Long?,
    val routineDayId: Long?,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val status: SessionStatus,
)
