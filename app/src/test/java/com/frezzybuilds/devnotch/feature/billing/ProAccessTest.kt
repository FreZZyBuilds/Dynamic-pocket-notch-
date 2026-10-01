package com.frezzybuilds.devnotch.feature.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("pro_access", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `free by default`() {
        val access = ProAccess(context, debugBuild = true)
        assertFalse(access.isPro.value)
        ProFeature.entries.forEach { assertFalse(access.isUnlocked(it)) }
    }

    @Test
    fun `active entitlement unlocks pro and survives restart`() {
        ProAccess(context, debugBuild = false).onEntitlementChanged(true)

        val restarted = ProAccess(context, debugBuild = false)
        assertTrue(restarted.isPro.value)

        restarted.onEntitlementChanged(false)
        assertFalse(restarted.isPro.value)
    }

    @Test
    fun `debug unlock works only in debug builds`() {
        val release = ProAccess(context, debugBuild = false)
        release.setDebugUnlock(true)
        assertFalse(release.isPro.value)
        assertFalse(release.canDebugUnlock)

        val debug = ProAccess(context, debugBuild = true)
        debug.setDebugUnlock(true)
        assertTrue(debug.isPro.value)

        // Ein später installierter Release-Build ignoriert das gespeicherte Debug-Flag.
        assertFalse(ProAccess(context, debugBuild = false).isPro.value)
    }

    @Test
    fun `limits follow pro status`() {
        assertEquals(ProPlan.FREE_CLIPBOARD_ENTRIES, ProLimits.clipboardEntries(isPro = false))
        assertNull(ProLimits.clipboardEntries(isPro = true))

        assertTrue(ProLimits.canAddShortcut(isPro = false, currentCount = ProPlan.FREE_SHORTCUTS - 1))
        assertFalse(ProLimits.canAddShortcut(isPro = false, currentCount = ProPlan.FREE_SHORTCUTS))
        assertTrue(ProLimits.canAddShortcut(isPro = true, currentCount = 100))
    }

    @Test
    fun `placeholder api key is detected`() {
        assertTrue(RevenueCatBilling.isPlaceholderKey("goog_REPLACE_WITH_YOUR_REVENUECAT_KEY"))
        assertTrue(RevenueCatBilling.isPlaceholderKey(""))
        assertFalse(RevenueCatBilling.isPlaceholderKey("goog_AbCdEf123456"))
    }

    @Test
    fun `paywall intent round trip`() {
        val intent = android.content.Intent().putExtra(Paywall.EXTRA_FEATURE, ProFeature.AI_TRACKER.name)
        assertEquals(ProFeature.AI_TRACKER, Paywall.featureFrom(intent))
        assertNull(Paywall.featureFrom(android.content.Intent().putExtra(Paywall.EXTRA_FEATURE, "NOPE")))
        assertNull(Paywall.featureFrom(null))
    }
}
