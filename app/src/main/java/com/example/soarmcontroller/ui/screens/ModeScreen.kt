package com.example.soarmcontroller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.ui.components.ConnectionGate
import com.example.soarmcontroller.ui.components.Guideline
import com.example.soarmcontroller.ui.components.GuidelinesDialog

/**
 * Shared page frame for every control mode:
 *  - a large title with a help button (opens the guidelines popup)
 *  - the mode's interactive content, gated behind an active connection
 */
@Composable
fun ModeScaffold(
    title: String,
    guidelines: List<Guideline>,
    connected: Boolean,
    onRequestConnect: () -> Unit,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showHelp = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "$title guidelines",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        ConnectionGate(connected = connected, onRequestConnect = onRequestConnect) {
            content()
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showHelp) {
        GuidelinesDialog(
            title = "$title — how it works",
            items = guidelines,
            onDismiss = { showHelp = false },
        )
    }
}
