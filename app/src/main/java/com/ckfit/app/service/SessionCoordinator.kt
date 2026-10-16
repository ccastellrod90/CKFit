package com.ckfit.app.service

import com.ckfit.app.data.repo.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Acciones de sesión que afectan a la vez a la BD y al entrenamiento en curso (motor + servicio). */
@Singleton
class SessionCoordinator @Inject constructor(
    private val sessions: SessionRepository,
    private val controller: WorkoutController,
) {
    /** Empieza (o devuelve la ya en curso). [plannedId] null = sesión extra fuera de la semana. */
    suspend fun start(routineDayId: Long, plannedId: Long?): Long = sessions.start(routineDayId, plannedId)

    /** "Terminar entrenamiento". */
    suspend fun finish(sessionId: Long) {
        controller.close()
        sessions.finish(sessionId)
    }

    /** "Saltar sesión" con una sesión ya empezada. */
    suspend fun skip(sessionId: Long, reason: String?) {
        controller.close()
        sessions.abandon(sessionId, skipPlanned = true, skipReason = reason)
    }

    /** "Elegir otra sesión" con una ya empezada: deja esta (vuelve a pendiente) y empieza la otra. */
    suspend fun switchTo(sessionId: Long, routineDayId: Long, plannedId: Long?): Long {
        controller.close()
        sessions.abandon(sessionId, skipPlanned = false, skipReason = null)
        return sessions.start(routineDayId, plannedId)
    }
}
