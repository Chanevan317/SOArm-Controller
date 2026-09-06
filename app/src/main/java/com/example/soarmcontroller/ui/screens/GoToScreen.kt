package com.example.soarmcontroller.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.data.PresetRepository
import com.example.soarmcontroller.data.model.GoToPreset
import com.example.soarmcontroller.ui.components.ExpandableCard
import com.example.soarmcontroller.ui.components.Guideline
import com.example.soarmcontroller.ui.components.NameDialog
import kotlinx.coroutines.launch

private val GOTO_GUIDELINES = listOf(
    Guideline(
        "Units",
        "Millimetres for X / Y / Z, degrees for pitch / roll, in the robot base frame.",
    ),
    Guideline(
        "Move to target",
        "Plans a straight path and drives the gripper there at a limited speed.",
    ),
    Guideline(
        "Bounds",
        "Points out of reach or outside the safe area are rejected, not attempted.",
    ),
    Guideline("Orientation", "Leave pitch / roll blank to keep the wrist where it is."),
    Guideline("Save preset", "Stores the current values locally to recall later."),
)

@Composable
fun GoToScreen(
    repo: PresetRepository,
    connected: Boolean,
    onRequestConnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    ModeScaffold(
        title = "Go To",
        guidelines = GOTO_GUIDELINES,
        connected = connected,
        onRequestConnect = onRequestConnect,
        contentPadding = contentPadding,
    ) {
        val scope = rememberCoroutineScope()
        val presets by repo.presets.collectAsState()

        var x by remember { mutableStateOf("") }
        var y by remember { mutableStateOf("") }
        var z by remember { mutableStateOf("") }
        var pitch by remember { mutableStateOf("") }
        var roll by remember { mutableStateOf("") }
        var showSave by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("X (mm)", x, { x = it }, Modifier.weight(1f))
                NumField("Y (mm)", y, { y = it }, Modifier.weight(1f))
                NumField("Z (mm)", z, { z = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Pitch °", pitch, { pitch = it }, Modifier.weight(1f))
                NumField("Roll °", roll, { roll = it }, Modifier.weight(1f))
            }

            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Move to target")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Stop") }
                TextButton(
                    onClick = { showSave = true },
                    modifier = Modifier.weight(1f),
                    enabled = listOf(x, y, z, pitch, roll).any { it.isNotBlank() },
                ) { Text("Save preset") }
            }

            ExpandableCard(title = "Saved presets (${presets.size})") {
                if (presets.isEmpty()) {
                    Text(
                        "None yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        presets.forEach { p ->
                            PresetRow(
                                preset = p,
                                onLoad = {
                                    x = p.x.orEmptyStr()
                                    y = p.y.orEmptyStr()
                                    z = p.z.orEmptyStr()
                                    pitch = p.pitch.orEmptyStr()
                                    roll = p.roll.orEmptyStr()
                                },
                                onDelete = { scope.launch { repo.delete(p.id) } },
                            )
                        }
                    }
                }
            }

            Text(
                "Reachability is checked on the laptop before any motion.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showSave) {
            NameDialog(
                title = "Save preset",
                onConfirm = { name ->
                    showSave = false
                    scope.launch {
                        repo.add(
                            GoToPreset(
                                name = name,
                                x = x.toDoubleOrNull(),
                                y = y.toDoubleOrNull(),
                                z = z.toDoubleOrNull(),
                                pitch = pitch.toDoubleOrNull(),
                                roll = roll.toDoubleOrNull(),
                            ),
                        )
                    }
                },
                onDismiss = { showSave = false },
            )
        }
    }
}

@Composable
private fun PresetRow(
    preset: GoToPreset,
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
            Text(preset.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary(preset),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete preset",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun summary(p: GoToPreset): String {
    val parts = buildList {
        p.x?.let { add("X $it") }
        p.y?.let { add("Y $it") }
        p.z?.let { add("Z $it") }
        p.pitch?.let { add("P $it") }
        p.roll?.let { add("R $it") }
    }
    return if (parts.isEmpty()) "—" else parts.joinToString("  ")
}

private fun Double?.orEmptyStr(): String = this?.let {
    if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
} ?: ""

@Composable
private fun NumField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onValueChange(s.filter { it.isDigit() || it == '-' || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
