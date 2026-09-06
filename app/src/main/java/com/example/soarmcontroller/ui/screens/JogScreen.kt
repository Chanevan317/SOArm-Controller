package com.example.soarmcontroller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.ui.components.Guideline
import com.example.soarmcontroller.ui.components.Joystick

private val JOG_GUIDELINES = listOf(
    Guideline(
        "Rate control",
        "Push a stick further to move faster along that axis; release and it springs back to zero.",
    ),
    Guideline("Left stick", "Moves the gripper in the horizontal plane (X and Y)."),
    Guideline("Right stick", "Moves the gripper up and down (Z) and rotates the wrist."),
    Guideline("Middle buttons", "Wrist pitch, and gripper open / close."),
    Guideline("STOP", "Halts all motion immediately."),
    Guideline("5 joints", "Some wrist angles can't be reached; the arm gets as close as it can."),
)

@Composable
fun JogScreen(
    connected: Boolean,
    onRequestConnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    ModeScaffold(
        title = "Jog",
        guidelines = JOG_GUIDELINES,
        connected = connected,
        onRequestConnect = onRequestConnect,
        contentPadding = contentPadding,
    ) {
        var left by remember { mutableStateOf(0f to 0f) }
        var right by remember { mutableStateOf(0f to 0f) }
        var speed by remember { mutableIntStateOf(1) } // 0 slow, 1 medium, 2 fast

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

            // --- Top: speed (label + options on one line) ----------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Speed:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                listOf("Slow", "Medium", "Fast").forEachIndexed { i, label ->
                    FilterChip(
                        selected = speed == i,
                        onClick = { speed = i },
                        label = { Text(label) },
                    )
                }
            }

            // --- Middle: pitch + gripper --------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Pitch −") }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Pitch +") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Grip open") }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Grip close") }
            }

            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("STOP", style = MaterialTheme.typography.titleMedium) }

            // --- Bottom: joysticks (thumb zone) -------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StickBlock("Translate  X · Y", Modifier.weight(1f)) {
                    Joystick(
                        onMove = { x, y -> left = x to y },
                        enabled = connected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
                StickBlock("Z · Wrist", Modifier.weight(1f)) {
                    Joystick(
                        onMove = { x, y -> right = x to y },
                        enabled = connected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
            }

            Text(
                "L %.2f, %.2f    R %.2f, %.2f".format(
                    left.first, left.second, right.first, right.second,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StickBlock(
    label: String,
    modifier: Modifier = Modifier,
    stick: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        stick()
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
