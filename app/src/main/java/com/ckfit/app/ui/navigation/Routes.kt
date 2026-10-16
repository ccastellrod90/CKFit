package com.ckfit.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data object Today

/** [autoStart]: "Comenzar" desde Rutina de hoy arranca la serie al abrir. */
@Serializable
data class ExerciseFocus(val sessionExerciseId: Long, val autoStart: Boolean = false)

@Serializable
data object Routines

@Serializable
data class RoutineDays(val routineId: Long)

@Serializable
data class DayEditor(val dayId: Long)

/** Editar una prescripción ([dayExerciseId]) o crear una nueva ([dayId] + [exerciseId]). */
@Serializable
data class ExerciseEditor(
    val dayExerciseId: Long? = null,
    val dayId: Long? = null,
    val exerciseId: Long? = null,
)

@Serializable
data object Progress

@Serializable
data object Settings
