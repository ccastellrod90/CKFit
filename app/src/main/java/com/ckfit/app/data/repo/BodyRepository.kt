package com.ckfit.app.data.repo

import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.db.entity.BodyMeasurementEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyRepository @Inject constructor(
    private val db: CKFitDatabase,
) {
    /** Pesajes del más reciente al más antiguo. */
    fun observeAllDesc(): Flow<List<BodyMeasurementEntity>> = db.bodyMeasurementDao().observeAllDesc()
}
