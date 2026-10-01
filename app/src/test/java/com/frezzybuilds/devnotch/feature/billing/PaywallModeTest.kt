package com.frezzybuilds.devnotch.feature.billing

import com.revenuecat.purchases.ui.revenuecatui.CustomVariableValue
import org.junit.Assert.assertEquals
import org.junit.Test

class PaywallModeTest {

    private val realKey = "goog_AbCdEf123456"

    @Test
    fun `parses gradle value, defaults to custom`() {
        assertEquals(PaywallMode.REVENUECAT, PaywallMode.from(" RevenueCat "))
        assertEquals(PaywallMode.CUSTOM, PaywallMode.from("custom"))
        assertEquals(PaywallMode.CUSTOM, PaywallMode.from("unbekannt"))
        assertEquals(PaywallMode.CUSTOM, PaywallMode.from(null))
    }

    @Test
    fun `revenuecat ui only with running sdk and real key`() {
        assertEquals(PaywallMode.REVENUECAT, PaywallMode.resolve(PaywallMode.REVENUECAT, sdkReady = true, apiKey = realKey))
        assertEquals(PaywallMode.CUSTOM, PaywallMode.resolve(PaywallMode.REVENUECAT, sdkReady = false, apiKey = realKey))
        assertEquals(
            PaywallMode.CUSTOM,
            PaywallMode.resolve(PaywallMode.REVENUECAT, sdkReady = true, apiKey = "goog_REPLACE_WITH_YOUR_REVENUECAT_KEY")
        )
        assertEquals(PaywallMode.CUSTOM, PaywallMode.resolve(PaywallMode.CUSTOM, sdkReady = true, apiKey = realKey))
    }

    @Test
    fun `highlighted feature is passed as custom variable`() {
        assertEquals(
            mapOf("feature" to CustomVariableValue.String("KI-Token-Tracker")),
            customVariables(ProFeature.AI_TRACKER)
        )
        assertEquals(mapOf("feature" to CustomVariableValue.String("DevNotch Pro")), customVariables(null))
    }
}
