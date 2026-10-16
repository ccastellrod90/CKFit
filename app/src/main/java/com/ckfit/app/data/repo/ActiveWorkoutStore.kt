package com.ckfit.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ckfit.app.domain.engine.EngineSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ejercicio en curso guardado para sobrevivir a la muerte del proceso. [savedAtElapsedMs] y
 * [savedAtWallMs] permiten detectar un reinicio del teléfono (el reloj monotónico vuelve a cero).
 */
@Serializable
data class ActiveWorkout(
    val sessionExerciseId: Long,
    val snapshot: EngineSnapshot,
    val savedAtElapsedMs: Long,
    val savedAtWallMs: Long,
)

@Singleton
class ActiveWorkoutStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): ActiveWorkout? = dataStore.data.first()[KEY]
        ?.let { runCatching { json.decodeFromString<ActiveWorkout>(it) }.getOrNull() }

    suspend fun save(active: ActiveWorkout) {
        dataStore.edit { it[KEY] = json.encodeToString(ActiveWorkout.serializer(), active) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("active_workout")
    }
}
