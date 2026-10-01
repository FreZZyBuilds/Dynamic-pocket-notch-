package com.frezzybuilds.devnotch.service

/** Ob die Notch auf dem Sperrbildschirm erscheint. */
enum class LockscreenMode(val label: String) {
    /** Sichtbar, aber nur Musik, Timer und Peeks ohne private Inhalte. */
    SHOW("Anzeigen"),
    HIDE("Ausblenden")
}

/** Gerätezustand aus Sicht der Notch. */
enum class DeviceLock {
    UNLOCKED,

    /** Bildschirm an, Sperrbildschirm sichtbar. */
    LOCKED,

    SCREEN_OFF;

    val isLocked: Boolean get() = this != UNLOCKED

    companion object {
        /** Reine Entscheidung – getrennt testbar. */
        fun from(screenOn: Boolean, keyguardLocked: Boolean): DeviceLock = when {
            !screenOn -> SCREEN_OFF
            keyguardLocked -> LOCKED
            else -> UNLOCKED
        }

        /** Overlay während der Sperre unsichtbar und nicht berührbar schalten? */
        fun hideOverlay(lock: DeviceLock, mode: LockscreenMode): Boolean =
            lock == SCREEN_OFF || (lock == LOCKED && mode == LockscreenMode.HIDE)
    }
}
