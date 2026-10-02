package com.frezzybuilds.devnotch.share.localsend

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.system.SystemEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * LocalSend in DevNotch – AirDrop für alle Geräte im selben WLAN. Läuft, solange jemand es braucht:
 * der Overlay-Dienst (wenn „Empfangen“ an ist) oder die Geräteauswahl beim Senden.
 */
object LocalSend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    /** Start/Stopp nacheinander auf einem eigenen Thread – Reihenfolge bleibt erhalten. */
    private val lifecycle = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "localsend-lifecycle").apply { isDaemon = true } }
        .asCoroutineDispatcher()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    /** Gefundene Geräte (zuletzt gesehen zuerst). */
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _transfer = MutableStateFlow<Transfer?>(null)
    /** Laufende Übertragung für die Notch. */
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val users = mutableSetOf<String>()
    private var server: MiniHttpServer? = null
    private var discovery: LocalSendDiscovery? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var port = LocalSendProtocol.PORT
    private val fingerprint = UUID.randomUUID().toString().replace("-", "")
    private lateinit var appContext: Context

    /** Offene Anfragen: Nutzer entscheidet in der Notch. */
    private val decisions = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

    fun selfInfo(): DeviceInfo {
        val alias = appContext.appContainer.notchSettings.localSendAlias.ifBlank { "DevNotch (${Build.MODEL})" }
        return DeviceInfo(alias = alias, deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(), fingerprint = fingerprint, port = port)
    }

    /** Meldet einen Nutzer an; der Netzwerkstart läuft im Hintergrund (nie auf dem Main-Thread). */
    fun acquire(context: Context, user: String) {
        appContext = context.applicationContext
        scope.launch(lifecycle) { start(user) }
    }

    @Synchronized
    private fun start(user: String) {
        users += user
        if (server != null) {
            discovery?.announce()
            return
        }
        val receiver = LocalSendReceiver(
            self = ::selfInfo,
            receiveEnabled = { appContext.appContainer.notchSettings.localSendReceive },
            ask = ::askUser,
            sink = DownloadsSink(appContext),
            onPeer = ::addPeer,
            onProgress = { _transfer.value = it },
            onDone = { request, count ->
                main.post {
                    PeekCenter.show(Peek.System(SystemEvent.SHARE, "✓", if (count == 1) request.files.first().fileName else "$count Dateien empfangen", "Downloads", 0xFF30D158L))
                }
            },
            onText = { sender, text ->
                main.post {
                    appContext.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("LocalSend", text))
                    PeekCenter.show(Peek.System(SystemEvent.SHARE, "💬", "Text von ${sender.alias}", "Kopiert", 0xFF64D2FFL))
                }
            }
        )
        val http = MiniHttpServer(receiver::handle)
        // 53317 ist Standard; läuft daneben die LocalSend-App, weichen wir aus.
        port = http.start(LocalSendProtocol.PORT) ?: http.start(LocalSendProtocol.PORT + 1) ?: return
        server = http
        multicastLock = appContext.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("devnotch-localsend")?.apply { setReferenceCounted(false); acquire() }
        val sender = LocalSendSender(::selfInfo)
        discovery = LocalSendDiscovery(::selfInfo, ::addPeer) { peer -> sender.register(peer)?.let { addPeer(peer.copy(info = it)) } != null }
            .also { if (it.start()) it.announce() }
        _running.value = true
    }

    fun release(user: String) {
        scope.launch(lifecycle) { stop(user) }
    }

    @Synchronized
    private fun stop(user: String) {
        users -= user
        if (users.isNotEmpty()) return
        discovery?.stop()
        server?.stop()
        multicastLock?.release()
        discovery = null
        server = null
        multicastLock = null
        decisions.values.forEach { it.complete(false) }
        _running.value = false
    }

    /** Neu suchen (Geräteauswahl). */
    fun refresh() = discovery?.announce()

    private fun addPeer(peer: Peer) {
        _peers.value = (listOf(peer) + _peers.value.filter { it.info.fingerprint != peer.info.fingerprint })
            .filter { System.currentTimeMillis() - it.lastSeen < PEER_TTL_MS }
    }

    /** Läuft im Server-Thread: Notch fragen, bis zu einer Minute auf die Antwort warten. */
    private fun askUser(request: IncomingRequest): Boolean {
        val future = CompletableFuture<Boolean>()
        decisions[request.id] = future
        main.post { PeekCenter.show(Peek.ShareRequest(request)) }
        val accepted = runCatching { future.get(DECISION_TIMEOUT_S, TimeUnit.SECONDS) }.getOrDefault(false)
        decisions.remove(request.id)
        main.post { (PeekCenter.current.value as? Peek.ShareRequest)?.takeIf { it.request.id == request.id }?.let(PeekCenter::dismiss) }
        return accepted
    }

    /** Antwort aus der Notch („Annehmen“/„Ablehnen“). */
    fun decide(requestId: String, accept: Boolean) {
        decisions[requestId]?.complete(accept)
    }

    /** Sendet [uris] an [peer]; Fortschritt und Ergebnis erscheinen in der Notch. */
    fun send(context: Context, peer: Peer, uris: List<Uri>) {
        val resolver = context.contentResolver
        val files = uris.map { uri ->
            var name = uri.lastPathSegment ?: "Datei"
            var size = -1L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            }
            if (size < 0) size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: 0L
            OutgoingFile(name, size, resolver.getType(uri) ?: "application/octet-stream") { resolver.openInputStream(uri) }
        }
        scope.launch {
            val result = LocalSendSender(::selfInfo).send(peer, files) { _transfer.value = it }
            main.post {
                val (symbol, title, tint) = when (result) {
                    SendResult.SENT -> Triple("✓", "An ${peer.info.alias} gesendet", 0xFF30D158L)
                    SendResult.DECLINED -> Triple("✕", "${peer.info.alias} hat abgelehnt", 0xFFFF453AL)
                    SendResult.BUSY -> Triple("⏳", "${peer.info.alias} ist beschäftigt", 0xFFFF9F0AL)
                    SendResult.FAILED -> Triple("⚠", "Senden fehlgeschlagen", 0xFFFF453AL)
                }
                PeekCenter.show(Peek.System(SystemEvent.SHARE, symbol, title, if (files.size > 1) "${files.size} Dateien" else null, tint))
            }
            release("send")
        }
    }

    private const val PEER_TTL_MS = 5 * 60_000L
    private const val DECISION_TIMEOUT_S = 60L
}

/** Speichert in Downloads/DevNotch (ab Android 10 ohne Berechtigung), sonst im App-Ordner. */
private class DownloadsSink(private val context: Context) : FileSink {
    override fun open(fileName: String, mimeType: String): OutputStream? {
        val safe = fileName.substringAfterLast('/').replace(Regex("[\\\\:*?\"<>|]"), "_").ifBlank { "Datei" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DevNotch")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            FileOutputStream(File(dir, safe))
        }
    }
}
