package com.example.soarmcontroller.net

import com.example.soarmcontroller.ui.connection.LinkStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Live telemetry from the laptop bridge (`state` frames). */
data class ArmTelemetry(
    val mode: String = "",
    val stopped: Boolean = false,
    val joints: Map<String, Double> = emptyMap(),
    val ee: Ee? = null,
) {
    data class Ee(val x: Double, val y: Double, val z: Double)
}

/** Latest `seq_state` frame — pose count / playback / captured poses. */
data class SeqSnapshot(
    val captured: Int = 0,
    val playing: Boolean = false,
    val released: Boolean = false,
    val poses: List<List<Double>> = emptyList(),
)

/** A one-off `ack` or `error` from the bridge, for transient UI (snackbar). */
data class BridgeEvent(val ok: Boolean, val text: String)

/**
 * WebSocket transport to the laptop bridge (`ws://host:port`). One JSON object
 * per frame; the contract lives in `laptop_run_scripts/soarm_bridge/protocol.py`.
 *
 * Send methods are no-ops while disconnected. All state is exposed as flows.
 */
class BridgeClient {

    private val http = OkHttpClient.Builder()
        .pingInterval(20L, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var ws: WebSocket? = null
    private var target: String = ""

    private val _status = MutableStateFlow(LinkStatus.DISCONNECTED)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _detail = MutableStateFlow<String?>(null)
    val detail: StateFlow<String?> = _detail.asStateFlow()

    private val _telemetry = MutableStateFlow(ArmTelemetry())
    val telemetry: StateFlow<ArmTelemetry> = _telemetry.asStateFlow()

    private val _seq = MutableStateFlow(SeqSnapshot())
    val seq: StateFlow<SeqSnapshot> = _seq.asStateFlow()

    private val _events = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BridgeEvent> = _events.asSharedFlow()

    val isConnected: Boolean get() = _status.value == LinkStatus.CONNECTED

    // ---- lifecycle --------------------------------------------------------

    fun connect(host: String, port: String) {
        val h = host.trim()
        val p = port.trim()
        if (h.isEmpty() || p.isEmpty()) {
            _status.value = LinkStatus.ERROR
            _detail.value = "Enter a host and port"
            return
        }
        closeSocket()
        target = "$h:$p"
        _status.value = LinkStatus.CONNECTING
        _detail.value = "Connecting to $target"
        ws = http.newWebSocket(Request.Builder().url("ws://$target").build(), Listener())
    }

    fun disconnect() {
        closeSocket()
        _status.value = LinkStatus.DISCONNECTED
        _detail.value = null
    }

    private fun closeSocket() {
        runCatching { ws?.close(1000, "client closing") }
        ws = null
    }

    // ---- outbound frames ------------------------------------------------

    private fun send(obj: JSONObject) {
        ws?.send(obj.toString())
    }

    private fun frame(type: String, build: JSONObject.() -> Unit = {}): JSONObject =
        JSONObject().put("type", type).apply(build)

    fun hello() = send(frame("hello") { put("client", "soarm-android"); put("proto", 1) })

    fun setMode(mode: String) = send(frame("mode") { put("mode", mode) })

    fun stop() = send(frame("stop"))

    fun resume() = send(frame("resume"))

    fun jog(
        vx: Float, vy: Float, vz: Float,
        pitch: Float, roll: Float, grip: Float,
        speed: Int, enabled: Boolean,
    ) = send(frame("jog") {
        put("vx", vx.toDouble()); put("vy", vy.toDouble()); put("vz", vz.toDouble())
        put("pitch", pitch.toDouble()); put("roll", roll.toDouble()); put("grip", grip.toDouble())
        put("speed", speed); put("enabled", enabled)
    })

    fun goTo(x: Double, y: Double, z: Double) =
        send(frame("goto") { put("x", x); put("y", y); put("z", z) })

    /** Sequence / pose mode: capture | delete | clear | play | stop | release | hold. */
    fun seqCmd(cmd: String, index: Int? = null, steps: List<Map<String, Any>>? = null) =
        send(frame("seq") {
            put("cmd", cmd)
            index?.let { put("index", it) }
            steps?.let { put("steps", JSONArray(it.map { s -> JSONObject(s) })) }
        })

    /** Sequence mode: absolute joint target from the viewer-style sliders. */
    fun seqJoints(joints: Map<String, Double>) =
        send(frame("seq") { put("q", JSONObject(joints as Map<*, *>)) })

    fun voice(token: String) = send(frame("voice") { put("token", token) })

    // ---- inbound ------------------------------------------------------

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            _status.value = LinkStatus.CONNECTED
            _detail.value = "Linked to $target"
            hello()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val m = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (m.optString("type")) {
                "state" -> _telemetry.value = ArmTelemetry(
                    mode = m.optString("mode"),
                    stopped = m.optBoolean("stopped"),
                    joints = m.optJSONObject("joints")?.let { j ->
                        buildMap {
                            val it = j.keys()
                            while (it.hasNext()) {
                                val k = it.next()
                                put(k, j.optDouble(k))
                            }
                        }
                    } ?: emptyMap(),
                    ee = m.optJSONObject("ee")?.let {
                        ArmTelemetry.Ee(it.optDouble("x"), it.optDouble("y"), it.optDouble("z"))
                    },
                )

                "seq_state" -> _seq.value = SeqSnapshot(
                    captured = m.optInt("captured"),
                    playing = m.optBoolean("playing"),
                    released = m.optBoolean("released"),
                    poses = m.optJSONArray("poses")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val p = arr.optJSONArray(i)
                            if (p == null) emptyList<Double>()
                            else (0 until p.length()).map { p.optDouble(it) }
                        }
                    } ?: emptyList(),
                )

                "ack" -> _events.tryEmit(
                    BridgeEvent(
                        ok = m.optBoolean("ok"),
                        text = "${m.optString("of")}: " +
                            if (m.optBoolean("ok")) "ok" else m.optString("reason").ifEmpty { "rejected" },
                    ),
                )

                "error" -> _events.tryEmit(BridgeEvent(ok = false, text = m.optString("msg")))

                "welcome" -> _detail.value = "Linked to ${m.optString("robot").ifEmpty { target }}"

                "pong" -> Unit
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _status.value = LinkStatus.ERROR
            _detail.value = t.message ?: "Connection failed"
            ws = null
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (_status.value != LinkStatus.ERROR) {
                _status.value = LinkStatus.DISCONNECTED
                _detail.value = null
            }
            ws = null
        }
    }
}
