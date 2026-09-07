package com.example.soarmcontroller.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.data.SequenceRepository
import com.example.soarmcontroller.data.model.SeqStep
import com.example.soarmcontroller.data.model.SequenceRecord
import com.example.soarmcontroller.net.BridgeClient
import com.example.soarmcontroller.ui.components.ExpandableCard
import com.example.soarmcontroller.ui.components.Guideline
import com.example.soarmcontroller.ui.components.NameDialog
import com.example.soarmcontroller.ui.components.NotConnectedDialog
import kotlinx.coroutines.launch

private val SEQ_GUIDELINES = listOf(
    Guideline("Release motors", "Turns off the servos so you can move the arm by hand."),
    Guideline("Add pose", "Captures the arm's current joint positions as a step."),
    Guideline("Add delay", "Inserts a pause (milliseconds) between steps."),
    Guideline("Play", "Runs the steps in order; Stop halts it."),
    Guideline(
        "Heads up",
        "The shoulder sags when motors are released; support the arm while positioning it.",
    ),
    Guideline("Save", "Stores the current steps locally; tap a saved sequence to reload it."),
)

@Composable
fun SequenceScreen(
    repo: SequenceRepository,
    bridge: BridgeClient,
    connected: Boolean,
    onRequestConnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    ModeScaffold(
        title = "Sequence",
        guidelines = SEQ_GUIDELINES,
        contentPadding = contentPadding,
    ) {
        val scope = rememberCoroutineScope()
        val saved by repo.sequences.collectAsState()
        val seq by bridge.seq.collectAsState()

        val steps = remember { mutableStateListOf<SeqStep>() }
        var nextPoseId by remember { mutableIntStateOf(1) }
        var motorsReleased by remember { mutableStateOf(false) }
        var showSave by remember { mutableStateOf(false) }
        var pendingStep by remember { mutableStateOf<(() -> Unit)?>(null) }

        // Ordered play payload: pose steps map to their capture order on the arm.
        fun playSteps(): List<Map<String, Any>> {
            var poseIdx = 0
            return steps.map { s ->
                when (s) {
                    is SeqStep.Pose -> mapOf<String, Any>("pose" to poseIdx++)
                    is SeqStep.Delay -> mapOf<String, Any>("delay_ms" to s.millis)
                }
            }
        }

        val addPose: () -> Unit = {
            bridge.seqCmd("capture")
            steps.add(SeqStep.Pose("Pose $nextPoseId"))
            nextPoseId++
        }
        val addDelay: () -> Unit = { steps.add(SeqStep.Delay(500)) }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Release motors (move by hand)", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = motorsReleased,
                    onCheckedChange = {
                        motorsReleased = it
                        bridge.seqCmd(if (it) "release" else "hold")
                    },
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    if (steps.isEmpty()) {
                        Text(
                            "No steps yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    } else {
                        steps.forEachIndexed { index, step ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                            StepRow(
                                ordinal = index + 1,
                                step = step,
                                onDelta = { delta ->
                                    val s = steps[index]
                                    if (s is SeqStep.Delay) {
                                        steps[index] = SeqStep.Delay(
                                            (s.millis + delta).coerceIn(100, 10_000),
                                        )
                                    }
                                },
                                onDelete = { steps.removeAt(index) },
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { if (connected) addPose() else pendingStep = addPose },
                    modifier = Modifier.weight(1f),
                ) { Text("Add pose") }
                OutlinedButton(
                    onClick = { if (connected) addDelay() else pendingStep = addDelay },
                    modifier = Modifier.weight(1f),
                ) { Text("Add delay") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { bridge.seqCmd("play", steps = playSteps()) },
                    enabled = connected && steps.any { it is SeqStep.Pose },
                    modifier = Modifier.weight(1f),
                ) { Text(if (seq.playing) "Playing…" else "Play") }
                OutlinedButton(
                    onClick = { bridge.seqCmd("stop") },
                    enabled = connected,
                    modifier = Modifier.weight(1f),
                ) { Text("Stop") }
                TextButton(
                    onClick = { showSave = true },
                    enabled = steps.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }

            Text(
                "Captured on arm: ${seq.captured}" +
                    (if (seq.released) " · motors released" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExpandableCard(title = "Saved sequences (${saved.size})") {
                if (saved.isEmpty()) {
                    Text(
                        "None yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        saved.forEach { rec ->
                            SavedRow(
                                record = rec,
                                onLoad = {
                                    steps.clear()
                                    steps.addAll(rec.steps)
                                    nextPoseId = rec.steps
                                        .filterIsInstance<SeqStep.Pose>().size + 1
                                },
                                onDelete = { scope.launch { repo.delete(rec.id) } },
                            )
                        }
                    }
                }
            }
        }

        pendingStep?.let { action ->
            NotConnectedDialog(
                proceedLabel = "Add anyway",
                onProceed = action,
                onConnect = onRequestConnect,
                onDismiss = { pendingStep = null },
            )
        }

        if (showSave) {
            NameDialog(
                title = "Save sequence",
                onConfirm = { name ->
                    showSave = false
                    scope.launch { repo.add(SequenceRecord(name = name, steps = steps.toList())) }
                },
                onDismiss = { showSave = false },
            )
        }
    }
}

@Composable
private fun StepRow(
    ordinal: Int,
    step: SeqStep,
    onDelta: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$ordinal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 14.dp),
        )
        when (step) {
            is SeqStep.Pose -> Text(
                step.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            is SeqStep.Delay -> {
                Text(
                    "Delay ${step.millis} ms",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDelta(-100) }) { Text("−") }
                IconButton(onClick = { onDelta(100) }) { Text("+") }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove step",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedRow(
    record: SequenceRecord,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(record.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${record.steps.size} steps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete sequence",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
