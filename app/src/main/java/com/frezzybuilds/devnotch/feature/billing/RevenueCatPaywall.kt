package com.frezzybuilds.devnotch.feature.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.BuildConfig
import com.frezzybuilds.devnotch.appContainer
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.CustomVariableValue
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog as RevenueCatPaywallDialog

/**
 * Einstieg für alle Paywall-Aufrufe: wählt je nach [BuildConfig.PAYWALL_MODE] die eigene
 * [PaywallDialog] oder die RevenueCat-Paywall. Schließt sich, sobald Pro aktiv ist.
 */
@Composable
fun PaywallHost(highlight: ProFeature?, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val mode = remember {
        PaywallMode.resolve(
            configured = PaywallMode.from(BuildConfig.PAYWALL_MODE),
            sdkReady = container.billing.isReady,
            apiKey = BuildConfig.REVENUECAT_API_KEY
        )
    }
    when (mode) {
        PaywallMode.REVENUECAT -> RevenueCatPaywall(highlight, onDismiss)
        PaywallMode.CUSTOM -> PaywallDialog(highlight = highlight, onDismiss = onDismiss)
    }
}

/**
 * RevenueCat Paywalls (purchases-ui): Design, Texte und Pakete kommen aus dem Dashboard
 * (Paywall an der aktuellen Offering). Wird nur gezeigt, solange das Entitlement „pro“ fehlt.
 * Das angefragte Feature steht im Template als `{{ custom.feature }}` zur Verfügung.
 */
@Composable
private fun RevenueCatPaywall(highlight: ProFeature?, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val isPro by container.proAccess.isPro.collectAsStateWithLifecycle()
    // Mit aktivem Entitlement zeigt RevenueCat den Dialog gar nicht erst an – dann direkt schließen.
    LaunchedEffect(isPro) { if (isPro) onDismiss() }
    if (isPro) return

    val options = remember {
        PaywallDialogOptions.Builder()
            .setRequiredEntitlementIdentifier(ProPlan.ENTITLEMENT_ID)
            .setShouldDisplayDismissButton(true)
            .setCustomVariables(customVariables(highlight))
            .setDismissRequest(onDismiss)
            .setListener(object : PaywallListener {
                override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                    container.billing.apply(customerInfo)
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    container.billing.apply(customerInfo)
                }
            })
            .build()
    }
    RevenueCatPaywallDialog(options)
}

/** Custom Variables für Paywall-Templates im RevenueCat-Dashboard. */
internal fun customVariables(highlight: ProFeature?): Map<String, CustomVariableValue> =
    mapOf("feature" to CustomVariableValue.String(highlight?.title ?: "DevNotch Pro"))
