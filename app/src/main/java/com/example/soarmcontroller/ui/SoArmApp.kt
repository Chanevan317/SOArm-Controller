package com.example.soarmcontroller.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.soarmcontroller.data.AppStore
import com.example.soarmcontroller.ui.components.FloatingNavBar
import com.example.soarmcontroller.ui.components.SoArmTopBar
import com.example.soarmcontroller.ui.connection.ConnectionController
import com.example.soarmcontroller.ui.connection.ConnectionSheet
import com.example.soarmcontroller.ui.navigation.Destination
import com.example.soarmcontroller.ui.screens.AboutScreen
import com.example.soarmcontroller.ui.screens.GoToScreen
import com.example.soarmcontroller.ui.screens.JogScreen
import com.example.soarmcontroller.ui.screens.SequenceScreen
import com.example.soarmcontroller.ui.screens.VoiceScreen

/**
 * Root of the UI: top bar + four bottom-nav modes, with an About screen layered
 * on top when opened from the overflow menu.
 */
@Composable
fun SoArmApp(
    store: AppStore,
    resolvedDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    val context = LocalContext.current
    val connection = rememberSaveable(saver = ConnectionController.Saver) { ConnectionController() }
    var current by rememberSaveable { mutableStateOf(Destination.JOG) }
    var showConnectionSheet by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val openConnection = { showConnectionSheet = true }
    val openSource = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, AppInfo.REPO_URL.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                SoArmTopBar(
                    linkStatus = connection.status,
                    resolvedDark = resolvedDark,
                    onConnectionClick = openConnection,
                    onToggleTheme = onToggleTheme,
                    onOpenSource = openSource,
                    onOpenAbout = { showAbout = true },
                )
            },
            bottomBar = {
                FloatingNavBar(current = current, onSelect = { current = it })
            },
        ) { innerPadding ->
            val connected = connection.isConnected
            when (current) {
                Destination.JOG ->
                    JogScreen(connected, openConnection, innerPadding)
                Destination.GO_TO ->
                    GoToScreen(store.presets, connected, openConnection, innerPadding)
                Destination.SEQUENCE ->
                    SequenceScreen(store.sequences, connected, openConnection, innerPadding)
                Destination.VOICE ->
                    VoiceScreen(connected, openConnection, innerPadding)
            }
        }

        if (showAbout) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AboutScreen(
                    resolvedDark = resolvedDark,
                    onToggleTheme = onToggleTheme,
                    onOpenSource = openSource,
                    onBack = { showAbout = false },
                )
            }
        }
    }

    if (showConnectionSheet) {
        ConnectionSheet(
            controller = connection,
            onDismiss = { showConnectionSheet = false },
        )
    }
}
