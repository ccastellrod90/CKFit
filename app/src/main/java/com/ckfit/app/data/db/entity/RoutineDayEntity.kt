package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Día del plan; dayOfWeek 1 = lunes … 7 = domingo. */
@Entity(
    tableName = "routine_day",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["routineId", "dayOfWeek"], unique = true)],
)
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val dayOfWeek: Int,
    val title: String,
    val colorHex: String,
    val isOptional: Boolean,
    val isRestDay: Boolean,
)
