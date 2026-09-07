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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.net.BridgeClient
import com.example.soarmcontroller.ui.components.AxisReadout
import com.example.soarmcontroller.ui.components.Guideline
import com.example.soarmcontroller.ui.components.Joystick
import com.example.soarmcontroller.ui.components.SplitAction
import com.example.soarmcontroller.ui.components.VerticalJog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val JOG_GUIDELINES = listOf(
    Guideline(
        "Rate control",
        "Push a control further to move faster along that axis; release and it re-centres.",
    ),
    Guideline("Left stick", "Moves the gripper across the table — X and Y."),
    Guideline("Height (Z)", "The vertical control raises and lowers the gripper. Up / down only."),
    Guideline("Pitch bar", "Hold to tilt the wrist down (−) or up (+)."),
    Guideline("Roll bar", "Hold to rotate the wrist (−) / (+)."),
    Guideline("Grip bar", "Hold to open or close the gripper."),
    Guideline("STOP", "Halts all motion immediately."),
    Guideline("5 joints", "Some wrist angles can't be reached; the arm gets as close as it can."),
)

@Composable
fun JogScreen(
    bridge: BridgeClient,
    connected: Boolean,
    onRequestConnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    var planar by remember { mutableStateOf(0f to 0f) } // x, y  (-1..1)
    var height by remember { mutableFloatStateOf(0f) }   // z     (-1..1)
    var pitchDir by remember { mutableFloatStateOf(0f) } // -1 / 0 / +1
    var rollDir by remember { mutableFloatStateOf(0f) }  // -1 / 0 / +1
    var gripDir by remember { mutableFloatStateOf(0f) }  // -1 / 0 / +1
    var speed by remember { mutableIntStateOf(1) }       // 0 slow, 1 medium, 2 fast

    // Stream jog frames at ~25 Hz while connected.
    // Stick: up (y+) = forward = base +X;  right (x+) = base -Y.
    LaunchedEffect(connected) {
        while (isActive && connected) {
            val (sx, sy) = planar
            val active = sx != 0f || sy != 0f || height != 0f ||
                pitchDir != 0f || rollDir != 0f || gripDir != 0f
            bridge.jog(
                vx = sy, vy = -sx, vz = height,
                pitch = pitchDir, roll = rollDir, grip = gripDir,
                speed = speed, enabled = active,
            )
            delay(40)
        }
    }

    ModeScaffold(
        title = "Jog",
        guidelines = JOG_GUIDELINES,
        contentPadding = contentPadding,
        pinnedContent = {
            Button(
                onClick = { bridge.stop() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("STOP", style = MaterialTheme.typography.titleMedium) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

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

            SplitAction(
                left = "Pitch −" to { down: Boolean -> pitchDir = if (down) -1f else 0f },
                right = "Pitch +" to { down: Boolean -> pitchDir = if (down) 1f else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            SplitAction(
                left = "Roll −" to { down: Boolean -> rollDir = if (down) -1f else 0f },
                right = "Roll +" to { down: Boolean -> rollDir = if (down) 1f else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            SplitAction(
                left = "Grip close" to { down: Boolean -> gripDir = if (down) -1f else 0f },
                right = "Grip open" to { down: Boolean -> gripDir = if (down) 1f else 0f },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ControlColumn(
                    label = "Move  X · Y",
                    readoutLabel = "L",
                    axes = listOf("X" to planar.first, "Y" to planar.second),
                    modifier = Modifier.weight(1f),
                ) {
                    Joystick(
                        onMove = { x, y -> planar = x to y },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
                ControlColumn(
                    label = "Height  Z",
                    readoutLabel = "R",
                    axes = listOf("Z" to height),
                    modifier = Modifier.weight(1f),
                ) {
                    VerticalJog(
                        onMove = { z -> height = z },
                        modifier = Modifier
                            .fillMaxWidth(0.52f)
                            .aspectRatio(0.52f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlColumn(
    label: String,
    readoutLabel: String,
    axes: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        control()
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        AxisReadout(
            label = readoutLabel,
            axes = axes,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
