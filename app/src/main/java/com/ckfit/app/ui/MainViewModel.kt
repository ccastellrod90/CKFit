package com.ckfit.app.ui

import androidx.lifecycle.ViewModel
import com.ckfit.app.service.WorkoutController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Para abrir el ejercicio en curso al tocar la notificación del entrenamiento. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val controller: WorkoutController,
) : ViewModel() {

    /** Ejercicio abierto en el controlador; si la app acaba de arrancar, espera un poco a que se restaure. */
    suspend fun activeExerciseId(): Long? = withTimeoutOrNull(RESTORE_WAIT_MS) {
        controller.state.filterNotNull().first().exercise.sessionExerciseId
    }

    private companion object {
        const val RESTORE_WAIT_MS = 3_000L
    }
}
