package com.frezzybuilds.devnotch.share.localsend

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Eine zu sendende Datei; [open] liefert den Inhalt (z. B. aus einer content://-URI). */
class OutgoingFile(val name: String, val size: Long, val mimeType: String, val open: () -> InputStream?)

/** Ergebnis eines Sendeversuchs. */
enum class SendResult { SENT, DECLINED, BUSY, FAILED }

/**
 * Sendeseite von LocalSend v2: prepare-upload, dann jede Datei per upload. HTTPS-Geräte haben ein
 * selbstsigniertes Zertifikat; geprüft wird, dass dessen SHA-256 dem angekündigten Fingerabdruck
 * entspricht (wie die LocalSend-App selbst) – so kann sich kein anderes Gerät dazwischenschieben.
 */
class LocalSendSender(private val self: () -> DeviceInfo) {

    fun send(peer: Peer, files: List<OutgoingFile>, onProgress: (Transfer?) -> Unit): SendResult {
        if (files.isEmpty()) return SendResult.FAILED
        val dtos = files.mapIndexed { i, f -> FileDto(id = "f$i", fileName = f.name, size = f.size, fileType = f.mimeType) }
        val prepare = PrepareUploadRequest(self(), dtos.associateBy { it.id })
        val (status, body) = request(peer, "${peer.baseUrl}/prepare-upload", "POST",
            LocalSendProtocol.json.encodeToString(PrepareUploadRequest.serializer(), prepare).toByteArray())
        when (status) {
            200 -> Unit
            204 -> return SendResult.SENT
            403 -> return SendResult.DECLINED
            409 -> return SendResult.BUSY
            else -> return SendResult.FAILED
        }
        val response = runCatching { LocalSendProtocol.json.decodeFromString(PrepareUploadResponse.serializer(), body) }.getOrNull()
            ?: return SendResult.FAILED
        val total = files.sumOf { it.size }.coerceAtLeast(1)
        var done = 0L
        try {
            dtos.forEachIndexed { i, dto ->
                val token = response.files[dto.id] ?: return@forEachIndexed // Empfänger will diese Datei nicht
                val input = files[i].open() ?: return SendResult.FAILED
                val url = "${peer.baseUrl}/upload?sessionId=${response.sessionId}&fileId=${dto.id}&token=$token"
                val connection = open(peer, url, "POST")
                connection.setFixedLengthStreamingMode(dto.size)
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                input.use { stream ->
                    connection.outputStream.use { out ->
                        copy(stream, out) { n ->
                            done += n
                            onProgress(Transfer(false, peer.info.alias, files.size, (done.toFloat() / total).coerceIn(0f, 1f), dto.fileName))
                        }
                    }
                }
                if (connection.responseCode !in 200..299) return SendResult.FAILED
                connection.disconnect()
            }
        } catch (_: Exception) {
            runCatching { request(peer, "${peer.baseUrl}/cancel?sessionId=${response.sessionId}", "POST", ByteArray(0)) }
            return SendResult.FAILED
        } finally {
            onProgress(null)
        }
        return SendResult.SENT
    }

    /** Antwort auf eine Ankündigung: uns beim anderen Gerät bekannt machen (/register). */
    fun register(peer: Peer): DeviceInfo? = runCatching {
        val (status, body) = request(peer, "${peer.baseUrl}/register", "POST",
            LocalSendProtocol.json.encodeToString(DeviceInfo.serializer(), self().copy(announce = null)).toByteArray())
        if (status == 200) LocalSendProtocol.json.decodeFromString(DeviceInfo.serializer(), body) else null
    }.getOrNull()

    private fun request(peer: Peer, url: String, method: String, body: ByteArray): Pair<Int, String> {
        val connection = open(peer, url, method)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        val text = runCatching { (if (status < 400) connection.inputStream else connection.errorStream)?.use { String(it.readBytes()) } }
            .getOrNull().orEmpty()
        connection.disconnect()
        return status to text
    }

    private fun open(peer: Peer, url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.doOutput = true
        connection.connectTimeout = 5_000
        // Der Empfänger entscheidet in Ruhe (LocalSend wartet bis zu einer Minute auf „Annehmen“).
        connection.readTimeout = 90_000
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = pinnedContext(peer.info.fingerprint).socketFactory
            // Selbstsigniert, keine Hostnamen – die Identität sichert der Fingerabdruck.
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        return connection
    }

    // Kein „alles vertrauen“: Das Zertifikat muss exakt dem angekündigten Fingerabdruck entsprechen
    // (Certificate Pinning wie in der LocalSend-App). Client-Zertifikate nutzen wir nicht.
    @android.annotation.SuppressLint("CustomX509TrustManager")
    private fun pinnedContext(fingerprint: String): SSLContext {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val actual = MessageDigest.getInstance("SHA-256").digest(chain.first().encoded).joinToString("") { "%02x".format(it) }
                if (!actual.equals(fingerprint, ignoreCase = true)) {
                    throw java.security.cert.CertificateException("Fingerabdruck passt nicht zum angekündigten Gerät")
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
    }
}
