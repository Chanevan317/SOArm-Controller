package com.example.soarmcontroller.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.example.soarmcontroller.data.AppStore
import com.example.soarmcontroller.net.BridgeClient
import com.example.soarmcontroller.net.Discovery
import com.example.soarmcontroller.ui.components.FloatingNavBar
import com.example.soarmcontroller.ui.components.SoArmTopBar
import com.example.soarmcontroller.ui.connection.ConnectionController
import com.example.soarmcontroller.ui.connection.ConnectionSheet
import com.example.soarmcontroller.ui.connection.LinkStatus
import com.example.soarmcontroller.ui.navigation.Destination
import com.example.soarmcontroller.ui.screens.AboutScreen
import com.example.soarmcontroller.ui.screens.GoToScreen
import com.example.soarmcontroller.ui.screens.JogScreen
import com.example.soarmcontroller.ui.screens.SequenceScreen
import com.example.soarmcontroller.ui.screens.VoiceScreen
import com.example.soarmcontroller.ui.theme.ThemeMode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Root of the UI: top bar + four bottom-nav modes, with an animated About screen
 * layered on top when opened from the overflow menu.
 */
@Composable
fun SoArmApp(
    store: AppStore,
    bridge: BridgeClient,
    themeMode: ThemeMode,
    resolvedDark: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val connection = rememberSaveable(saver = ConnectionController.Saver) { ConnectionController() }
    var current by rememberSaveable { mutableStateOf(Destination.JOG) }
    var showConnectionSheet by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    val linkStatus by bridge.status.collectAsState()
    val connected = linkStatus == LinkStatus.CONNECTED
    val snackbar = remember { SnackbarHostState() }

    // Auto-find the bridge on the network (cable first, then UDP discovery).
    var autoConnect by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(autoConnect) {
        while (isActive && autoConnect) {
            if (!bridge.isConnected) {
                val ep = if (Discovery.reachable("127.0.0.1", 8765)) {
                    Discovery.Endpoint("127.0.0.1", 8765)
                } else {
                    Discovery.find(2500)
                }
                if (ep != null && !bridge.isConnected) {
                    bridge.connect(ep.host, ep.port.toString())
                }
            }
            delay(3000)
        }
    }

    // Tell the bridge which mode is on screen (and re-send on every reconnect).
    LaunchedEffect(current, connected) {
        if (connected) bridge.setMode(current.wire)
    }
    LaunchedEffect(Unit) {
        bridge.events.collect { snackbar.showSnackbar(it.text) }
    }

    // Live progress of a back gesture while About is open (0 = open, 1 = dismissed).
    val aboutBack = remember { Animatable(0f) }

    // Back on a main screen asks before leaving.
    BackHandler(enabled = !showAbout) { showExitConfirm = true }

    // Back on About slides it away, following the gesture where the OS supports it.
    PredictiveBackHandler(enabled = showAbout) { progress ->
        try {
            progress.collect { event -> aboutBack.snapTo(event.progress.coerceIn(0f, 1f)) }
            showAbout = false
            aboutBack.snapTo(0f)
        } catch (_: CancellationException) {
            aboutBack.animateTo(0f, tween(220))
        }
    }

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
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                SoArmTopBar(
                    linkStatus = linkStatus,
                    themeMode = themeMode,
                    onConnectionClick = openConnection,
                    onThemeModeChange = onThemeModeChange,
                    onOpenSource = openSource,
                    onOpenAbout = { showAbout = true },
                )
            },
            bottomBar = {
                FloatingNavBar(current = current, onSelect = { current = it })
            },
        ) { innerPadding ->
            when (current) {
                Destination.JOG ->
                    JogScreen(bridge, connected, openConnection, innerPadding)
                Destination.GO_TO ->
                    GoToScreen(store.presets, bridge, connected, openConnection, innerPadding)
                Destination.SEQUENCE ->
                    SequenceScreen(store.sequences, bridge, connected, openConnection, innerPadding)
                Destination.VOICE ->
                    VoiceScreen(bridge, connected, openConnection, innerPadding)
            }
        }

        AnimatedVisibility(
            visible = showAbout,
            enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(180)),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = aboutBack.value
                        translationX = size.width * 0.16f * p
                        val s = 1f - 0.06f * p
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.15f * p
                    },
            ) {
                AboutScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenSource = openSource,
                    onBack = { showAbout = false },
                )
            }
        }
    }

    if (showConnectionSheet) {
        ConnectionSheet(
            controller = connection,
            bridge = bridge,
            autoConnect = autoConnect,
            onAutoConnectChange = { autoConnect = it },
            onDismiss = { showConnectionSheet = false },
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit SOArm Controller?") },
            text = { Text("You'll be disconnected from the arm.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    activity?.finish()
                }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Stay") }
            },
        )
    }
}
