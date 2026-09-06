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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.ui.components.AxisReadout
import com.example.soarmcontroller.ui.components.Guideline
import com.example.soarmcontroller.ui.components.Joystick
import com.example.soarmcontroller.ui.components.SplitAction
import com.example.soarmcontroller.ui.components.VerticalJog

private val JOG_GUIDELINES = listOf(
    Guideline(
        "Rate control",
        "Push a control further to move faster along that axis; release and it re-centres.",
    ),
    Guideline("Left stick", "Moves the gripper across the table — X and Y."),
    Guideline("Height (Z)", "The vertical control raises and lowers the gripper. Up / down only."),
    Guideline("Pitch bar", "Tilts the wrist down (−) or up (+)."),
    Guideline("Grip bar", "Opens or closes the gripper."),
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
        contentPadding = contentPadding,
        pinnedContent = {
            // Pinned under the header, away from the joystick thumb zone.
            Button(
                onClick = {},
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
        var planar by remember { mutableStateOf(0f to 0f) } // x, y
        var height by remember { mutableFloatStateOf(0f) }   // z
        var speed by remember { mutableIntStateOf(1) }       // 0 slow, 1 medium, 2 fast

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // --- Speed -------------------------------------------------
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

            // --- Pitch + gripper, each a split bar --------------------
            SplitAction(
                left = "Pitch −" to {},
                right = "Pitch +" to {},
                modifier = Modifier.fillMaxWidth(),
            )
            SplitAction(
                left = "Grip close" to {},
                right = "Grip open" to {},
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Two equal columns: a control stacked over its readout ---
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
                            .aspectRatio(0.52f), // same rendered height as the joystick
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
