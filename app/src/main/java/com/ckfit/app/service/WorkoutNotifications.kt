package com.ckfit.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ckfit.app.MainActivity
import com.ckfit.app.R
import com.ckfit.app.domain.engine.EngineState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notificación persistente del entrenamiento: estado + acciones Listo / Pausa (sección 8). */
@Singleton
class WorkoutNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_workout),
            NotificationManager.IMPORTANCE_LOW, // silenciosa: los avisos son de audio (Fase 4)
        ).apply {
            description = context.getString(R.string.notification_channel_workout_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Lo que cambia el contenido visible; los segundos no cuentan porque los lleva el cronómetro. */
    fun contentKey(state: WorkoutState): Any {
        val e = state.engine
        val phase = when (e) {
            is EngineState.Paused -> "paused:" + e.previous::class.simpleName
            else -> e::class.simpleName
        }
        val set = when (e) {
            is EngineState.Waiting -> e.set
            is EngineState.WorkingReps -> e.set * 10 + (e.side?.ordinal ?: 9)
            is EngineState.WorkingTimed -> e.set * 10 + (e.side?.ordinal ?: 9)
            is EngineState.SwitchSide -> e.set
            is EngineState.Resting -> e.nextSet * 1000 + (e.totalMs / 1000).toInt() // +15 s cambia el total
            else -> 0
        }
        return listOf(state.exercise.sessionExerciseId, phase, set, state.exercise.weightKg)
    }

    fun show(state: WorkoutState) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
        if (granted) NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(state))
    }

    fun build(state: WorkoutState?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ckfit)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
        if (state == null) {
            return builder.setContentTitle(context.getString(R.string.notification_workout_title)).build()
        }
        val ex = state.exercise
        builder.setContentTitle(ex.name)
        val e = state.engine
        val paused = e is EngineState.Paused
        val shown = if (e is EngineState.Paused) e.previous else e
        val now = System.currentTimeMillis()
        when (shown) {
            is EngineState.Waiting -> builder.setContentText(context.getString(R.string.notification_waiting, shown.set, shown.total))
            is EngineState.PreCountdown -> builder.setContentText(context.getString(R.string.focus_get_ready))
            is EngineState.WorkingReps -> {
                builder.setContentText(context.getString(R.string.notification_working, shown.set, ex.sets))
                if (!paused) builder.chronometer(now - shown.elapsedMs, countDown = false)
            }
            is EngineState.WorkingTimed -> {
                builder.setContentText(context.getString(R.string.notification_working, shown.set, ex.sets))
                if (!paused) builder.chronometer(now + shown.remainingMs, countDown = true)
            }
            is EngineState.SwitchSide -> builder.setContentText(context.getString(R.string.focus_switch_side))
            is EngineState.Resting -> {
                builder.setContentText(context.getString(R.string.notification_resting, shown.nextSet, ex.sets))
                if (!paused) builder.chronometer(now + shown.remainingMs, countDown = true)
            }
            EngineState.ExerciseDone -> builder.setContentText(
                ex.nextName?.let { context.getString(R.string.notification_done_next, it) }
                    ?: context.getString(R.string.focus_exercise_done),
            )
            is EngineState.Paused -> Unit
        }
        if (paused) builder.setSubText(context.getString(R.string.focus_paused))

        // Acciones: Empezar/Listo según la fase y Pausa/Seguir.
        when (shown) {
            is EngineState.Waiting ->
                builder.addAction(0, context.getString(R.string.action_start_set), serviceIntent(WorkoutService.ACTION_START))
            is EngineState.WorkingReps, is EngineState.WorkingTimed ->
                builder.addAction(0, context.getString(R.string.action_done_set), serviceIntent(WorkoutService.ACTION_DONE))
            else -> Unit
        }
        if (e != EngineState.ExerciseDone && shown !is EngineState.Waiting) {
            if (paused) {
                builder.addAction(0, context.getString(R.string.action_resume), serviceIntent(WorkoutService.ACTION_RESUME))
            } else {
                builder.addAction(0, context.getString(R.string.action_pause), serviceIntent(WorkoutService.ACTION_PAUSE))
            }
        }
        return builder.build()
    }

    private fun NotificationCompat.Builder.chronometer(whenMs: Long, countDown: Boolean) {
        setWhen(whenMs)
        setShowWhen(true)
        setUsesChronometer(true)
        setChronometerCountDown(countDown)
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, WorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Abre la app en el ejercicio en curso (reutiliza la actividad si ya está: singleTop). */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 1
    }
}
