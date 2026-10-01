package com.frezzybuilds.devnotch.feature.billing

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Zentrale Stelle für „Ist Pro freigeschaltet?“.
 *
 * Quellen: das RevenueCat-Entitlement (über [onEntitlementChanged]) und – nur in Debug-Builds –
 * eine Test-Freischaltung, weil mit dem Platzhalter-Key keine echten Käufe möglich sind.
 * Der letzte bekannte Status wird gespeichert, damit die Notch nach einem Neustart sofort
 * richtig aussieht (RevenueCat bestätigt ihn kurz darauf).
 */
class ProAccess(context: Context, private val debugBuild: Boolean) {

    private val prefs = context.getSharedPreferences("pro_access", Context.MODE_PRIVATE)

    private var entitled = prefs.getBoolean(KEY_ENTITLED, false)
    private var debugUnlocked = debugBuild && prefs.getBoolean(KEY_DEBUG_UNLOCK, false)

    private val _isPro = MutableStateFlow(entitled || debugUnlocked)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun isUnlocked(@Suppress("UNUSED_PARAMETER") feature: ProFeature): Boolean = _isPro.value

    /** Vom RevenueCat-Listener aufgerufen (CustomerInfo → Entitlement „pro“ aktiv?). */
    fun onEntitlementChanged(active: Boolean) {
        entitled = active
        prefs.edit { putBoolean(KEY_ENTITLED, active) }
        publish()
    }

    /** Nur Debug-Builds: Pro ohne Kauf testen. In Release-Builds wirkungslos. */
    fun setDebugUnlock(enabled: Boolean) {
        if (!debugBuild) return
        debugUnlocked = enabled
        prefs.edit { putBoolean(KEY_DEBUG_UNLOCK, enabled) }
        publish()
    }

    val isDebugUnlocked: Boolean get() = debugUnlocked
    val canDebugUnlock: Boolean get() = debugBuild

    private fun publish() {
        _isPro.value = entitled || debugUnlocked
    }

    private companion object {
        const val KEY_ENTITLED = "entitled"
        const val KEY_DEBUG_UNLOCK = "debug_unlock"
    }
}
