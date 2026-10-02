package com.frezzybuilds.devnotch.share

import android.content.Context
import android.content.Intent
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.peek.Peek
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.system.SystemEvent

/** Einstiege aus der Notch: NameDrop, Dateien senden (LocalSend), Wallet. */
object ShareActions {

    /** Startet NameDrop mit der gespeicherten Kontaktkarte; ohne Karte geht es in die Einstellungen. */
    fun startNameDrop(context: Context) {
        val card = context.appContainer.notchSettings.contactCard
        if (!card.isComplete) {
            PeekCenter.show(Peek.System(SystemEvent.SHARE, "👤", "Kontaktkarte fehlt", "Einrichten", 0xFFFF9F0AL))
            context.startActivity(
                Intent(context, com.frezzybuilds.devnotch.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            return
        }
        NameDropSession.start(card)
        PeekCenter.show(Peek.NameDrop())
    }

    /** Dateiauswahl → Gerät im WLAN wählen → senden. */
    fun pickAndSend(context: Context) {
        context.startActivity(
            Intent(context, LocalSendShareActivity::class.java)
                .setAction(LocalSendShareActivity.ACTION_PICK_FILES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Google Wallet / Samsung Wallet: öffnen und Zahlungen erkennen (Apple-Pay-Bestätigung). */
object Wallet {
    private val APPS = listOf(
        "com.google.android.apps.walletnfcrel", // Google Wallet
        "com.samsung.android.spay", // Samsung Wallet
        "com.samsung.android.samsungpay.gear"
    )

    /** Zahlungs-Benachrichtigungen kommen auch von den Play-Diensten („Google Pay“). */
    private val PAYMENT_SOURCES = APPS + "com.google.android.gms"

    fun installedApp(context: Context): String? =
        APPS.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun open(context: Context): Boolean {
        val pkg = installedApp(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    private val AMOUNT = Regex("""(?:(€|EUR|\$|USD|£|GBP|CHF)\s?(\d{1,3}(?:[.,\s]\d{3})*[.,]\d{2}))|(?:(\d{1,3}(?:[.\s]\d{3})*[.,]\d{2})\s?(€|EUR|\$|USD|£|GBP|CHF))""")
    private val PAID_WORDS = Regex("(?i)bezahl|zahlung|payment|paid|transaktion|transaction|kauf|purchase")

    /** Erkannte Zahlung: Händler und Betrag (wie angezeigt, z. B. „12,50 €“). */
    data class Payment(val merchant: String, val amount: String)

    fun parsePayment(n: NotchNotification): Payment? {
        if (n.packageName !in PAYMENT_SOURCES) return null
        val text = "${n.title} ${n.text.orEmpty()}"
        val match = AMOUNT.find(text) ?: return null
        // Play-Dienste melden vieles – dort nur mit eindeutigem Zahlungswort.
        if (n.packageName == "com.google.android.gms" && !PAID_WORDS.containsMatchIn(text)) return null
        val amount = match.value.trim()
        val merchant = n.title.replace(amount, "").trim(' ', '·', '-', ':').ifBlank { n.appLabel }
        return Payment(merchant, amount)
    }
}
