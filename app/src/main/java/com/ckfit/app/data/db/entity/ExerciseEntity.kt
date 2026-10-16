package com.ckfit.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ckfit.app.domain.model.MeasureType

/**
 * Catálogo de ejercicios. Nunca se borra: se archiva para no romper el historial.
 * [usesWeight] (v4): false en los de peso corporal (planchas, dominadas…): no se muestra peso.
 */
@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val measureType: MeasureType,
    val perSide: Boolean,
    val isCustom: Boolean,
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "1") val usesWeight: Boolean = true,
)
