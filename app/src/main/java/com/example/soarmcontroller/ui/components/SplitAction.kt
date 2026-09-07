package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Two press-and-hold actions that read as one control: a single bordered pill
 * split down the middle. Each half reports `true` on press, `false` on release,
 * so callers can drive a continuous value while the finger is down.
 */
@Composable
fun SplitAction(
    left: Pair<String, (Boolean) -> Unit>,
    right: Pair<String, (Boolean) -> Unit>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Half(left.first, left.second, enabled, Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Half(right.first, right.second, enabled, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Half(
    text: String,
    onPress: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onPress(true)
                        tryAwaitRelease()
                        onPress(false)
                    },
                )
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
