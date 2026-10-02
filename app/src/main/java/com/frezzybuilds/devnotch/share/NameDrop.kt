package com.frezzybuilds.devnotch.share

import java.io.ByteArrayOutputStream

/** Eigene Kontaktkarte für NameDrop – nur lokal gespeichert, nur auf Wunsch geteilt. */
data class ContactCard(val name: String, val phone: String = "", val email: String = "") {
    val isComplete: Boolean get() = name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank())

    /** vCard 3.0 – verstehen Android-Kontakte, iPhone-Kamera (QR) und Desktop-Programme. */
    fun toVCard(): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        val parts = name.trim().split(Regex("\\s+"))
        val last = if (parts.size > 1) parts.last() else ""
        val first = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else parts.first()
        append("N:${esc(last)};${esc(first)};;;\r\n")
        append("FN:${esc(name.trim())}\r\n")
        if (phone.isNotBlank()) append("TEL;TYPE=CELL:${esc(phone.trim())}\r\n")
        if (email.isNotBlank()) append("EMAIL;TYPE=INTERNET:${esc(email.trim())}\r\n")
        append("END:VCARD\r\n")
    }

    companion object {
        private fun esc(value: String) = value
            .replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

        /** Liest Name, Telefon und E-Mail aus einer empfangenen vCard (2.1/3.0/4.0). */
        fun fromVCard(vcard: String): ContactCard? {
            val lines = vcard.replace("\r\n ", "").replace("\n ", "").lines()
            fun value(prefix: String) = lines.firstOrNull { it.uppercase().startsWith(prefix) }
                ?.substringAfter(':')?.trim()?.replace("\\,", ",")?.replace("\\;", ";")?.replace("\\\\", "\\")
            val name = value("FN") ?: value("N:")?.split(';')?.filter { it.isNotBlank() }?.reversed()?.joinToString(" ")
            if (name.isNullOrBlank()) return null
            return ContactCard(name, value("TEL").orEmpty(), value("EMAIL").orEmpty())
        }
    }
}

/** NDEF-Nachricht mit einem MIME-Record (hier: text/vcard), ohne Android-Klassen kodiert. */
object Ndef {
    const val VCARD_MIME = "text/vcard"

    fun mimeRecord(mime: String, payload: ByteArray): ByteArray {
        val type = mime.toByteArray(Charsets.US_ASCII)
        val short = payload.size < 256
        val out = ByteArrayOutputStream()
        // MB | ME | (SR) | TNF=0x02 (MIME-Medientyp)
        out.write(0x80 or 0x40 or (if (short) 0x10 else 0) or 0x02)
        out.write(type.size)
        if (short) {
            out.write(payload.size)
        } else {
            out.write(payload.size ushr 24 and 0xFF); out.write(payload.size ushr 16 and 0xFF)
            out.write(payload.size ushr 8 and 0xFF); out.write(payload.size and 0xFF)
        }
        out.write(type)
        out.write(payload)
        return out.toByteArray()
    }

    /** Payload des ersten MIME-Records mit [mime] – für empfangene Rohdaten. */
    fun mimePayload(message: ByteArray, mime: String): ByteArray? {
        var i = 0
        while (i < message.size) {
            val header = message[i].toInt() and 0xFF
            val short = header and 0x10 != 0
            val hasId = header and 0x08 != 0
            val typeLength = message[i + 1].toInt() and 0xFF
            var p = i + 2
            val payloadLength: Int
            if (short) {
                payloadLength = message[p].toInt() and 0xFF
                p += 1
            } else {
                payloadLength = ((message[p].toInt() and 0xFF) shl 24) or ((message[p + 1].toInt() and 0xFF) shl 16) or
                    ((message[p + 2].toInt() and 0xFF) shl 8) or (message[p + 3].toInt() and 0xFF)
                p += 4
            }
            val idLength = if (hasId) message[p++].toInt() and 0xFF else 0
            val type = String(message, p, typeLength, Charsets.US_ASCII)
            p += typeLength + idLength
            if (header and 0x07 == 0x02 && type.equals(mime, ignoreCase = true)) {
                return message.copyOfRange(p, p + payloadLength)
            }
            if (header and 0x40 != 0) break
            i = p + payloadLength
        }
        return null
    }
}

/**
 * NFC-Forum-Type-4-Tag in Software (Host Card Emulation): Ein anderes Handy liest uns wie einen
 * NFC-Aufkleber mit unserer Kontaktkarte. Reine Logik – der [android.nfc.cardemulation.HostApduService]
 * reicht nur die APDUs durch.
 */
class Type4TagEmulator(ndefMessage: ByteArray, private val onFullyRead: () -> Unit = {}) {
    private val ndefFile: ByteArray = byteArrayOf((ndefMessage.size ushr 8).toByte(), ndefMessage.size.toByte()) + ndefMessage
    private val ccFile: ByteArray = byteArrayOf(
        0x00, 0x0F, // CCLEN
        0x20, // Mapping-Version 2.0
        0x00, 0x7F, // MLe: max. 127 Byte je READ BINARY
        0x00, 0x7F, // MLc
        0x04, 0x06, // NDEF-File-Control-TLV
        0xE1.toByte(), 0x04, // File-ID
        (ndefFile.size ushr 8).toByte(), ndefFile.size.toByte(), // max. NDEF-Größe
        0x00, // Lesen erlaubt
        0xFF.toByte() // Schreiben verboten
    )
    private var selected: ByteArray? = null
    private var appSelected = false
    private var reported = false

    fun process(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_WRONG
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        return when (ins) {
            0xA4 -> select(apdu, p1)
            0xB0 -> read(apdu)
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(apdu: ByteArray, p1: Int): ByteArray {
        val lc = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
        if (apdu.size < 5 + lc) return SW_WRONG
        val data = apdu.copyOfRange(5, 5 + lc)
        if (p1 == 0x04) {
            appSelected = data.contentEquals(NDEF_AID)
            selected = null
            return if (appSelected) SW_OK else SW_NOT_FOUND
        }
        if (!appSelected) return SW_NOT_FOUND
        selected = when {
            data.contentEquals(byteArrayOf(0xE1.toByte(), 0x03)) -> ccFile
            data.contentEquals(byteArrayOf(0xE1.toByte(), 0x04)) -> ndefFile
            else -> return SW_NOT_FOUND
        }
        return SW_OK
    }

    private fun read(apdu: ByteArray): ByteArray {
        val file = selected ?: return SW_NOT_FOUND
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val le = if (apdu.size > 4) (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        if (offset > file.size) return SW_WRONG_OFFSET
        val end = minOf(file.size, offset + le)
        // Fertig, sobald das Ende der Nachricht gelesen wurde (nicht schon beim Längenfeld allein).
        if (file === ndefFile && end == file.size && end > 2 && !reported) {
            reported = true
            onFullyRead()
        }
        return file.copyOfRange(offset, end) + SW_OK
    }

    companion object {
        val NDEF_AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG = byteArrayOf(0x67, 0x00)
        val SW_WRONG_OFFSET = byteArrayOf(0x6B, 0x00)
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    }
}
