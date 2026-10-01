package com.frezzybuilds.devnotch.ui

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BatteryPercentTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `unsupported capacity falls back to the battery broadcast`() {
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 41)
                .putExtra(BatteryManager.EXTRA_SCALE, 50)
        )
        assertEquals(82, batteryPercent(app))
    }

    @Test
    fun `no usable value means no battery text instead of a garbage number`() {
        assertNull(batteryPercent(app))
    }
}
