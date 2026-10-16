package com.ckfit.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ckfit.app.domain.engine.EngineEvent
import com.ckfit.app.domain.voice.VoiceCommands
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceStatus { OFF, NO_PERMISSION, LOADING, LISTENING, ERROR }

/** Fuente de comandos de voz (sección 8). Los botones y la voz disparan los mismos [EngineEvent]. */
interface VoiceCommandSource {
    fun start()
    fun stop()
    val commands: Flow<EngineEvent>
}

/**
 * Vosk sin conexión con gramática cerrada. Solo se usan resultados finales (onResult/onFinalResult),
 * nunca parciales; el mismo comando repetido en menos de 800 ms se ignora.
 */
@Singleton
class VoskCommandSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VoiceCommandSource {

    private val _commands = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val commands: Flow<EngineEvent> = _commands

    private val _status = MutableStateFlow(VoiceStatus.OFF)
    val status: StateFlow<VoiceStatus> = _status

    /** Última frase reconocida (también "[unk]"), para la prueba de micrófono de Ajustes. */
    private val _lastHeard = MutableStateFlow<String?>(null)
    val lastHeard: StateFlow<String?> = _lastHeard

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var wanted = false
    private var paused = false

    private var lastCommand: EngineEvent? = null
    private var lastCommandAt = 0L

    private val json = Json { ignoreUnknownKeys = true }

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    override fun start() {
        wanted = true
        if (!hasPermission()) {
            _status.value = VoiceStatus.NO_PERMISSION
            return
        }
        if (speechService != null) return
        val loaded = model
        if (loaded != null) {
            listen(loaded)
            return
        }
        if (_status.value == VoiceStatus.LOADING) return
        _status.value = VoiceStatus.LOADING
        // La primera vez copia el modelo de assets a almacenamiento (unos segundos); después, solo si cambió.
        StorageService.unpack(
            context,
            MODEL_ASSET_DIR,
            MODEL_TARGET_DIR,
            { m ->
                synchronized(this) {
                    model = m
                    if (wanted) listen(m) else _status.value = VoiceStatus.OFF
                }
            },
            { _ -> _status.value = VoiceStatus.ERROR },
        )
    }

    private fun listen(m: Model) {
        runCatching {
            val rec = Recognizer(m, SAMPLE_RATE, VoiceCommands.grammarJson)
            val service = SpeechService(rec, SAMPLE_RATE)
            service.startListening(listener)
            service.setPause(paused)
            recognizer = rec
            speechService = service
        }.onSuccess {
            _status.value = VoiceStatus.LISTENING
        }.onFailure {
            _status.value = VoiceStatus.ERROR
        }
    }

    @Synchronized
    override fun stop() {
        wanted = false
        speechService?.let {
            it.stop()
            it.shutdown()
        }
        speechService = null
        recognizer?.close()
        recognizer = null
        _status.value = if (hasPermission()) VoiceStatus.OFF else VoiceStatus.NO_PERMISSION
    }

    /** Mientras habla la voz de la app no se escucha (el micrófono la oiría). */
    @Synchronized
    fun setPaused(value: Boolean) {
        paused = value
        speechService?.setPause(value)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = Unit // se ignoran los parciales
        override fun onResult(hypothesis: String?) = handle(hypothesis)
        override fun onFinalResult(hypothesis: String?) = handle(hypothesis)
        override fun onError(exception: Exception?) {
            _status.value = VoiceStatus.ERROR
        }
        override fun onTimeout() = Unit
    }

    private fun handle(hypothesis: String?) {
        if (paused) return
        val text = hypothesis?.let {
            runCatching { json.parseToJsonElement(it).jsonObject["text"]?.jsonPrimitive?.content }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: return
        _lastHeard.value = text
        val event = VoiceCommands.parse(text) ?: return
        val now = SystemClock.elapsedRealtime()
        if (event == lastCommand && now - lastCommandAt < DEBOUNCE_MS) return
        lastCommand = event
        lastCommandAt = now
        _commands.tryEmit(event)
    }

    private companion object {
        const val SAMPLE_RATE = 16000f
        const val DEBOUNCE_MS = 800L
        /** Carpeta en assets del módulo :models (con su uuid) y destino en el almacenamiento de la app. */
        const val MODEL_ASSET_DIR = "model-es"
        const val MODEL_TARGET_DIR = "model"
    }
}
