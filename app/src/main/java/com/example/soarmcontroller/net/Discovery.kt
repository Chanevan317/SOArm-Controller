package com.example.soarmcontroller.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

/** Finds the laptop bridge on the local network — no IP address needed. */
object Discovery {

    private const val UDP_PORT = 8766
    private val MAGIC = "SOARM-DISCOVER-1".toByteArray()

    data class Endpoint(val host: String, val port: Int)

    /**
     * Broadcast a probe and return the first bridge that answers, or null.
     *
     * Sends to every local interface's own broadcast address as well as the
     * limited broadcast. On a phone hotspot, `255.255.255.255` tends to leave via
     * mobile data instead of the hotspot, so the per-interface addresses are what
     * actually reach the laptop.
     */
    suspend fun find(timeoutMs: Long = 3000): Endpoint? = withContext(Dispatchers.IO) {
        runCatching {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 400

                val targets = broadcastTargets()
                val buf = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < deadline) {
                    for (addr in targets) {
                        runCatching { sock.send(DatagramPacket(MAGIC, MAGIC.size, addr, UDP_PORT)) }
                    }
                    try {
                        while (true) {
                            val resp = DatagramPacket(buf, buf.size)
                            sock.receive(resp)
                            val json = runCatching {
                                JSONObject(String(resp.data, 0, resp.length, Charsets.UTF_8))
                            }.getOrNull()
                            val host = resp.address?.hostAddress
                            if (json?.optBoolean("soarm") == true && host != null) {
                                return@use Endpoint(host, json.optInt("ws_port", 8765))
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // nothing this round — probe again
                    }
                }
                null
            }
        }.getOrNull()
    }

    /** Per-interface broadcast addresses + the limited broadcast, deduped. */
    private fun broadcastTargets(): List<InetAddress> {
        val out = linkedSetOf<InetAddress>()
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    val b = ia.broadcast ?: continue
                    if (ia.address is Inet4Address) out.add(b)
                }
            }
        }
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        return out.toList()
    }

    /** Quick TCP check — is something listening at host:port? */
    suspend fun reachable(host: String, port: Int, timeoutMs: Int = 400): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                true
            }.getOrDefault(false)
        }
}
