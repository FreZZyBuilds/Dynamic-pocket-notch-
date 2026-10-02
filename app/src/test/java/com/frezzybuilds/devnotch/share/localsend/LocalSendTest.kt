package com.frezzybuilds.devnotch.share.localsend

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/** Ende-zu-Ende über echte Sockets auf localhost: Sender ↔ Empfänger nach LocalSend v2. */
class LocalSendTest {

    private val received = ConcurrentHashMap<String, ByteArrayOutputStream>()
    private val sink = object : FileSink {
        override fun open(fileName: String, mimeType: String): OutputStream = ByteArrayOutputStream().also { received[fileName] = it }
    }
    private var server: MiniHttpServer? = null

    @After
    fun stop() {
        server?.stop()
    }

    private fun receiver(accept: Boolean = true, enabled: Boolean = true, onDone: (Int) -> Unit = {}, onText: (String) -> Unit = {}): Peer {
        val self = DeviceInfo(alias = "Empfänger", fingerprint = "recv", port = 0)
        val r = LocalSendReceiver(
            self = { self },
            receiveEnabled = { enabled },
            ask = { accept },
            sink = sink,
            onDone = { _, n -> onDone(n) },
            onText = { _, text -> onText(text) }
        )
        val http = MiniHttpServer(r::handle)
        val port = http.start(0)!!
        server = http
        return Peer(self.copy(port = port), "127.0.0.1", System.currentTimeMillis())
    }

    private val sender = LocalSendSender { DeviceInfo(alias = "Sender", fingerprint = "send") }

    private fun file(name: String, bytes: ByteArray) = OutgoingFile(name, bytes.size.toLong(), "application/octet-stream") { ByteArrayInputStream(bytes) }

    @Test
    fun `files arrive completely after the user accepts`() {
        var done = -1
        val peer = receiver(onDone = { done = it })
        val photo = ByteArray(300_000) { (it % 256).toByte() }
        val doc = "Hallo LocalSend".toByteArray()
        val progress = mutableListOf<Float>()
        val result = sender.send(peer, listOf(file("foto.jpg", photo), file("notiz.txt", doc))) { t -> t?.let { progress += it.fraction } }
        assertEquals(SendResult.SENT, result)
        assertArrayEquals(photo, received["foto.jpg"]!!.toByteArray())
        assertArrayEquals(doc, received["notiz.txt"]!!.toByteArray())
        assertEquals(2, done)
        assertTrue("Fortschritt steigt bis 100 %", progress.isNotEmpty() && progress.last() == 1f)
    }

    @Test
    fun `declined or disabled receivers get nothing`() {
        assertEquals(SendResult.DECLINED, sender.send(receiver(accept = false), listOf(file("a.bin", ByteArray(10)))) {})
        server?.stop()
        assertEquals(SendResult.DECLINED, sender.send(receiver(enabled = false), listOf(file("b.bin", ByteArray(10)))) {})
        assertNull(received["a.bin"])
        assertNull(received["b.bin"])
    }

    @Test
    fun `plain text is delivered without upload`() {
        var text: String? = null
        val peer = receiver(onText = { text = it })
        val prepare = PrepareUploadRequest(
            DeviceInfo(alias = "iPhone", fingerprint = "x"),
            mapOf("t" to FileDto("t", "text.txt", 5, "text/plain", preview = "Hallo"))
        )
        val url = java.net.URL("${peer.baseUrl}/prepare-upload")
        val c = url.openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.outputStream.use { it.write(LocalSendProtocol.json.encodeToString(PrepareUploadRequest.serializer(), prepare).toByteArray()) }
        assertEquals(204, c.responseCode)
        assertEquals("Hallo", text)
    }

    @Test
    fun `register answers with our device info`() {
        val peer = receiver()
        val info = sender.register(peer)
        assertEquals("Empfänger", info?.alias)
    }
}
