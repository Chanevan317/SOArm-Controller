package com.example.soarmcontroller.ui.connection

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.soarmcontroller.net.BridgeClient
import com.example.soarmcontroller.ui.AppInfo

/**
 * Connection status + controls. The app finds the bridge automatically; this
 * sheet is mostly a status view, with a manual entry as a fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSheet(
    controller: ConnectionController,
    bridge: BridgeClient,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val status by bridge.status.collectAsState()
    val detail by bridge.detail.collectAsState()
    val uriHandler = LocalUriHandler.current
    var showManual by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Laptop bridge", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status == LinkStatus.CONNECTING ||
                    (autoConnect && status != LinkStatus.CONNECTED)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(16.dp).width(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    when {
                        status == LinkStatus.CONNECTED -> detail ?: "Connected"
                        status == LinkStatus.CONNECTING -> detail ?: "Connecting…"
                        autoConnect -> "Searching your Wi-Fi for the bridge…"
                        else -> detail ?: "Not connected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status == LinkStatus.ERROR && !autoConnect)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status == LinkStatus.CONNECTED) {
                    OutlinedButton(
                        onClick = { onAutoConnectChange(false); bridge.disconnect() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Disconnect") }
                } else {
                    Button(
                        onClick = { onAutoConnectChange(true) },
                        enabled = !autoConnect,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (autoConnect) "Searching…" else "Search again") }
                }
                TextButton(
                    onClick = { showManual = !showManual },
                    modifier = Modifier.weight(1f),
                ) { Text(if (showManual) "Hide manual" else "Enter manually") }
            }

            AnimatedVisibility(visible = showManual) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = controller.host,
                            onValueChange = { controller.host = it.trim() },
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
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onAutoConnectChange(false)
                            bridge.connect(controller.host, controller.port)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Connect to this address") }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            Text("First time — set up the laptop", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SetupStep("1", "On your laptop, download the laptop_run_scripts folder from the project on GitHub.")
            SetupStep("2", "In that folder, run:  python run_bridge.py")
            SetupStep("3", "Keep the phone and laptop on the same Wi-Fi. The app connects on its own.")
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = { uriHandler.openUri(AppInfo.SETUP_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open setup instructions on GitHub") }
        }
    }
}

@Composable
private fun SetupStep(n: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(n, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
