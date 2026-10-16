package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ckfit.app.data.db.entity.RoutineDayEntity
import com.ckfit.app.data.db.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

data class RoutineSummary(
    @Embedded val routine: RoutineEntity,
    val trainingDays: Int,
    val optionalDays: Int,
)

data class RoutineDayWithCount(
    @Embedded val day: RoutineDayEntity,
    val exerciseCount: Int,
)

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    suspend fun insertDay(day: RoutineDayEntity): Long

    @Update
    suspend fun updateDay(day: RoutineDayEntity)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Query("UPDATE routine SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** Deja activo solo [id]. */
    @Query("UPDATE routine SET isActive = (id = :id)")
    suspend fun activate(id: Long)

    @Query("SELECT COUNT(*) FROM routine")
    suspend fun count(): Int

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutine(id: Long): RoutineEntity?

    @Query("SELECT * FROM routine WHERE id = :id")
    fun observeRoutine(id: Long): Flow<RoutineEntity?>

    @Query(
        """
        SELECT r.*,
            (SELECT COUNT(*) FROM routine_day d WHERE d.routineId = r.id AND d.isRestDay = 0) AS trainingDays,
            (SELECT COUNT(*) FROM routine_day d WHERE d.routineId = r.id AND d.isRestDay = 0 AND d.isOptional = 1) AS optionalDays
        FROM routine r
        ORDER BY r.isActive DESC, r.createdAt, r.id
        """,
    )
    fun observeSummaries(): Flow<List<RoutineSummary>>

    @Query("SELECT * FROM routine WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): RoutineEntity?

    @Query("SELECT * FROM routine_day WHERE routineId = :routineId ORDER BY dayOfWeek")
    fun observeDays(routineId: Long): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_day WHERE routineId = :routineId ORDER BY dayOfWeek")
    suspend fun getDays(routineId: Long): List<RoutineDayEntity>

    @Query(
        """
        SELECT d.*, (SELECT COUNT(*) FROM day_exercise e WHERE e.dayId = d.id AND e.archived = 0) AS exerciseCount
        FROM routine_day d WHERE d.routineId = :routineId ORDER BY d.dayOfWeek
        """,
    )
    fun observeDaysWithCount(routineId: Long): Flow<List<RoutineDayWithCount>>

    @Query("SELECT * FROM routine_day WHERE id = :dayId")
    suspend fun getDay(dayId: Long): RoutineDayEntity?

    @Query("SELECT * FROM routine_day WHERE id = :dayId")
    fun observeDay(dayId: Long): Flow<RoutineDayEntity?>
}
