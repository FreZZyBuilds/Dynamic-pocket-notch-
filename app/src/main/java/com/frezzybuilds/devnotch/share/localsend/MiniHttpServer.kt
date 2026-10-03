package com.frezzybuilds.devnotch.share.localsend

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Eingehende Anfrage: Pfad, Query, Header und der Body als Stream (genau Content-Length Bytes). */
class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val remoteHost: String,
    val body: InputStream,
    val contentLength: Long
) {
    fun bodyText(): String = String(body.readNBytesCompat(contentLength.toInt()), Charsets.UTF_8)
}

class HttpResponse(val status: Int, val body: ByteArray = ByteArray(0), val contentType: String = "application/json") {
    companion object {
        fun json(text: String, status: Int = 200) = HttpResponse(status, text.toByteArray(Charsets.UTF_8))
        fun empty(status: Int) = HttpResponse(status)
    }
}

/**
 * Minimaler HTTP/1.1-Server für LocalSend (nur das Nötigste: Content-Length-Bodies, eine Anfrage
 * pro Verbindung). Jede Verbindung läuft in einem eigenen Thread – Uploads blockieren keine
 * anderen Anfragen.
 */
class MiniHttpServer(private val handler: (HttpRequest) -> HttpResponse) {
    private var socket: ServerSocket? = null
    private var pool: ExecutorService? = null

    /** @return tatsächlicher Port, oder null, wenn er belegt ist (z. B. LocalSend-App läuft). */
    fun start(port: Int): Int? {
        val server = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) } }.getOrNull()
            ?: return null
        socket = server
        val executor = Executors.newCachedThreadPool { r -> Thread(r, "localsend-http").apply { isDaemon = true } }
        pool = executor
        executor.execute {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                executor.execute { serve(client) }
            }
        }
        return server.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        pool?.shutdownNow()
        socket = null
        pool = null
    }

    private fun serve(client: Socket) = client.use { sock ->
        sock.soTimeout = 60_000
        val input = BufferedInputStream(sock.getInputStream())
        val requestLine = input.readLineAscii() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readLineAscii() ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        val body = LimitedInputStream(input, length)
        val response = runCatching {
            handler(HttpRequest(parts[0].uppercase(), path, query, headers, sock.inetAddress.hostAddress.orEmpty(), body, length))
        }.getOrElse { HttpResponse.empty(500) }
        body.drain()
        write(sock.getOutputStream(), response)
    }

    private fun write(out: OutputStream, response: HttpResponse) {
        val reason = when (response.status) {
            200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 403 -> "Forbidden"
            404 -> "Not Found"; 409 -> "Conflict"; else -> "Error"
        }
        val head = buildString {
            append("HTTP/1.1 ${response.status} $reason\r\n")
            if (response.body.isNotEmpty()) append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(response.body)
        out.flush()
    }
}

private fun InputStream.readLineAscii(): String? {
    val sb = StringBuilder()
    while (true) {
        val b = read()
        if (b == -1) return if (sb.isEmpty()) null else sb.toString()
        if (b == '\n'.code) return sb.toString().trimEnd('\r')
        sb.append(b.toChar())
        if (sb.length > 16_384) return null
    }
}

internal fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = ByteArray(n)
    var read = 0
    while (read < n) {
        val r = read(out, read, n - read)
        if (r == -1) break
        read += r
    }
    return if (read == n) out else out.copyOf(read)
}

/** Liest höchstens [limit] Bytes – der Rest der Verbindung gehört nicht zum Body. */
class LimitedInputStream(private val source: InputStream, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        val b = source.read()
        if (b >= 0) remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (n > 0) remaining -= n
        return n
    }

    fun drain() {
        val buf = ByteArray(8192)
        while (remaining > 0 && read(buf, 0, buf.size) > 0) Unit
    }
}
