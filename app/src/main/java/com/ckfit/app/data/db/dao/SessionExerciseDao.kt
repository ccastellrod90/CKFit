package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.ckfit.app.data.db.entity.DayExerciseEntity
import com.ckfit.app.data.db.entity.ExerciseEntity
import com.ckfit.app.data.db.entity.SessionExerciseEntity
import com.ckfit.app.domain.model.SessionExerciseStatus
import kotlinx.coroutines.flow.Flow

/** Progreso de una sesión: ejercicios cumplidos o saltados sobre el total. */
data class SessionProgress(val finished: Int, val total: Int)

/** Ejercicio de la sesión con su prescripción (null si ya no existe) y su ejercicio del catálogo. */
data class SessionExerciseDetail(
    @Embedded val item: SessionExerciseEntity,
    @Relation(parentColumn = "dayExerciseId", entityColumn = "id")
    val dayExercise: DayExerciseEntity?,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity?,
)

@Dao
interface SessionExerciseDao {
    @Insert
    suspend fun insert(item: SessionExerciseEntity): Long

    @Insert
    suspend fun insertAll(items: List<SessionExerciseEntity>)

    @Query("SELECT * FROM session_exercise WHERE id = :id")
    suspend fun get(id: Long): SessionExerciseEntity?

    @Transaction
    @Query("SELECT * FROM session_exercise WHERE id = :id")
    suspend fun getDetail(id: Long): SessionExerciseDetail?

    @Transaction
    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position, id")
    fun observeDetails(sessionId: Long): Flow<List<SessionExerciseDetail>>

    @Transaction
    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position, id")
    suspend fun getDetails(sessionId: Long): List<SessionExerciseDetail>

    @Query("UPDATE session_exercise SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: SessionExerciseStatus)

    @Query("UPDATE session_exercise SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Al terminar la sesión, lo que quedó sin hacer cuenta como saltado. */
    @Query("UPDATE session_exercise SET status = 'SKIPPED' WHERE sessionId = :sessionId AND status = 'PENDING'")
    suspend fun skipPending(sessionId: Long)

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN status IN ('DONE', 'SKIPPED') THEN 1 ELSE 0 END), 0) AS finished,
               COUNT(*) AS total
        FROM session_exercise WHERE sessionId = :sessionId
        """,
    )
    fun observeProgress(sessionId: Long): Flow<SessionProgress>
}
