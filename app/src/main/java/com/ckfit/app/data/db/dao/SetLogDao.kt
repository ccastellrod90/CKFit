package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ckfit.app.data.db.entity.SetLogEntity
import kotlinx.coroutines.flow.Flow

/** Series cerradas de un ejercicio de la sesión (las dos filas de una serie por lado cuentan una vez). */
data class SetCount(val sessionExerciseId: Long, val sets: Int)

@Dao
interface SetLogDao {
    @Insert
    suspend fun insert(log: SetLogEntity): Long

    @Insert
    suspend fun insertAll(logs: List<SetLogEntity>)

    @Query("SELECT * FROM set_log WHERE sessionExerciseId = :sessionExerciseId ORDER BY setNumber, side")
    suspend fun getForSessionExercise(sessionExerciseId: Long): List<SetLogEntity>

    /** Última serie guardada; para reanudar desde la siguiente si el proceso murió. */
    @Query("SELECT MAX(setNumber) FROM set_log WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun lastSetNumber(sessionExerciseId: Long): Int?

    @Query(
        """
        SELECT sessionExerciseId, COUNT(DISTINCT setNumber) AS sets FROM set_log
        WHERE sessionExerciseId IN (SELECT id FROM session_exercise WHERE sessionId = :sessionId)
        GROUP BY sessionExerciseId
        """,
    )
    fun observeSetCounts(sessionId: Long): Flow<List<SetCount>>

    /** Peso de la última serie con peso de este ejercicio en una sesión anterior (para el aviso de "menos peso"). */
    @Query(
        """
        SELECT sl.weightKg FROM set_log sl JOIN session_exercise se ON se.id = sl.sessionExerciseId
        WHERE se.exerciseId = :exerciseId AND se.sessionId != :currentSessionId AND sl.weightKg IS NOT NULL
        ORDER BY sl.completedAt DESC, sl.id DESC LIMIT 1
        """,
    )
    suspend fun previousWeight(exerciseId: Long, currentSessionId: Long): Double?

    @Query(
        "SELECT COUNT(*) FROM set_log WHERE sessionExerciseId IN (SELECT id FROM session_exercise WHERE sessionId = :sessionId)",
    )
    suspend fun countForSession(sessionId: Long): Int
}
