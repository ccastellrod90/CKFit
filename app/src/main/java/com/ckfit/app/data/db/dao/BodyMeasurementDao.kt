package com.ckfit.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ckfit.app.data.db.entity.BodyMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {
    @Insert
    suspend fun insert(measurement: BodyMeasurementEntity): Long

    @Query("SELECT * FROM body_measurement ORDER BY date DESC, id DESC")
    fun observeAllDesc(): Flow<List<BodyMeasurementEntity>>
}
