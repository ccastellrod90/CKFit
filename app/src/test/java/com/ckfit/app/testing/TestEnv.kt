package com.ckfit.app.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.repo.ActiveWorkoutStore
import com.ckfit.app.data.repo.BodyRepository
import com.ckfit.app.data.repo.ProfileRepository
import com.ckfit.app.data.repo.RoutineRepository
import com.ckfit.app.data.repo.SessionRepository
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.data.repo.WeekRepository
import com.ckfit.app.data.seed.AssetSeedSource
import com.ckfit.app.data.seed.SeedLoader
import com.ckfit.app.domain.usecase.ObserveHomeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Reloj que el test puede mover de día. */
class MutableClock(var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant

    fun setDate(date: LocalDate) {
        instant = date.atTime(10, 0).toInstant(ZoneOffset.UTC)
    }
}

/** BD en memoria + DataStore temporal + repositorios reales, para tests con Robolectric. */
class TestEnv(start: LocalDate = LocalDate.of(2026, 9, 21)) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val clock = MutableClock(start.atTime(10, 0).toInstant(ZoneOffset.UTC))
    val db: CKFitDatabase = Room.inMemoryDatabaseBuilder(context, CKFitDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStoreFile = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { dataStoreFile }
    val settings = SettingsRepository(dataStore)
    val activeStore = ActiveWorkoutStore(dataStore)

    val seedLoader = SeedLoader(db, AssetSeedSource(context), clock)
    val weekRepository = WeekRepository(db, settings)
    val routineRepository = RoutineRepository(db, weekRepository, clock)
    val sessionRepository = SessionRepository(db, settings, clock)
    val observeHome = ObserveHomeUseCase(
        routineRepository,
        weekRepository,
        sessionRepository,
        settings,
        BodyRepository(db),
        ProfileRepository(db),
    )

    fun today(): LocalDate = LocalDate.now(clock)

    fun close() {
        db.close()
        scope.cancel()
        dataStoreFile.delete()
    }
}
