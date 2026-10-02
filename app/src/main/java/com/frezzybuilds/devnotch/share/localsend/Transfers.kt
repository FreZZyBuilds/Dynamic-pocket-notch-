package com.frezzybuilds.devnotch.share.localsend

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Laufende Übertragung – für den Fortschritt in der Notch. */
data class Transfer(
    val incoming: Boolean,
    val peerAlias: String,
    val fileCount: Int,
    val fraction: Float,
    val currentFile: String
)

/** Eingehende Anfrage, über die der Nutzer in der Notch entscheidet. */
data class IncomingRequest(
    val id: String,
    val sender: DeviceInfo,
    val files: List<FileDto>
) {
    val totalBytes: Long get() = files.sumOf { it.size }

    /** Reiner Text (LocalSend „Text senden“): kommt im Vorschaufeld, keine Datei nötig. */
    val text: String? get() = files.singleOrNull()?.takeIf { it.fileType.startsWith("text/plain") }?.preview
}

/** Ziel für empfangene Dateien (Android: Downloads/DevNotch). */
interface FileSink {
    fun open(fileName: String, mimeType: String): OutputStream?
}

/**
 * Empfangsseite von LocalSend v2: Registrierung, Anfrage (Nutzer entscheidet), Upload je Datei mit
 * Token, Abbruch. Ohne Android-Abhängigkeiten – die Rückrufe verbinden mit Notch und Speicher.
 */
class LocalSendReceiver(
    private val self: () -> DeviceInfo,
    private val receiveEnabled: () -> Boolean,
    /** Blockiert, bis der Nutzer entschieden hat (oder die Zeit abläuft). */
    private val ask: (IncomingRequest) -> Boolean,
    private val sink: FileSink,
    private val onPeer: (Peer) -> Unit = {},
    private val onProgress: (Transfer?) -> Unit = {},
    private val onDone: (IncomingRequest, saved: Int) -> Unit = { _, _ -> },
    private val onText: (DeviceInfo, String) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis
) {
    private class Session(val id: String, val request: IncomingRequest, val tokens: Map<String, String>) {
        val received = mutableSetOf<String>()
        var bytesDone = 0L
    }

    @Volatile
    private var session: Session? = null

    fun handle(req: HttpRequest): HttpResponse {
        val api = LocalSendProtocol.API
        return when {
            req.path == "$api/register" && req.method == "POST" -> register(req)
            (req.path == "$api/info" || req.path == "/api/localsend/v1/info") && req.method == "GET" ->
                HttpResponse.json(LocalSendProtocol.json.encodeToString(DeviceInfo.serializer(), self()))
            req.path == "$api/prepare-upload" && req.method == "POST" -> prepare(req)
            req.path == "$api/upload" && req.method == "POST" -> upload(req)
            req.path == "$api/cancel" && req.method == "POST" -> cancel(req)
            else -> HttpResponse.empty(404)
        }
    }

    private fun register(req: HttpRequest): HttpResponse {
        val info = runCatching { LocalSendProtocol.json.decodeFromString(DeviceInfo.serializer(), req.bodyText()) }.getOrNull()
            ?: return HttpResponse.empty(400)
        if (info.fingerprint != self().fingerprint) onPeer(Peer(info, req.remoteHost, now()))
        return HttpResponse.json(LocalSendProtocol.json.encodeToString(DeviceInfo.serializer(), self().copy(announce = null)))
    }

    private fun prepare(req: HttpRequest): HttpResponse {
        if (!receiveEnabled()) return HttpResponse.empty(403)
        if (session != null) return HttpResponse.empty(409)
        val body = runCatching { LocalSendProtocol.json.decodeFromString(PrepareUploadRequest.serializer(), req.bodyText()) }.getOrNull()
            ?: return HttpResponse.empty(400)
        if (body.files.isEmpty()) return HttpResponse.empty(400)
        onPeer(Peer(body.info, req.remoteHost, now()))
        val request = IncomingRequest(UUID.randomUUID().toString(), body.info, body.files.values.toList())
        if (!ask(request)) return HttpResponse.empty(403)
        request.text?.let { text ->
            onText(body.info, text)
            return HttpResponse.empty(204)
        }
        val tokens = body.files.keys.associateWith { UUID.randomUUID().toString() }
        val s = Session(UUID.randomUUID().toString(), request, tokens)
        session = s
        onProgress(Transfer(true, body.info.alias, request.files.size, 0f, request.files.first().fileName))
        val response = PrepareUploadResponse(s.id, tokens)
        return HttpResponse.json(LocalSendProtocol.json.encodeToString(PrepareUploadResponse.serializer(), response))
    }

    private fun upload(req: HttpRequest): HttpResponse {
        val s = session ?: return HttpResponse.empty(403)
        val fileId = req.query["fileId"] ?: return HttpResponse.empty(400)
        if (req.query["sessionId"] != s.id || s.tokens[fileId] != req.query["token"]) return HttpResponse.empty(403)
        val file = s.request.files.firstOrNull { it.id == fileId } ?: return HttpResponse.empty(403)
        val out = sink.open(file.fileName, file.fileType) ?: return HttpResponse.empty(500)
        val total = s.request.totalBytes.coerceAtLeast(1)
        val ok = runCatching {
            out.use { stream ->
                copy(req.body, stream) { n ->
                    synchronized(s) { s.bytesDone += n }
                    onProgress(Transfer(true, s.request.sender.alias, s.request.files.size, (s.bytesDone.toFloat() / total).coerceIn(0f, 1f), file.fileName))
                }
            }
        }.isSuccess
        if (!ok) return HttpResponse.empty(500)
        val finished = synchronized(s) {
            s.received += fileId
            s.received.size == s.request.files.size
        }
        if (finished) {
            session = null
            onProgress(null)
            onDone(s.request, s.received.size)
        }
        return HttpResponse.empty(200)
    }

    private fun cancel(req: HttpRequest): HttpResponse {
        val s = session
        if (s != null && (req.query["sessionId"] == null || req.query["sessionId"] == s.id)) {
            session = null
            onProgress(null)
        }
        return HttpResponse.empty(200)
    }
}

/** Kopiert in 64-KB-Blöcken und meldet jeden Block (für den Fortschritt). */
internal fun copy(input: InputStream, output: OutputStream, onChunk: (Int) -> Unit) {
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        output.write(buffer, 0, n)
        onChunk(n)
    }
}
