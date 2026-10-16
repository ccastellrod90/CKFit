package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.ckfit.app.data.db.entity.DayExerciseEntity
import com.ckfit.app.data.db.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

data class DayExerciseWithExercise(
    @Embedded val dayExercise: DayExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

@Dao
interface DayExerciseDao {
    @Insert
    suspend fun insert(dayExercise: DayExerciseEntity): Long

    @Update
    suspend fun update(dayExercise: DayExerciseEntity)

    /** Solo para tests: la app archiva, nunca borra. */
    @Delete
    suspend fun delete(dayExercise: DayExerciseEntity)

    @Query("UPDATE day_exercise SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** El peso es del ejercicio: se actualiza en todos los días y planes que lo usan. */
    @Query("UPDATE day_exercise SET currentWeightKg = :weightKg WHERE exerciseId = :exerciseId")
    suspend fun setExerciseWeight(exerciseId: Long, weightKg: Double?)

    @Query("SELECT MAX(currentWeightKg) FROM day_exercise WHERE exerciseId = :exerciseId")
    suspend fun exerciseWeight(exerciseId: Long): Double?

    @Query("UPDATE day_exercise SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("SELECT MAX(position) FROM day_exercise WHERE dayId = :dayId AND archived = 0")
    suspend fun maxPosition(dayId: Long): Int?

    @Query("SELECT * FROM day_exercise WHERE id = :id")
    suspend fun get(id: Long): DayExerciseEntity?

    @Transaction
    @Query("SELECT * FROM day_exercise WHERE dayId = :dayId AND archived = 0 ORDER BY position")
    fun observeForDay(dayId: Long): Flow<List<DayExerciseWithExercise>>

    @Transaction
    @Query("SELECT * FROM day_exercise WHERE dayId = :dayId AND archived = 0 ORDER BY position")
    suspend fun getForDay(dayId: Long): List<DayExerciseWithExercise>

    @Query("SELECT COUNT(*) FROM day_exercise")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM day_exercise WHERE archived = 0")
    suspend fun countActive(): Int
}
