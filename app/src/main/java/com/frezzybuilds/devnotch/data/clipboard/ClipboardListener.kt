package com.frezzybuilds.devnotch.data.clipboard

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Speichert kopierte Texte in der Room-Historie.
 *
 * Einschränkung ab Android 10: Das System meldet Änderungen und gibt den Inhalt nur an die
 * App mit Fokus (oder die Standard-Tastatur) heraus. Deshalb ruft der Overlay-Service
 * zusätzlich [captureCurrentClip] auf, sobald die aufgeklappte Notch Fokus bekommt.
 */
class ClipboardListener(
    context: Context,
    private val repository: ClipboardRepository,
    private val scope: CoroutineScope
) : ClipboardManager.OnPrimaryClipChangedListener {

    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)

    fun start() = clipboard.addPrimaryClipChangedListener(this)

    fun stop() = clipboard.removePrimaryClipChangedListener(this)

    override fun onPrimaryClipChanged() = captureCurrentClip()

    fun captureCurrentClip() {
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
        // Als sensibel markierte Inhalte (z. B. Passwörter aus Passwort-Managern) nie speichern.
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return

        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        scope.launch { repository.save(text) }
    }

    private companion object {
        // ClipDescription.EXTRA_IS_SENSITIVE (API 33), als String auch auf älteren Versionen nutzbar.
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
