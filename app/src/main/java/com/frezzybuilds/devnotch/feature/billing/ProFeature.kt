package com.frezzybuilds.devnotch.feature.billing

/**
 * Paywall-Feature-Flags.
 *
 * Free: Basis-Notch (Pille, Edge-Player, Mediensteuerung, Gesten), Focus Timer, Notizen,
 * Clipboard mit den letzten [ProPlan.FREE_CLIPBOARD_ENTRIES] Einträgen und bis zu
 * [ProPlan.FREE_SHORTCUTS] Projekt-Shortcuts.
 *
 * Pro: alles hier aufgeführte.
 */
enum class ProFeature(val title: String, val description: String) {
    GITHUB_HEATMAP("GitHub-Heatmap", "Contributions der letzten 16 Wochen, Profil und offene PRs"),
    AI_TRACKER("KI-Token-Tracker", "Kosten & Tokens von OpenAI, Anthropic, OpenRouter, Gemini, Ollama – auch in der Notch"),
    UNLIMITED_CLIPBOARD("Unbegrenzte Zwischenablage", "Statt der letzten ${ProPlan.FREE_CLIPBOARD_ENTRIES} Einträge die komplette Historie"),
    UNLIMITED_SHORTCUTS("Unbegrenzte Projekt-Shortcuts", "Statt ${ProPlan.FREE_SHORTCUTS} beliebig viele App- und URL-Kacheln")
}

object ProPlan {
    const val FREE_CLIPBOARD_ENTRIES = 5
    const val FREE_SHORTCUTS = 3

    /** RevenueCat-Entitlement, das alle Pro-Features freischaltet. */
    const val ENTITLEMENT_ID = "pro"
}

/** Grenzen abhängig vom Pro-Status – an einer Stelle, damit UI und Datenhaltung übereinstimmen. */
object ProLimits {
    /** null = unbegrenzt (keine Kürzung). */
    fun clipboardEntries(isPro: Boolean): Int? = if (isPro) null else ProPlan.FREE_CLIPBOARD_ENTRIES

    fun canAddShortcut(isPro: Boolean, currentCount: Int): Boolean =
        isPro || currentCount < ProPlan.FREE_SHORTCUTS
}
