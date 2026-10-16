package com.ckfit.app.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ckfit.app.R
import com.ckfit.app.data.repo.AudioSettings
import com.ckfit.app.data.repo.TrainingSettings
import com.ckfit.app.service.TtsStatus
import com.ckfit.app.service.VoiceStatus
import com.ckfit.app.ui.components.VoiceHelpSheet
import kotlin.math.roundToInt

@Composable
fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingStepper(label: String, value: Int, range: IntRange, format: @Composable (Int) -> String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Text(stringResource(R.string.minus_sign), style = MaterialTheme.typography.titleLarge)
        }
        Text(format(value), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Text(stringResource(R.string.plus_sign), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Voz: activar comandos y prueba de micrófono (muestra la última frase reconocida). */
@Composable
fun VoiceSection(
    audio: AudioSettings,
    status: VoiceStatus,
    lastHeard: String?,
    micTest: Boolean,
    micTestAvailable: Boolean,
    onChange: ((AudioSettings) -> AudioSettings) -> Unit,
    onMicTest: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onMicTest(true)
    }
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) VoiceHelpSheet(onDismiss = { showHelp = false })
    SettingsCard(stringResource(R.string.settings_voice_section)) {
        SettingSwitch(
            label = stringResource(R.string.settings_voice_commands),
            checked = audio.voiceCommands,
            onChange = { v -> onChange { it.copy(voiceCommands = v) } },
            hint = stringResource(R.string.settings_voice_commands_hint),
        )
        TextButton(onClick = { showHelp = true }) {
            Text(stringResource(R.string.voice_help_open), color = MaterialTheme.colorScheme.secondary)
        }
        if (audio.voiceCommands) {
            if (micTestAvailable) {
                OutlinedButton(onClick = {
                    when {
                        micTest -> onMicTest(false)
                        hasMicPermission(context) -> onMicTest(true)
                        else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Text(stringResource(if (micTest) R.string.settings_mic_test_stop else R.string.settings_mic_test))
                }
                if (micTest) {
                    Text(
                        text = when (status) {
                            VoiceStatus.LOADING -> stringResource(R.string.voice_loading)
                            VoiceStatus.LISTENING -> stringResource(R.string.settings_mic_heard, lastHeard ?: "…")
                            VoiceStatus.ERROR -> stringResource(R.string.voice_error)
                            VoiceStatus.NO_PERMISSION -> stringResource(R.string.voice_no_permission)
                            VoiceStatus.OFF -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.settings_mic_test_busy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Audio: voz, volumen, bips, ducking y vibración. */
@Composable
fun AudioSection(
    audio: AudioSettings,
    ttsStatus: TtsStatus,
    onChange: ((AudioSettings) -> AudioSettings) -> Unit,
    onTestVoice: (String) -> Unit,
) {
    val context = LocalContext.current
    val sample = stringResource(R.string.settings_voice_sample)
    SettingsCard(stringResource(R.string.settings_audio_section)) {
        SettingSwitch(stringResource(R.string.settings_tts), audio.tts, { v -> onChange { it.copy(tts = v) } })
        if (audio.tts) {
            // El volumen se aplica al soltar el deslizador (no en cada paso).
            var volume by remember(audio.volume) { mutableStateOf(audio.volume) }
            Text(
                stringResource(R.string.settings_volume, (volume * 100).roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { onChange { it.copy(volume = volume) } },
                valueRange = 0.1f..1f,
            )
            OutlinedButton(onClick = { onTestVoice(sample) }) { Text(stringResource(R.string.settings_test_voice)) }
            if (ttsStatus == TtsStatus.NO_SPANISH_VOICE || ttsStatus == TtsStatus.ERROR) {
                Text(
                    stringResource(R.string.settings_tts_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(stringResource(R.string.settings_tts_install)) }
            }
        }
        SettingSwitch(stringResource(R.string.settings_beeps), audio.beeps, { v -> onChange { it.copy(beeps = v) } })
        SettingSwitch(
            stringResource(R.string.settings_ducking),
            audio.ducking,
            { v -> onChange { it.copy(ducking = v) } },
            hint = stringResource(R.string.settings_ducking_hint),
        )
        SettingSwitch(stringResource(R.string.settings_vibration), audio.vibration, { v -> onChange { it.copy(vibration = v) } })
    }
}

/** Cuentas atrás: final del descanso, previa a ejercicios por tiempo y aviso a mitad de serie. */
@Composable
fun CountdownSection(
    training: TrainingSettings,
    onRestCountdown: (Int) -> Unit,
    onPreCountdown: (Int) -> Unit,
    onHalfway: (Boolean) -> Unit,
) {
    SettingsCard(stringResource(R.string.settings_countdown_section)) {
        SettingStepper(
            label = stringResource(R.string.settings_rest_countdown),
            value = training.restCountdownSec,
            range = 0..10,
            format = { stringResource(R.string.seconds_value, it) },
            onChange = onRestCountdown,
        )
        SettingStepper(
            label = stringResource(R.string.settings_pre_countdown),
            value = training.preCountdownSec,
            range = 0..10,
            format = { stringResource(R.string.seconds_value, it) },
            onChange = onPreCountdown,
        )
        SettingSwitch(
            stringResource(R.string.settings_halfway_cue),
            training.halfwayCue,
            onHalfway,
            hint = stringResource(R.string.settings_halfway_cue_hint),
        )
    }
}

/**
 * Batería: pide excluir la app de la optimización (para que no la duerman con la pantalla apagada).
 * Algunas marcas (Huawei/Honor, Xiaomi, Samsung) tienen además su propio gestor: se explica en el texto.
 */
@SuppressLint("BatteryLife")
@Composable
fun BatterySection() {
    val context = LocalContext.current
    var ignoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        ignoring = isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    SettingsCard(stringResource(R.string.settings_battery_section)) {
        Text(
            stringResource(if (ignoring) R.string.settings_battery_ok else R.string.settings_battery_optimized),
            style = MaterialTheme.typography.bodyLarge,
            color = if (ignoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!ignoring) {
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)),
                    )
                }
            }) { Text(stringResource(R.string.settings_battery_request)) }
        }
        Text(
            stringResource(R.string.settings_battery_vendor_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hasMicPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
