package com.frezzybuilds.devnotch.share.localsend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LocalSend-Protokoll v2 (https://github.com/localsend/protocol) – die quelloffene AirDrop-
 * Alternative für Android, iOS, macOS, Windows und Linux. Entdeckung per UDP-Multicast,
 * Übertragung per HTTP(S) im lokalen Netz; nichts läuft über das Internet.
 */
object LocalSendProtocol {
    const val MULTICAST_GROUP = "224.0.0.167"
    const val PORT = 53317
    const val VERSION = "2.1"
    const val API = "/api/localsend/v2"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
}

/** Geräteinfo (Multicast-Ankündigung, /register, „info“ in prepare-upload). */
@Serializable
data class DeviceInfo(
    val alias: String,
    val version: String = LocalSendProtocol.VERSION,
    val deviceModel: String? = null,
    val deviceType: String? = "mobile",
    val fingerprint: String,
    val port: Int = LocalSendProtocol.PORT,
    val protocol: String = "http",
    val download: Boolean = false,
    val announce: Boolean? = null
)

@Serializable
data class FileDto(
    val id: String,
    val fileName: String,
    val size: Long,
    val fileType: String,
    val sha256: String? = null,
    /** Bei reinem Text (fileType text/plain) steht hier die Nachricht. */
    val preview: String? = null
)

@Serializable
data class PrepareUploadRequest(val info: DeviceInfo, val files: Map<String, FileDto>)

@Serializable
data class PrepareUploadResponse(val sessionId: String, val files: Map<String, String>)

/** Ein im WLAN gefundenes LocalSend-Gerät. */
data class Peer(val info: DeviceInfo, val host: String, val lastSeen: Long) {
    val baseUrl: String get() = "${info.protocol}://$host:${info.port}${LocalSendProtocol.API}"
}
