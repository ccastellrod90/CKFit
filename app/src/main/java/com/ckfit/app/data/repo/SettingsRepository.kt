package com.ckfit.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Ajustes que usa el motor de entrenamiento (sección 5: Entrenamiento y Cuentas atrás). */
data class TrainingSettings(
    /** Siguiente serie: automático (arranca solo tras el descanso) o manual (espera "empezar"). */
    val autoAdvance: Boolean = true,
    val switchSideSec: Int = SettingsRepository.DEFAULT_SWITCH_SIDE_SEC,
    /** Cuenta final del descanso desde N s. */
    val restCountdownSec: Int = 5,
    /** Cuenta previa a ejercicios por tiempo. */
    val preCountdownSec: Int = 3,
    /** Aviso a mitad de serie por tiempo. */
    val halfwayCue: Boolean = false,
)

/** Ajustes de audio y voz (sección 5: Voz y Audio). */
data class AudioSettings(
    val voiceCommands: Boolean = true,
    val tts: Boolean = true,
    /** Volumen propio de la voz, 0..1 (independiente del volumen del sistema dentro de su canal). */
    val volume: Float = 1f,
    val beeps: Boolean = true,
    /** Bajar la música mientras habla. */
    val ducking: Boolean = true,
    val vibration: Boolean = false,
)

/** Ajustes en DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val training: Flow<TrainingSettings> = dataStore.data.map {
        TrainingSettings(
            autoAdvance = it[AUTO_ADVANCE] ?: true,
            switchSideSec = it[SWITCH_SIDE_SEC] ?: DEFAULT_SWITCH_SIDE_SEC,
            restCountdownSec = it[REST_COUNTDOWN_SEC] ?: 5,
            preCountdownSec = it[PRE_COUNTDOWN_SEC] ?: 3,
            halfwayCue = it[HALFWAY_CUE] ?: false,
        )
    }.distinctUntilChanged()

    val switchSideSec: Flow<Int> = training.map { it.switchSideSec }.distinctUntilChanged()

    suspend fun setSwitchSideSec(value: Int) {
        dataStore.edit { it[SWITCH_SIDE_SEC] = value.coerceIn(0, 60) }
    }

    suspend fun setAutoAdvance(value: Boolean) {
        dataStore.edit { it[AUTO_ADVANCE] = value }
    }

    suspend fun setRestCountdownSec(value: Int) {
        dataStore.edit { it[REST_COUNTDOWN_SEC] = value.coerceIn(0, 10) }
    }

    suspend fun setPreCountdownSec(value: Int) {
        dataStore.edit { it[PRE_COUNTDOWN_SEC] = value.coerceIn(0, 10) }
    }

    suspend fun setHalfwayCue(value: Boolean) {
        dataStore.edit { it[HALFWAY_CUE] = value }
    }

    val audio: Flow<AudioSettings> = dataStore.data.map { it.toAudio() }.distinctUntilChanged()

    suspend fun updateAudio(transform: (AudioSettings) -> AudioSettings) {
        dataStore.edit {
            val next = transform(it.toAudio())
            it[VOICE_COMMANDS] = next.voiceCommands
            it[TTS] = next.tts
            it[VOLUME] = next.volume.coerceIn(0f, 1f)
            it[BEEPS] = next.beeps
            it[DUCKING] = next.ducking
            it[VIBRATION] = next.vibration
        }
    }

    private fun Preferences.toAudio() = AudioSettings(
        voiceCommands = this[VOICE_COMMANDS] ?: true,
        tts = this[TTS] ?: true,
        volume = this[VOLUME] ?: 1f,
        beeps = this[BEEPS] ?: true,
        ducking = this[DUCKING] ?: true,
        vibration = this[VIBRATION] ?: false,
    )

    /** Estado de la cola semanal, no un ajuste visible: la sesión elegida con "Elegir otra sesión". */
    val chosenPlannedId: Flow<Long?> = dataStore.data.map { it[CHOSEN_PLANNED_ID] }

    suspend fun setChosenPlannedId(id: Long?) {
        dataStore.edit { if (id == null) it.remove(CHOSEN_PLANNED_ID) else it[CHOSEN_PLANNED_ID] = id }
    }

    companion object {
        const val DEFAULT_SWITCH_SIDE_SEC = 5
        private val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        private val SWITCH_SIDE_SEC = intPreferencesKey("switch_side_sec")
        private val REST_COUNTDOWN_SEC = intPreferencesKey("rest_countdown_sec")
        private val PRE_COUNTDOWN_SEC = intPreferencesKey("pre_countdown_sec")
        private val HALFWAY_CUE = booleanPreferencesKey("halfway_cue")
        private val CHOSEN_PLANNED_ID = longPreferencesKey("chosen_planned_id")
        private val VOICE_COMMANDS = booleanPreferencesKey("voice_commands")
        private val TTS = booleanPreferencesKey("tts")
        private val VOLUME = floatPreferencesKey("tts_volume")
        private val BEEPS = booleanPreferencesKey("beeps")
        private val DUCKING = booleanPreferencesKey("ducking")
        private val VIBRATION = booleanPreferencesKey("vibration")
    }
}
