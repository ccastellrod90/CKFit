package com.ckfit.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ckfit.app.ui.navigation.CKFitNavHost
import com.ckfit.app.ui.theme.CKFitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Cambia cada vez que se toca la notificación del entrenamiento: la navegación abre el ejercicio. */
    private val openWorkoutRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Tema solo oscuro: íconos claros en las barras aunque el sistema esté en modo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            CKFitTheme {
                CKFitNavHost(openWorkoutRequest = openWorkoutRequest.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) openWorkoutRequest.intValue++
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "com.ckfit.app.extra.OPEN_WORKOUT"
    }
}
