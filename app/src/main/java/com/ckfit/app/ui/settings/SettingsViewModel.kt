package com.ckfit.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ckfit.app.data.db.entity.UserProfileEntity
import com.ckfit.app.data.repo.PhoneOwnerNameSource
import com.ckfit.app.data.repo.ProfileRepository
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.data.repo.TrainingSettings
import com.ckfit.app.data.repo.AudioSettings
import com.ckfit.app.service.AudioCuePlayer
import com.ckfit.app.service.TtsStatus
import com.ckfit.app.service.VoiceStatus
import com.ckfit.app.service.VoskCommandSource
import com.ckfit.app.service.WorkoutService
import com.ckfit.app.domain.model.Sex
import com.ckfit.app.domain.model.WeightUnit
import com.ckfit.app.domain.model.ageOn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ProfileUiState(
    val loaded: Boolean = false,
    /** Textos de los campos tal como los escribe el usuario. */
    val nameText: String = "",
    val heightText: String = "",
    val heightError: Boolean = false,
    val birthDate: LocalDate? = null,
    val age: Int? = null,
    val sex: Sex? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.MIN,
)

sealed interface SettingsEvent {
    data object PhoneNameNotFound : SettingsEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val phoneOwnerName: PhoneOwnerNameSource,
    private val clock: Clock,
    private val voice: VoskCommandSource,
    private val player: AudioCuePlayer,
) : ViewModel() {

    private val heightInput = MutableStateFlow<String?>(null)
    private val nameInput = MutableStateFlow<String?>(null)

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val profile: StateFlow<ProfileUiState> = combine(
        profileRepository.observe(),
        heightInput,
        nameInput,
    ) { p, height, name ->
        val today = LocalDate.now(clock)
        val text = height ?: p.heightCm?.toString().orEmpty()
        ProfileUiState(
            loaded = true,
            nameText = name ?: p.name.orEmpty(),
            heightText = text,
            heightError = text.isNotEmpty() && text.toIntOrNull()?.let { it in HEIGHT_RANGE_CM } != true,
            birthDate = p.birthDate,
            age = p.birthDate?.let { ageOn(it, today) },
            sex = p.sex,
            unit = p.unit,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    val training: StateFlow<TrainingSettings> = settingsRepository.training
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingSettings())

    val audio: StateFlow<AudioSettings> = settingsRepository.audio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioSettings())

    val voiceStatus: StateFlow<VoiceStatus> = voice.status
    val lastHeard: StateFlow<String?> = voice.lastHeard
    val ttsStatus: StateFlow<TtsStatus> = player.ttsStatus

    /** La prueba de micrófono usa el mismo reconocedor que el entrenamiento: solo sin entrenamiento en marcha. */
    private val _micTest = MutableStateFlow(false)
    val micTest: StateFlow<Boolean> = _micTest
    val micTestAvailable: Boolean get() = !WorkoutService.running

    init {
        // El nombre se guarda con un pequeño retraso para no escribir en cada tecla.
        viewModelScope.launch {
            nameInput.filterNotNull().debounce(NAME_DEBOUNCE_MS).collect { name ->
                save { it.copy(name = name.trim().takeIf(String::isNotEmpty)) }
            }
        }
    }

    fun onNameChange(text: String) {
        nameInput.value = text.take(MAX_NAME_LENGTH)
    }

    /** Tras conceder Contactos: toma el nombre de la tarjeta «Yo», si existe. */
    fun importNameFromPhone() {
        viewModelScope.launch {
            val name = phoneOwnerName.firstName()
            if (name == null) {
                _events.send(SettingsEvent.PhoneNameNotFound)
            } else {
                nameInput.value = name
            }
        }
    }

    fun onHeightChange(text: String) {
        val digits = text.filter(Char::isDigit).take(3)
        heightInput.value = digits
        val cm = digits.toIntOrNull()
        when {
            digits.isEmpty() -> save { it.copy(heightCm = null) }
            cm != null && cm in HEIGHT_RANGE_CM -> save { it.copy(heightCm = cm) }
        }
    }

    fun onBirthDateChange(date: LocalDate?) = save { it.copy(birthDate = date) }

    fun onSexChange(sex: Sex?) = save { it.copy(sex = sex) }

    fun onUnitChange(unit: WeightUnit) = save { it.copy(unit = unit) }

    fun onAutoAdvanceChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoAdvance(value) }
    }

    fun onSwitchSideSecChange(value: Int) {
        viewModelScope.launch { settingsRepository.setSwitchSideSec(value) }
    }

    fun onRestCountdownChange(value: Int) {
        viewModelScope.launch { settingsRepository.setRestCountdownSec(value) }
    }

    fun onPreCountdownChange(value: Int) {
        viewModelScope.launch { settingsRepository.setPreCountdownSec(value) }
    }

    fun onHalfwayCueChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setHalfwayCue(value) }
    }

    fun updateAudio(transform: (AudioSettings) -> AudioSettings) {
        viewModelScope.launch { settingsRepository.updateAudio(transform) }
    }

    fun setMicTest(on: Boolean) {
        if (on && !micTestAvailable) return
        _micTest.value = on
        if (on) voice.start() else voice.stop()
    }

    /** "Probar voz": el primer uso inicia el motor de voz (tarda un momento). */
    fun testVoice(text: String) {
        player.prepare()
        player.speak(text)
    }

    override fun onCleared() {
        if (_micTest.value) voice.stop()
    }

    private fun save(transform: (UserProfileEntity) -> UserProfileEntity) {
        viewModelScope.launch { profileRepository.update(transform) }
    }

    companion object {
        val HEIGHT_RANGE_CM = 50..250
        const val MAX_NAME_LENGTH = 30
        private const val NAME_DEBOUNCE_MS = 400L
    }
}
