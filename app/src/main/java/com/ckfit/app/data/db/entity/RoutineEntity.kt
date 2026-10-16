package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** Un plan (p. ej. "Nivel 2"). Solo uno activo. */
@Entity(tableName = "routine", indices = [Index("isActive")])
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean,
    val createdAt: Instant,
)
