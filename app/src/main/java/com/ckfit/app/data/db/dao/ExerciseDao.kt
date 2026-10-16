package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ckfit.app.data.db.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name")
    suspend fun getAll(): List<ExerciseEntity>

    /** Sin orden: se ordena en Kotlin con Collator (español, sin distinguir tildes). */
    @Query("SELECT * FROM exercise WHERE archived = 0")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ExerciseEntity?

    /** Prescripciones vivas (no archivadas) que usan el ejercicio, en cualquier plan. */
    @Query("SELECT COUNT(*) FROM day_exercise WHERE exerciseId = :exerciseId AND archived = 0")
    suspend fun countUsages(exerciseId: Long): Int
}
