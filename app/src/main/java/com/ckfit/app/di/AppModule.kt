package com.ckfit.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.ckfit.app.data.db.CKFitDatabase
import com.ckfit.app.data.seed.AssetSeedSource
import com.ckfit.app.data.seed.SeedSource
import com.ckfit.app.service.CueSink
import com.ckfit.app.service.AudioCuePlayer
import com.ckfit.app.service.VoiceCommandSource
import com.ckfit.app.service.VoskCommandSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.os.SystemClock
import com.ckfit.app.domain.engine.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CKFitDatabase =
        Room.databaseBuilder(context, CKFitDatabase::class.java, CKFitDatabase.NAME)
            .addMigrations(*CKFitDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /** Reloj del motor: sigue contando con la pantalla apagada y no salta con cambios de hora. */
    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = TimeSource { SystemClock.elapsedRealtime() }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** Scope que vive lo que el proceso: el entrenamiento en curso no depende de ninguna pantalla. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindSeedSource(impl: AssetSeedSource): SeedSource

    @Binds
    abstract fun bindCueSink(impl: AudioCuePlayer): CueSink

    @Binds
    abstract fun bindVoiceCommandSource(impl: VoskCommandSource): VoiceCommandSource
}
