package com.ckfit.app.domain.usecase

import android.content.Context
import com.ckfit.app.data.repo.WeekRepository
import com.ckfit.app.data.seed.SeedLoader
import com.ckfit.app.service.WorkoutController
import com.ckfit.app.service.WorkoutService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Se llama al abrir la app y al volver a ella (con la app visible): seed (una vez), cola de la semana
 * actual y, si el proceso murió con un ejercicio en marcha, lo retoma con su servicio.
 */
@Singleton
class AppInitializer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val seedLoader: SeedLoader,
    private val weekRepository: WeekRepository,
    private val controller: WorkoutController,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private var seeded = false

    suspend fun prepare() = mutex.withLock {
        if (!seeded) {
            seedLoader.seedIfEmpty()
            seeded = true
        }
        weekRepository.ensureCurrentWeek(LocalDate.now(clock))
        if (controller.restoreIfNeeded()) WorkoutService.start(context)
    }
}
