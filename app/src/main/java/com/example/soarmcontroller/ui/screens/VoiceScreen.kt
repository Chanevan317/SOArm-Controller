package com.example.soarmcontroller.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.soarmcontroller.net.BridgeClient
import com.example.soarmcontroller.ui.components.ExpandableCard
import com.example.soarmcontroller.ui.components.Guideline
import com.example.soarmcontroller.ui.components.LevelBars
import com.example.soarmcontroller.voice.VoiceModel
import com.example.soarmcontroller.voice.VoiceRecognizer
import kotlinx.coroutines.launch

/**
 * The recognition grammar — every one of these is a command the laptop bridge
 * acts on, so whatever Vosk hears gets sent straight through.
 */
private val COMMANDS = listOf(
    "stop", "home", "rest", "ready", "extend",
    "open", "close",
    "faster", "slower",
    "up", "down", "left", "right", "forward", "back",
    "rotate left", "rotate right",
)

private val VOICE_GUIDELINES = listOf(
    Guideline("Fixed grammar", "Only the listed keywords are recognised — no free sentences."),
    Guideline("Directions latch", "\"right\", \"up\", etc. keep the arm moving until you say \"stop\" or another direction."),
    Guideline("On-device", "Recognition runs locally with Vosk; no internet needed after setup."),
    Guideline("Push-to-talk", "Hold the mic for anything that moves the arm."),
    Guideline("Stop", "Always mirrored on a physical button — never voice-only."),
    Guideline("Model", "The ~40 MB speech model downloads once, on first use."),
)

@Composable
fun VoiceScreen(
    bridge: BridgeClient,
    connected: Boolean,
    onRequestConnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    ModeScaffold(
        title = "Voice",
        guidelines = VOICE_GUIDELINES,
        contentPadding = contentPadding,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val recognizer = remember { VoiceRecognizer(context.applicationContext, COMMANDS) }
        val vs by recognizer.state.collectAsState()

        var modelPresent by remember { mutableStateOf(VoiceModel.isReady(context)) }
        var downloading by remember { mutableStateOf(false) }
        var progress by remember { mutableFloatStateOf(0f) }

        var hasMic by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        val micPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> hasMic = granted }

        LaunchedEffect(modelPresent) { if (modelPresent) recognizer.ensureModel() }
        DisposableEffect(Unit) { onDispose { recognizer.shutdown() } }

        // Forward every recognised keyword to the bridge — including repeats of
        // the same word (send() is a no-op when disconnected).
        LaunchedEffect(recognizer) {
            recognizer.recognized.collect { token -> bridge.voice(token) }
        }

        val canListen = modelPresent && vs.ready && hasMic

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ExpandableCard(title = "Voice commands (${COMMANDS.size})") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COMMANDS.forEach { CommandPill(it) }
                }
            }

            if (!modelPresent) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Speech model", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "About 40 MB, downloaded once and kept on the device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Button(
                                onClick = {
                                    downloading = true
                                    scope.launch {
                                        val ok = runCatching {
                                            VoiceModel.download(context) { progress = it }
                                        }.isSuccess
                                        downloading = false
                                        if (ok) {
                                            modelPresent = true
                                            recognizer.ensureModel()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Download voice model") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            LevelBars(active = vs.listening, modifier = Modifier.fillMaxWidth())

            Text(
                text = displayWord(vs.partial, vs.lastCommand, vs.listening, canListen),
                style = MaterialTheme.typography.headlineMedium,
                color = if (vs.partial.isEmpty() && vs.lastCommand == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MicButton(
                    listening = vs.listening,
                    onToggle = {
                        when {
                            !hasMic -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            !modelPresent -> Unit // download card above
                            vs.listening -> recognizer.stop()
                            else -> recognizer.start()
                        }
                    },
                )
            }

            vs.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                helperLine(hasMic, modelPresent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun displayWord(
    partial: String,
    last: String?,
    listening: Boolean,
    canListen: Boolean,
): String = when {
    partial.isNotEmpty() -> partial
    last != null -> last
    listening -> "listening…"
    canListen -> "tap to start"
    else -> "—"
}

private fun helperLine(hasMic: Boolean, modelPresent: Boolean): String = when {
    !hasMic -> "Microphone permission needed."
    !modelPresent -> "Download the model to start."
    else -> "Recognised keywords are sent to the arm when connected."
}

@Composable
private fun CommandPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MicButton(listening: Boolean, onToggle: () -> Unit) {
    val ring by animateDpAsState(if (listening) 96.dp else 80.dp, label = "mic-size")
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (listening) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(ring),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (listening) "Stop listening" else "Start listening",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
