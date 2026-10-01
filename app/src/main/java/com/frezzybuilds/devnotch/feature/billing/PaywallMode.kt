package com.frezzybuilds.devnotch.feature.billing

/**
 * Welche Paywall gezeigt wird – per Gradle wählbar (`revenuecat.paywall` in local.properties
 * oder `-Prevenuecat.paywall=…`):
 * - [CUSTOM]: eigene [PaywallDialog]-Compose-UI, Pakete aus der aktuellen Offering.
 * - [REVENUECAT]: RevenueCat Paywalls (purchases-ui), Layout/Texte im RevenueCat-Dashboard
 *   pflegbar, ohne App-Update änderbar.
 */
enum class PaywallMode {
    CUSTOM, REVENUECAT;

    companion object {
        fun from(value: String?): PaywallMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: CUSTOM

        /**
         * RevenueCat-UI nur, wenn das SDK mit echtem Key läuft – sonst (Platzhalter, kein Play
         * Store) würde sie nur einen Fehler zeigen; dann greift die eigene Paywall.
         */
        fun resolve(configured: PaywallMode, sdkReady: Boolean, apiKey: String): PaywallMode =
            if (configured == REVENUECAT && sdkReady && !RevenueCatBilling.isPlaceholderKey(apiKey)) REVENUECAT
            else CUSTOM
    }
}
