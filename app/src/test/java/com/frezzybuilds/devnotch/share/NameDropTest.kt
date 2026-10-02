package com.frezzybuilds.devnotch.share

import com.frezzybuilds.devnotch.notify.NotchNotification
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class NameDropTest {

    private val card = ContactCard("Lena Sommer", "+49 151 2345678", "lena@example.com")

    @Test
    fun `vcard roundtrip keeps name, phone and email`() {
        val vcard = card.toVCard()
        assertTrue(vcard.startsWith("BEGIN:VCARD\r\nVERSION:3.0"))
        assertTrue("N: Nachname;Vorname", "N:Sommer;Lena;;;" in vcard)
        assertEquals(card, ContactCard.fromVCard(vcard))
        // Sonderzeichen werden maskiert und wieder gelesen.
        val tricky = ContactCard("Müller, Jan; Dr.", "0151", "")
        assertEquals(tricky, ContactCard.fromVCard(tricky.toVCard()))
        assertNull(ContactCard.fromVCard("BEGIN:VCARD\r\nEND:VCARD"))
    }

    @Test
    fun `ndef mime record encodes and decodes short and long payloads`() {
        val short = "hallo".toByteArray()
        val record = Ndef.mimeRecord(Ndef.VCARD_MIME, short)
        assertEquals(0xD2, record[0].toInt() and 0xFF) // MB|ME|SR|TNF=2
        assertArrayEquals(short, Ndef.mimePayload(record, "text/vcard"))
        val long = ByteArray(1000) { (it % 251).toByte() }
        assertArrayEquals(long, Ndef.mimePayload(Ndef.mimeRecord(Ndef.VCARD_MIME, long), "TEXT/VCARD"))
        assertNull(Ndef.mimePayload(record, "text/plain"))
    }

    @Test
    fun `type 4 tag emulation serves the vcard to a reader`() {
        val message = Ndef.mimeRecord(Ndef.VCARD_MIME, card.toVCard().toByteArray())
        var reads = 0
        val tag = Type4TagEmulator(message) { reads++ }
        val ok = Type4TagEmulator.SW_OK
        fun apdu(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

        // Ohne Anwendung keine Dateien.
        assertArrayEquals(Type4TagEmulator.SW_NOT_FOUND, tag.process(apdu(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x03)))
        assertArrayEquals(ok, tag.process(apdu(0x00, 0xA4, 0x04, 0x00, 0x07, 0xD2, 0x76, 0x00, 0x00, 0x85, 0x01, 0x01, 0x00)))
        assertArrayEquals(ok, tag.process(apdu(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x03)))
        val cc = tag.process(apdu(0x00, 0xB0, 0x00, 0x00, 0x0F))
        assertEquals(17, cc.size)
        assertEquals(0x20, cc[2].toInt())

        assertArrayEquals(ok, tag.process(apdu(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x04)))
        val nlen = tag.process(apdu(0x00, 0xB0, 0x00, 0x00, 0x02))
        val length = ((nlen[0].toInt() and 0xFF) shl 8) or (nlen[1].toInt() and 0xFF)
        assertEquals(message.size, length)
        // Wie ein echter Leser in 127-Byte-Häppchen lesen.
        val out = ByteArrayOutputStream()
        var offset = 2
        while (offset < length + 2) {
            val le = minOf(0x7F, length + 2 - offset)
            val r = tag.process(apdu(0x00, 0xB0, offset shr 8, offset and 0xFF, le))
            out.write(r, 0, r.size - 2)
            offset += le
        }
        assertArrayEquals(message, out.toByteArray())
        assertEquals("„gesendet“ genau einmal", 1, reads)
        assertEquals(card, ContactCard.fromVCard(String(Ndef.mimePayload(out.toByteArray(), Ndef.VCARD_MIME)!!)))
    }

    @Test
    fun `wallet payments are recognised like apple pay`() {
        fun n(pkg: String, title: String, text: String?) = NotchNotification("k", pkg, "Wallet", title, text)
        assertEquals(Wallet.Payment("REWE Markt", "12,50 €"), Wallet.parsePayment(n("com.google.android.apps.walletnfcrel", "REWE Markt", "12,50 €")))
        assertEquals("€ 3.99", Wallet.parsePayment(n("com.samsung.android.spay", "Bezahlt bei Bäckerei", "€ 3.99"))?.amount)
        assertNull("Play-Dienste nur mit Zahlungswort", Wallet.parsePayment(n("com.google.android.gms", "Speicher fast voll", "1,50 €")))
        assertEquals("1,50 €", Wallet.parsePayment(n("com.google.android.gms", "Zahlung an Kiosk", "1,50 €"))?.amount)
        assertNull("andere Apps nie", Wallet.parsePayment(n("org.telegram.messenger", "Lena", "Schuldest mir 12,50 €")))
        assertNull("ohne Betrag nicht", Wallet.parsePayment(n("com.google.android.apps.walletnfcrel", "Karte hinzugefügt", null)))
    }
}
