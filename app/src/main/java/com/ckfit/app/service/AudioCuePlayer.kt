package com.ckfit.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ckfit.app.data.repo.AudioSettings
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.di.ApplicationScope
import com.ckfit.app.domain.engine.AudioCue
import com.ckfit.app.domain.engine.BeepType
import com.ckfit.app.domain.voice.SpokenText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class TtsStatus { NOT_STARTED, READY, NO_SPANISH_VOICE, ERROR }

/**
 * Traduce los [AudioCue] del motor a voz (TextToSpeech en español), bips (ToneGenerator) y vibración.
 * La voz usa el uso "guía de navegación" y pide foco transitorio con ducking: la música baja mientras habla.
 */
@Singleton
class AudioCuePlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) : CueSink {

    @Volatile
    private var settings = AudioSettings()

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()

    private var tts: TextToSpeech? = null
    private val _ttsStatus = MutableStateFlow(TtsStatus.NOT_STARTED)
    val ttsStatus: StateFlow<TtsStatus> = _ttsStatus

    /** true mientras la voz habla: el reconocedor ignora ese audio (no se oye a sí mismo). */
    private val pending = AtomicInteger(0)
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private var toneGenerator: ToneGenerator? = null

    /** Frases pedidas mientras la voz se inicia; se dicen en cuanto está lista. */
    private val waiting = mutableListOf<String>()

    init {
        scope.launch { settingsRepository.audio.collect { settings = it } }
    }

    /** Arranca el motor de voz (tarda un poco): se llama al iniciar el servicio o al probar la voz. */
    fun prepare() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                _ttsStatus.value = TtsStatus.ERROR
                return@TextToSpeech
            }
            engine.setAudioAttributes(speechAttributes)
            engine.setOnUtteranceProgressListener(progressListener)
            _ttsStatus.value = if (selectSpanish(engine)) TtsStatus.READY else TtsStatus.NO_SPANISH_VOICE
            val queued = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
            queued.forEach(::speak)
        }
    }

    /** Preferencia: español latinoamericano (es-US / es-419 / es-MX), luego España, luego cualquier "es". */
    private fun selectSpanish(engine: TextToSpeech): Boolean {
        val candidates = listOf("es-US", "es-419", "es-MX", "es-ES", "es").map(Locale::forLanguageTag)
        return candidates.any { locale ->
            engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE &&
                engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun play(cue: AudioCue) {
        val s = settings
        if (s.vibration && cue.isStateChange()) vibrate()
        when (cue) {
            is AudioCue.Beep -> if (s.beeps) beep(cue.type)
            else -> if (s.tts) SpokenText.of(cue)?.let(::speak)
        }
    }

    /** Frase suelta (p. ej. "Probar voz" en Ajustes). */
    fun speak(text: String) {
        val engine = tts
        if (engine == null || _ttsStatus.value == TtsStatus.NOT_STARTED) {
            synchronized(waiting) { if (waiting.size < MAX_WAITING) waiting += text }
            prepare()
            return
        }
        if (_ttsStatus.value != TtsStatus.READY) return
        if (pending.getAndIncrement() == 0) {
            _speaking.value = true
            if (settings.ducking) audioManager.requestAudioFocus(focusRequest)
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume) }
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, params, "ckfit-${System.nanoTime()}")
        if (result != TextToSpeech.SUCCESS) utteranceFinished()
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = utteranceFinished()

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = utteranceFinished()
        override fun onError(utteranceId: String?, errorCode: Int) = utteranceFinished()
        override fun onStop(utteranceId: String?, interrupted: Boolean) = utteranceFinished()
    }

    private fun utteranceFinished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            _speaking.value = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun beep(type: BeepType) {
        val generator = toneGenerator ?: runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, (settings.volume * ToneGenerator.MAX_VOLUME).toInt())
        }.getOrNull()?.also { toneGenerator = it } ?: return
        when (type) {
            BeepType.SHORT -> generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
            BeepType.DOUBLE -> generator.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS * 2)
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Cambios de estado que merecen vibración: empieza serie, empieza/termina descanso, cambio de lado, fin. */
    private fun AudioCue.isStateChange() = when (this) {
        is AudioCue.SetStart, AudioCue.Go, AudioCue.SwitchSide, is AudioCue.Beep, is AudioCue.ExerciseFinished -> true
        else -> false
    }

    /** Al terminar el entrenamiento: suelta la voz y los bips. */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _ttsStatus.value = TtsStatus.NOT_STARTED
        synchronized(waiting) { waiting.clear() }
        pending.set(0)
        _speaking.value = false
        audioManager.abandonAudioFocusRequest(focusRequest)
        toneGenerator?.release()
        toneGenerator = null
    }

    private companion object {
        const val BEEP_MS = 150
        const val VIBRATION_MS = 80L
        const val MAX_WAITING = 3
    }
}
