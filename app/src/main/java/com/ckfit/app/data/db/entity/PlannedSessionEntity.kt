package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ckfit.app.domain.model.PlannedStatus
import java.time.LocalDate

/** Cola semanal: una fila por día no-descanso del plan activo. */
@Entity(
    tableName = "planned_session",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("weekStart"), Index("routineDayId"), Index("sessionId")],
)
data class PlannedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: LocalDate,
    val routineDayId: Long?,
    val plannedDate: LocalDate,
    val status: PlannedStatus,
    val skipReason: String? = null,
    val sessionId: Long? = null,
)
