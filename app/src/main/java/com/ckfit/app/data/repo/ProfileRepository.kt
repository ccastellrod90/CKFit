package com.ckfit.app.data.repo

import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val db: CKFitDatabase,
) {
    fun observe(): Flow<UserProfileEntity> = db.profileDao().observe().map { it ?: UserProfileEntity() }

    suspend fun update(transform: (UserProfileEntity) -> UserProfileEntity) {
        val current = db.profileDao().get() ?: UserProfileEntity()
        db.profileDao().upsert(transform(current).copy(id = UserProfileEntity.SINGLE_ID))
    }
}
