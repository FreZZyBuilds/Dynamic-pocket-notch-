package com.frezzybuilds.devnotch.share.localsend

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket

/**
 * Multicast-Entdeckung: Wir kündigen uns an (announce = true) und hören auf andere. Auf fremde
 * Ankündigungen antworten wir per HTTP /register, ersatzweise per Multicast (announce = false).
 */
class LocalSendDiscovery(
    private val self: () -> DeviceInfo,
    private val onPeer: (Peer) -> Unit,
    private val registerBack: (Peer) -> Boolean
) {
    private var socket: MulticastSocket? = null
    private var thread: Thread? = null
    private val group: InetAddress = InetAddress.getByName(LocalSendProtocol.MULTICAST_GROUP)

    fun start(): Boolean {
        val s = runCatching {
            MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(LocalSendProtocol.PORT))
                @Suppress("DEPRECATION") joinGroup(group)
                timeToLive = 1
            }
        }.getOrNull() ?: return false
        socket = s
        thread = Thread({ listen(s) }, "localsend-discovery").apply { isDaemon = true; start() }
        return true
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        thread = null
    }

    /** Uns im Netz bekannt machen (beim Start und beim Öffnen der Geräteauswahl). */
    fun announce() = sendMulticast(self().copy(announce = true))

    private fun sendMulticast(info: DeviceInfo) {
        val s = socket ?: return
        val bytes = LocalSendProtocol.json.encodeToString(DeviceInfo.serializer(), info).toByteArray()
        Thread { runCatching { s.send(DatagramPacket(bytes, bytes.size, group, LocalSendProtocol.PORT)) } }.start()
    }

    private fun listen(s: MulticastSocket) {
        val buffer = ByteArray(4096)
        while (!s.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            runCatching { s.receive(packet) }.onFailure { return }
            val info = runCatching {
                LocalSendProtocol.json.decodeFromString(DeviceInfo.serializer(), String(packet.data, 0, packet.length, Charsets.UTF_8))
            }.getOrNull() ?: continue
            if (info.fingerprint == self().fingerprint) continue
            val peer = Peer(info, packet.address.hostAddress.orEmpty(), System.currentTimeMillis())
            onPeer(peer)
            if (info.announce == true) {
                // Antworten: bevorzugt per HTTP, sonst per Multicast.
                Thread { if (!registerBack(peer)) sendMulticast(self().copy(announce = false)) }.start()
            }
        }
    }
}
