package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ckfit.app.data.db.entity.WorkoutSessionEntity
import com.ckfit.app.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WorkoutSessionDao {
    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getInProgress(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun get(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutSessionEntity?>

    @Query("UPDATE workout_session SET status = :status, endedAt = :endedAt WHERE id = :id")
    suspend fun close(id: Long, status: SessionStatus, endedAt: Instant)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun delete(id: Long)
}
