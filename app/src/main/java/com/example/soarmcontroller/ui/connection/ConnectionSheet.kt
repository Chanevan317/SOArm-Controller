package com.example.soarmcontroller.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Bottom sheet for pointing the app at the laptop bridge. Reached from the
 * connection pill in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSheet(
    controller: ConnectionController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Connect to laptop", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Enter the address of the bridge script running on your laptop " +
                    "(same Wi-Fi network).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = controller.host,
                    onValueChange = { controller.host = it },
                    label = { Text("Host / IP") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = controller.port,
                    onValueChange = { controller.port = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.width(112.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            controller.detail?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { controller.disconnect() },
                    enabled = controller.status != LinkStatus.DISCONNECTED,
                    modifier = Modifier.weight(1f),
                ) { Text("Disconnect") }
                Button(
                    onClick = { controller.connect(scope) },
                    enabled = controller.status == LinkStatus.DISCONNECTED ||
                        controller.status == LinkStatus.ERROR,
                    modifier = Modifier.weight(1f),
                ) { Text("Connect") }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "The transport isn't wired yet — this simulates the handshake.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
