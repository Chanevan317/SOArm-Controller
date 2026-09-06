package com.example.soarmcontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Shown when the user triggers an action that would normally read live data from
 * the arm (save a target, capture a pose, add a delay) while nothing is connected.
 * Offers to connect, proceed regardless, or cancel.
 */
@Composable
fun NotConnectedDialog(
    proceedLabel: String,
    onProceed: () -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not connected") },
        text = {
            Text("You're not linked to the arm, so there's no live data to capture. Continue anyway?")
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onProceed()
            }) { Text(proceedLabel) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = {
                    onDismiss()
                    onConnect()
                }) { Text("Connect") }
            }
        },
    )
}
