package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Wraps a mode's interactive content. When [connected] is false the content is
 * dimmed, all touches are swallowed, and a "connect first" callout is shown.
 */
@Composable
fun ConnectionGate(
    connected: Boolean,
    onRequestConnect: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box {
        Box(
            modifier = if (connected) {
                Modifier
            } else {
                Modifier
                    .alpha(0.35f)
                    .pointerInput(Unit) { /* consume all gestures */ awaitPointerEventScopeConsume() }
            },
        ) {
            content()
        }

        if (!connected) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.LinkOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Not connected",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Connect to the arm first to use these controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRequestConnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.awaitPointerEventScopeConsume() {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}
