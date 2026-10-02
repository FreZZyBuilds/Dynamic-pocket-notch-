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

/** In welchem Fenstertyp die Notch gerade hängt. */
enum class OverlayHost {
    /** Normales Overlay (`TYPE_APPLICATION_OVERLAY`) – unter der Statusleiste, nie über der Sperre. */
    APP,

    /** Overlay der Bedienungshilfe (`TYPE_ACCESSIBILITY_OVERLAY`) – auch über dem Sperrbildschirm. */
    ACCESSIBILITY;

    companion object {
        /**
         * Nur während der Sperre (schon ab Bildschirm aus, damit beim Einschalten nichts springt)
         * und nur, wenn die Notch dort erscheinen soll und die Bedienungshilfe verbunden ist.
         * Entsperrt immer das normale Overlay: Das Accessibility-Fenster läge sonst auch über
         * der heruntergezogenen Benachrichtigungsleiste.
         */
        fun choose(lock: DeviceLock, mode: LockscreenMode, accessibilityConnected: Boolean): OverlayHost =
            if (lock.isLocked && mode == LockscreenMode.SHOW && accessibilityConnected) ACCESSIBILITY else APP
    }
}
