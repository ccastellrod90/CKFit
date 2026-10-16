package com.ckfit.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Pesaje. Peso en kg. */
@Entity(tableName = "body_measurement", indices = [Index("date")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
    val waistCm: Double? = null,
    val notes: String? = null,
)
