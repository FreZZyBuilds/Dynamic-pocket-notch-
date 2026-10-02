package com.frezzybuilds.devnotch.notify

/** Was auf dem Sperrbildschirm von einer Benachrichtigung zu sehen ist. */
enum class LockContent(val label: String) {
    FULL("Alles"),
    APP_ONLY("Nur App"),
    HIDDEN("Nichts")
}

/** Welche Live-Ansichten erkannt werden. */
data class LivePrefs(
    val calls: Boolean = true,
    val navigation: Boolean = true,
    val timers: Boolean = true,
    val progress: Boolean = true
) {
    val any: Boolean get() = calls || navigation || timers || progress
}

/** Einstellungen für Benachrichtigungen in der Notch. */
data class NotifyPrefs(
    val enabled: Boolean = true,
    val durationSeconds: Float = DEFAULT_DURATION_SECONDS,
    val lockContent: LockContent = LockContent.APP_ONLY,
    val skipOngoing: Boolean = true,
    val skipSilent: Boolean = true,
    val respectDnd: Boolean = true,
    val blockedApps: Set<String> = emptySet(),
    val live: LivePrefs = LivePrefs()
) {
    companion object {
        const val DEFAULT_DURATION_SECONDS = 5f
        val DURATION_RANGE = 2f..10f
    }
}

/** Reine Filter-Logik: Soll diese Benachrichtigung als Peek durch die Notch laufen? */
object NotificationRules {
    fun shouldPeek(n: NotchNotification, prefs: NotifyPrefs, dndActive: Boolean, ownPackage: String): Boolean = when {
        !prefs.enabled -> false
        n.packageName == ownPackage -> false
        // Medien zeigt die Notch ohnehin als Player; Gruppen-Sammelmeldungen doppeln nur.
        n.isMedia || n.isGroupSummary -> false
        n.packageName in prefs.blockedApps -> false
        prefs.skipOngoing && n.ongoing -> false
        prefs.skipSilent && n.silent -> false
        prefs.respectDnd && dndActive -> false
        n.title.isBlank() && n.text.isNullOrBlank() -> false
        // System-Sammelmeldungen („6 weitere Benachrichtigungen“), Dienste und Statusmeldungen.
        n.packageName in SYSTEM_PACKAGES -> false
        n.category in QUIET_CATEGORIES -> false
        else -> true
    }

    private val SYSTEM_PACKAGES = setOf("android", "com.android.systemui", "com.samsung.android.app.smartcapture")
    private val QUIET_CATEGORIES = setOf(
        android.app.Notification.CATEGORY_SERVICE,
        android.app.Notification.CATEGORY_SYSTEM,
        android.app.Notification.CATEGORY_STATUS,
        android.app.Notification.CATEGORY_TRANSPORT
    )

    /** Was gesperrt angezeigt wird: Titel/Text nur bei [LockContent.FULL]. */
    fun redact(n: NotchNotification, locked: Boolean, lockContent: LockContent): NotchNotification? = when {
        !locked -> n
        lockContent == LockContent.HIDDEN -> null
        lockContent == LockContent.APP_ONLY -> n.copy(title = n.appLabel, text = "Neue Benachrichtigung", icon = null, actions = emptyList())
        else -> n
    }
}
