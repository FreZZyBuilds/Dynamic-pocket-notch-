package com.frezzybuilds.devnotch.share

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.system.SystemEvent

/**
 * Empfängt eine per NameDrop (NFC) gelesene Kontaktkarte: Hinweis in der Notch und das
 * Kontaktformular mit vorausgefüllten Feldern – gespeichert wird erst, wenn du bestätigst.
 * Braucht keine Kontakte-Berechtigung.
 */
class NameDropReceiveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val card = readCard(intent)
        if (card != null) {
            PeekCenter.show(Peek.System(SystemEvent.SHARE, "👤", card.name, "NameDrop", 0xFF64D2FFL))
            runCatching { startActivity(insertIntent(card)) }
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun readCard(intent: Intent): ContactCard? {
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>()?.toTypedArray()
        } ?: return null
        val payload = messages.asSequence().mapNotNull { Ndef.mimePayload(it.toByteArray(), Ndef.VCARD_MIME) }.firstOrNull()
            ?: return null
        return ContactCard.fromVCard(String(payload, Charsets.UTF_8))
    }

    companion object {
        fun insertIntent(card: ContactCard) = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, card.name)
            if (card.phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, card.phone)
            if (card.email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, card.email)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
