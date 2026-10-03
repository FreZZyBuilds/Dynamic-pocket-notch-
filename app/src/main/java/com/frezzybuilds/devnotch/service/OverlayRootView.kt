package com.frezzybuilds.devnotch.service

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Wurzel-View des Overlay-Fensters. Ist das Fenster fokussierbar (Notizen offen), landet die
 * Zurück-Taste hier statt bei der App dahinter – ohne Behandlung würde sie verpuffen. Sie
 * klappt die Notch ein. Eine offene Bildschirmtastatur verbraucht das erste Zurück selbst.
 */
@SuppressLint("ViewConstructor")
internal class OverlayRootView(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Reine Flag-Logik, getrennt testbar. */
internal object OverlayWindowFlags {
    fun isFocusable(expanded: Boolean, contentWantsFocus: Boolean): Boolean = expanded && contentWantsFocus
}
