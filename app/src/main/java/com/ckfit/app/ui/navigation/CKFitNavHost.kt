package com.ckfit.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.ckfit.app.ui.MainViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ckfit.app.R
import com.ckfit.app.ui.focus.ExerciseFocusScreen
import com.ckfit.app.ui.home.HomeScreen
import com.ckfit.app.ui.placeholder.PlaceholderScreen
import com.ckfit.app.ui.routines.DayEditorScreen
import com.ckfit.app.ui.routines.ExerciseEditorScreen
import com.ckfit.app.ui.routines.RoutineDaysScreen
import com.ckfit.app.ui.routines.RoutinesScreen
import com.ckfit.app.ui.settings.SettingsScreen
import com.ckfit.app.ui.today.TodayScreen

@Composable
fun CKFitNavHost(openWorkoutRequest: Int = 0, mainViewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    // Tocar la notificación del entrenamiento: Rutina de hoy → ejercicio en curso (atrás vuelve a la lista).
    LaunchedEffect(openWorkoutRequest) {
        if (openWorkoutRequest == 0) return@LaunchedEffect
        val id = mainViewModel.activeExerciseId() ?: return@LaunchedEffect
        val current = navController.currentBackStackEntry
        if (current?.destination?.hasRoute<ExerciseFocus>() == true &&
            current.toRoute<ExerciseFocus>().sessionExerciseId == id
        ) {
            return@LaunchedEffect
        }
        navController.navigate(Today) { launchSingleTop = true }
        navController.navigate(ExerciseFocus(id)) { launchSingleTop = true }
    }

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onOpenToday = { navController.navigate(Today) },
                onOpenRoutines = { navController.navigate(Routines) },
                onOpenProgress = { navController.navigate(Progress) },
                onOpenSettings = { navController.navigate(Settings) },
            )
        }
        composable<Settings> { SettingsScreen(onBack = back) }

        composable<Routines> {
            RoutinesScreen(
                onBack = back,
                onOpenRoutine = { navController.navigate(RoutineDays(it)) },
            )
        }
        composable<RoutineDays> {
            RoutineDaysScreen(
                onBack = back,
                onOpenDay = { navController.navigate(DayEditor(it)) },
            )
        }
        composable<DayEditor> {
            DayEditorScreen(
                onBack = back,
                onOpenPrescription = { navController.navigate(ExerciseEditor(dayExerciseId = it)) },
                onNewPrescription = { dayId, exerciseId ->
                    navController.navigate(ExerciseEditor(dayId = dayId, exerciseId = exerciseId))
                },
            )
        }
        composable<ExerciseEditor> { ExerciseEditorScreen(onDone = back) }

        composable<Today> {
            TodayScreen(
                onBack = back,
                onOpenExercise = { id, autoStart -> navController.navigate(ExerciseFocus(id, autoStart)) },
                onSessionClosed = { navController.popBackStack(Home, inclusive = false) },
            )
        }
        composable<ExerciseFocus> {
            ExerciseFocusScreen(
                onBack = back,
                // "Siguiente" reemplaza el ejercicio actual: atrás vuelve a "Rutina de hoy".
                onOpenExercise = { next ->
                    navController.navigate(ExerciseFocus(next)) {
                        popUpTo<ExerciseFocus> { inclusive = true }
                    }
                },
                onSessionClosed = { navController.popBackStack(Home, inclusive = false) },
            )
        }

        // Pantallas de las fases siguientes.
        composable<Progress> { PlaceholderScreen(R.string.progress_title, onBack = back) }
    }
}
