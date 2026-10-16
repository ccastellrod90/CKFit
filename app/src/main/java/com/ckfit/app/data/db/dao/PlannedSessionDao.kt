package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.ckfit.app.data.db.entity.PlannedSessionEntity
import com.ckfit.app.domain.model.PlannedStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Sesión planificada con los datos de su día (null si el día ya no existe). */
data class PlannedSessionWithDay(
    @Embedded val planned: PlannedSessionEntity,
    val dayOfWeek: Int?,
    val dayTitle: String?,
    val dayColorHex: String?,
    val dayOptional: Boolean?,
)

@Dao
interface PlannedSessionDao {
    @Insert
    suspend fun insertAll(items: List<PlannedSessionEntity>)

    @Query(
        """
        SELECT ps.*, rd.dayOfWeek AS dayOfWeek, rd.title AS dayTitle,
               rd.colorHex AS dayColorHex, rd.isOptional AS dayOptional
        FROM planned_session ps LEFT JOIN routine_day rd ON rd.id = ps.routineDayId
        WHERE ps.weekStart = :weekStart
        ORDER BY ps.plannedDate, ps.id
        """,
    )
    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedSessionWithDay>>

    @Query(
        """
        SELECT ps.*, rd.dayOfWeek AS dayOfWeek, rd.title AS dayTitle,
               rd.colorHex AS dayColorHex, rd.isOptional AS dayOptional
        FROM planned_session ps LEFT JOIN routine_day rd ON rd.id = ps.routineDayId
        WHERE ps.weekStart = :weekStart
        ORDER BY ps.plannedDate, ps.id
        """,
    )
    suspend fun getWeek(weekStart: LocalDate): List<PlannedSessionWithDay>

    @Query(
        """
        SELECT ps.*, rd.dayOfWeek AS dayOfWeek, rd.title AS dayTitle,
               rd.colorHex AS dayColorHex, rd.isOptional AS dayOptional
        FROM planned_session ps LEFT JOIN routine_day rd ON rd.id = ps.routineDayId
        WHERE ps.weekStart < :weekStart AND ps.status = 'PENDING'
        """,
    )
    suspend fun getPendingBefore(weekStart: LocalDate): List<PlannedSessionWithDay>

    @Query("SELECT * FROM planned_session WHERE id = :id")
    suspend fun get(id: Long): PlannedSessionEntity?

    @Query("UPDATE planned_session SET status = :status WHERE id IN (:ids)")
    suspend fun setStatus(ids: List<Long>, status: PlannedStatus)

    @Query("UPDATE planned_session SET status = :status, skipReason = :reason WHERE id = :id")
    suspend fun setStatusAndReason(id: Long, status: PlannedStatus, reason: String?)

    @Query("UPDATE planned_session SET sessionId = :sessionId WHERE id = :id")
    suspend fun setSession(id: Long, sessionId: Long?)

    @Query("DELETE FROM planned_session WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT * FROM planned_session ORDER BY plannedDate, id")
    suspend fun getAll(): List<PlannedSessionEntity>
}
