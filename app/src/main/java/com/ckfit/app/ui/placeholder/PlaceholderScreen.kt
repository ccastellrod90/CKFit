package com.ckfit.app.ui.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ckfit.app.R
import com.ckfit.app.ui.components.CKFitTopBar

/** Pantalla provisional para las rutas que se construyen en fases siguientes. */
@Composable
fun PlaceholderScreen(@StringRes titleRes: Int, onBack: () -> Unit) {
    Scaffold(topBar = { CKFitTopBar(stringResource(titleRes), onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
