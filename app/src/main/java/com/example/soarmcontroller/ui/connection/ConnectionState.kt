package com.example.soarmcontroller.ui.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lifecycle of the link to the laptop bridge. */
enum class LinkStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Holds the connection target and status.
 *
 * NOTE: the actual socket to the laptop bridge script is not wired yet — [connect]
 * currently simulates the handshake so the UI can be exercised. Swap the body of
 * [connect] for the real transport once the on-wire protocol is settled.
 */
class ConnectionController(
    host: String = DEFAULT_HOST,
    port: String = DEFAULT_PORT,
) {
    var host by mutableStateOf(host)
    var port by mutableStateOf(port)
    var status by mutableStateOf(LinkStatus.DISCONNECTED)
        private set
    var detail by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    val isConnected: Boolean get() = status == LinkStatus.CONNECTED

    fun connect(scope: CoroutineScope) {
        if (status == LinkStatus.CONNECTING || status == LinkStatus.CONNECTED) return
        val target = "${host.trim()}:${port.trim()}"
        job?.cancel()
        job = scope.launch {
            status = LinkStatus.CONNECTING
            detail = "Reaching $target"
            delay(800) // stand-in for the real handshake
            status = LinkStatus.CONNECTED
            detail = "Linked to $target"
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        status = LinkStatus.DISCONNECTED
        detail = null
    }

    companion object {
        const val DEFAULT_HOST = "192.168.1.50"
        const val DEFAULT_PORT = "8765"

        val Saver = listSaver<ConnectionController, String>(
            save = { listOf(it.host, it.port) },
            restore = { ConnectionController(it[0], it[1]) },
        )
    }
}
