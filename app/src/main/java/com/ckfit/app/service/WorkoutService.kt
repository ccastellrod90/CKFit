package com.ckfit.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ckfit.app.data.repo.SettingsRepository
import com.ckfit.app.domain.engine.EngineEvent
import com.ckfit.app.domain.engine.TimeSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio en primer plano mientras hay un entrenamiento: mantiene vivo el proceso y el motor con la
 * pantalla apagada (wake lock parcial), habla los avisos, escucha los comandos de voz y muestra la
 * notificación con el estado y las acciones.
 *
 * Tipo mediaPlayback siempre (los avisos son audio) + microphone solo si hay permiso y voz activada:
 * Android 14+ exige RECORD_AUDIO concedido antes de pedir el tipo microphone.
 * Si el sistema mata el proceso, START_STICKY lo relanza y el ejercicio se retoma donde iba.
 */
@AndroidEntryPoint
class WorkoutService : Service() {

    @Inject lateinit var controller: WorkoutController

    @Inject lateinit var notifications: WorkoutNotifications

    @Inject lateinit var player: AudioCuePlayer

    @Inject lateinit var voice: VoskCommandSource

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var timeSource: TimeSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var voiceWanted = false

    override fun onCreate() {
        super.onCreate()
        running = true
        notifications.ensureChannel()
        startInForeground()
        player.prepare()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        scope.launch {
            // Solo se redibuja cuando cambia algo visible (fase, serie, peso…); los segundos los cuenta el cronómetro.
            controller.state.filterNotNull()
                .map { notifications.contentKey(it) to it }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (_, state) -> notifications.show(state) }
        }
        scope.launch { voice.commands.collect(controller::send) }
        // No se escucha mientras habla la propia app.
        scope.launch { player.speaking.collect(voice::setPaused) }
        scope.launch { settings.audio.map { it.voiceCommands }.distinctUntilChanged().collect { updateVoice() } }
        scope.launch {
            while (isActive) {
                updateVoice()
                delay(VOICE_CHECK_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android exige llamar a startForeground poco después de startForegroundService.
        if (!startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            null -> scope.launch {
                // Relanzado por el sistema tras matar el proceso: se retoma el ejercicio guardado.
                if (!controller.restoreIfNeeded()) stopSelf()
            }
            ACTION_START -> controller.send(EngineEvent.Start)
            ACTION_DONE -> controller.send(EngineEvent.Done)
            ACTION_PAUSE -> controller.send(EngineEvent.Pause)
            ACTION_RESUME -> controller.send(EngineEvent.Resume)
            ACTION_REFRESH -> scope.launch { updateVoice() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        scope.cancel()
        voice.stop()
        player.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    /** Escucha si la voz está activada, hay permiso y hubo actividad en los últimos 10 min. */
    private suspend fun updateVoice() {
        val enabled = settings.audio.first().voiceCommands
        val recent = timeSource.nowMs() - controller.lastActivityAt < VOICE_IDLE_TIMEOUT_MS
        val want = enabled && voice.hasPermission() && recent
        if (want == voiceWanted) return
        voiceWanted = want
        if (want) {
            startInForeground() // añade el tipo microphone ahora que hay permiso
            voice.start()
        } else {
            voice.stop()
        }
    }

    /** false si Android no permite ponerse en primer plano (p. ej. relanzado en segundo plano). */
    private fun startInForeground(): Boolean {
        val notification = notifications.build(controller.state.value)
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (voice.hasPermission()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return runCatching {
            ServiceCompat.startForeground(this, WorkoutNotifications.NOTIFICATION_ID, notification, type)
        }.recoverCatching {
            // Sin micrófono (p. ej. permiso "solo mientras se usa" y la app no está visible): solo audio.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    WorkoutNotifications.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                throw it
            }
        }.isSuccess
    }

    companion object {
        const val ACTION_START = "com.ckfit.app.action.START"
        const val ACTION_DONE = "com.ckfit.app.action.DONE"
        const val ACTION_PAUSE = "com.ckfit.app.action.PAUSE"
        const val ACTION_RESUME = "com.ckfit.app.action.RESUME"
        const val ACTION_REFRESH = "com.ckfit.app.action.REFRESH"

        private const val WAKE_LOCK_TAG = "CKFit:workout"
        /** Tope de seguridad por si algo falla y nadie para el servicio. */
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
        private const val VOICE_IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val VOICE_CHECK_MS = 15_000L

        @Volatile
        var running = false
            private set

        /**
         * Debe llamarse con la app visible (restricción de Android 12+ para servicios en primer plano).
         * Si ya está corriendo no hace nada: así es seguro desde las acciones de la notificación.
         */
        fun start(context: Context) {
            if (running) return
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java).setAction(ACTION_REFRESH))
        }

        /** Tras conceder el micrófono con el entrenamiento en marcha: vuelve a evaluar la voz. */
        fun refresh(context: Context) {
            if (!running) return
            context.startService(Intent(context, WorkoutService::class.java).setAction(ACTION_REFRESH))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
