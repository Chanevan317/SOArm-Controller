package com.example.soarmcontroller.ui.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** Lifecycle of the link to the laptop bridge. */
enum class LinkStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Holds the connection target (host / port) for the connection sheet's text
 * fields. The actual socket lives in [com.example.soarmcontroller.net.BridgeClient];
 * this is just the saveable form state.
 */
class ConnectionController(
    host: String = DEFAULT_HOST,
    port: String = DEFAULT_PORT,
) {
    var host by mutableStateOf(host)
    var port by mutableStateOf(port)

    companion object {
        const val DEFAULT_HOST = "192.168.1.50"
        const val DEFAULT_PORT = "8765"

        val Saver = listSaver<ConnectionController, String>(
            save = { listOf(it.host, it.port) },
            restore = { ConnectionController(it[0], it[1]) },
        )
    }
}
