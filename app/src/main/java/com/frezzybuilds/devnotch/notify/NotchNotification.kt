package com.frezzybuilds.devnotch.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import android.content.Intent
import android.app.RemoteInput
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

/** Eine Aktion aus der Benachrichtigung (z. B. „Annehmen“, „Als gelesen markieren“). */
data class NotchAction(
    val title: String,
    val intent: PendingIntent?,
    /** Verlangt Texteingabe (Antworten) – läuft über [ReplyAction], nicht als einfacher Knopf. */
    val needsInput: Boolean = false
)

/**
 * Direktantwort der App (Aktion mit [RemoteInput]), wie im Android-Benachrichtigungsfeld:
 * Der Text geht an die App, ohne sie zu öffnen.
 */
class ReplyAction(
    val title: String,
    private val intent: PendingIntent,
    private val remoteInputs: Array<RemoteInput>
) {
    /** Schickt [text] an die App. @return false, wenn die App den Intent nicht mehr annimmt. */
    fun send(context: Context, text: String): Boolean = runCatching {
        val results = Bundle().apply {
            remoteInputs.filter { it.allowFreeFormInput }.forEach { putCharSequence(it.resultKey, text) }
        }
        val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        }
        intent.send(context, 0, fillIn)
        true
    }.getOrDefault(false)
}

/**
 * Neutrales Abbild einer Benachrichtigung – nur, was die Notch braucht. Wird nie gespeichert
 * oder übertragen; lebt nur im Arbeitsspeicher, solange die Benachrichtigung aktiv ist.
 */
data class NotchNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String? = null,
    /** Großes Icon (Avatar, Abbiege-Pfeil …) oder App-Icon. */
    val icon: Bitmap? = null,
    val postTime: Long = 0L,
    val category: String? = null,
    val ongoing: Boolean = false,
    val contentIntent: PendingIntent? = null,
    val actions: List<NotchAction> = emptyList(),
    val progress: Int = 0,
    val progressMax: Int = 0,
    val progressIndeterminate: Boolean = false,
    val usesChronometer: Boolean = false,
    /** Notification.when – Startzeit eines Anrufs/einer Stoppuhr bzw. Ende eines Countdowns. */
    val whenTime: Long = 0L,
    val chronometerCountDown: Boolean = false,
    val isGroupSummary: Boolean = false,
    /** Stille/unwichtige Benachrichtigung (Wichtigkeit ≤ niedrig). */
    val silent: Boolean = false,
    val isMedia: Boolean = false,
    /** CallStyle-Intents (Android 12+), falls die Telefon-App sie liefert. */
    val answerIntent: PendingIntent? = null,
    val declineIntent: PendingIntent? = null,
    val hangUpIntent: PendingIntent? = null,
    val isCallStyle: Boolean = false,
    /** Direktantwort, falls die App eine anbietet (Messenger, SMS, Mail …). */
    val reply: ReplyAction? = null,
    /** Leitfarbe aus dem App-Icon (ARGB) – tönt den Hintergrund des Peeks. */
    val accent: Int? = null
) {
    val progressFraction: Float? =
        if (progressMax > 0 && !progressIndeterminate) (progress.toFloat() / progressMax).coerceIn(0f, 1f) else null

    companion object {
        /** Liest eine StatusBarNotification aus (Framework-Typen bleiben hier gekapselt). */
        fun from(context: Context, sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?): NotchNotification? {
            val n = sbn.notification ?: return null
            val extras = n.extras ?: return null
            val pm = context.packageManager
            val app = AppVisuals.of(context, sbn.packageName)
            val appLabel = app.label
            val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
                ?.toString()?.trim().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.trim()
            val icon = runCatching {
                (n.getLargeIcon() ?: n.smallIcon)?.loadDrawable(context)?.toBitmap(96, 96)
            }.getOrNull() ?: app.icon

            val silent = ranking?.let { map ->
                val r = NotificationListenerService.Ranking()
                map.getRanking(sbn.key, r) && r.importance <= android.app.NotificationManager.IMPORTANCE_LOW
            } ?: false
            val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()

            @Suppress("DEPRECATION")
            fun intentExtra(key: String): PendingIntent? = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) extras.getParcelable(key, PendingIntent::class.java)
                else extras.getParcelable(key) as? PendingIntent
            }.getOrNull()

            return NotchNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = title.ifEmpty { appLabel },
                text = text?.takeIf { it.isNotEmpty() },
                icon = icon,
                postTime = sbn.postTime,
                category = n.category,
                ongoing = sbn.isOngoing,
                contentIntent = n.contentIntent,
                actions = n.actions.orEmpty().map { a ->
                    NotchAction(a.title?.toString().orEmpty(), a.actionIntent, needsInput = !a.remoteInputs.isNullOrEmpty())
                },
                progress = extras.getInt(Notification.EXTRA_PROGRESS),
                progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX),
                progressIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
                usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
                whenTime = n.`when`,
                chronometerCountDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
                isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                silent = silent,
                isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
                answerIntent = intentExtra("android.answerIntent"),
                declineIntent = intentExtra("android.declineIntent"),
                hangUpIntent = intentExtra("android.hangUpIntent"),
                isCallStyle = template.endsWith("CallStyle"),
                accent = app.accent,
                reply = n.actions.orEmpty().firstNotNullOfOrNull { a ->
                    val inputs = a.remoteInputs?.filter { it.allowFreeFormInput }
                    if (a.actionIntent != null && !inputs.isNullOrEmpty()) {
                        ReplyAction(a.title?.toString().orEmpty().ifBlank { "Antworten" }, a.actionIntent, a.remoteInputs)
                    } else {
                        null
                    }
                }
            )
        }
    }
}

/**
 * Löst einen PendingIntent einer anderen App aus. Ab Android 14 muss der Sender den Start einer
 * Activity aus dem Hintergrund ausdrücklich erlauben (DevNotch darf das als Overlay-App).
 */
fun PendingIntent.sendFromNotch(context: Context): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        send(context, 0, null, null, null, null, options)
    } else {
        send()
    }
    true
}.getOrDefault(false)

/**
 * App-Name, -Icon und Leitfarbe je Paket – einmal bestimmt, dann aus dem Speicher. Vorher kostete
 * jede Benachrichtigung (Maps aktualisiert sekündlich) erneut PackageManager-Abfragen.
 */
object AppVisuals {
    class Entry(val label: String, val icon: Bitmap?, val accent: Int?)

    private val cache = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > 64
    }

    @Synchronized
    fun of(context: Context, packageName: String): Entry = cache.getOrPut(packageName) {
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
        val icon = runCatching { pm.getApplicationIcon(packageName).toBitmap(96, 96) }.getOrNull()
        Entry(label, icon, icon?.let(::accentOf))
    }

    /** Kräftige Farbe des Icons (Telegram blau, WhatsApp grün …), sonst die häufigste. */
    fun accentOf(icon: Bitmap): Int? = runCatching {
        val palette = androidx.palette.graphics.Palette.from(icon).maximumColorCount(12).generate()
        (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch)?.rgb
    }.getOrNull()
}