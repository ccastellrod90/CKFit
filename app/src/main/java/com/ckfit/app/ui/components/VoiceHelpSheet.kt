package com.ckfit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ckfit.app.R
import com.ckfit.app.domain.voice.VoiceAction
import com.ckfit.app.domain.voice.VoiceCommands

/** Ayuda: qué se puede decir para cada acción (la lista sale de la propia gramática). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.voice_help_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.voice_help_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VoiceCommands.groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(actionLabel(group.action)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        group.phrases.joinToString(" · ") { "«$it»" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Text(
                stringResource(R.string.voice_help_tips),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private fun actionLabel(action: VoiceAction) = when (action) {
    VoiceAction.START -> R.string.voice_action_start
    VoiceAction.DONE -> R.string.voice_action_done
    VoiceAction.NEXT -> R.string.voice_action_next
    VoiceAction.SKIP -> R.string.voice_action_skip
    VoiceAction.MORE_REST -> R.string.voice_action_more_rest
    VoiceAction.PAUSE -> R.string.voice_action_pause
    VoiceAction.RESUME -> R.string.voice_action_resume
}
