package com.frezzybuilds.devnotch.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosticsTest {

    @After
    fun reset() {
        PerfMonitor.reset()
        PerfMonitor.state = "Pille"
    }

    @Test
    fun `slow frames are counted per state, pauses are ignored`() {
        PerfMonitor.state = "Peek · System"
        PerfMonitor.record(16_000_000L)
        PerfMonitor.record(50_000_000L) // Ruckler
        PerfMonitor.record(900_000_000L) // Pause, kein Ruckler
        PerfMonitor.state = "aufgeklappt · Übersicht"
        PerfMonitor.record(16_000_000L)
        val stats = PerfMonitor.stats()
        assertEquals(PerfMonitor.Stat(2, 1, 50f), stats["Peek · System"])
        assertEquals(PerfMonitor.Stat(1, 0, 0f), stats["aufgeklappt · Übersicht"])
        assertEquals(1, PerfMonitor.janks.value.size)
        assertTrue(PerfMonitor.summary().startsWith("Peek · System: 2 Bilder, 1 ruckelig (50 %)"))
    }

    @Test
    fun `report contains device line and perf summary`() {
        PerfMonitor.state = "Pille"
        PerfMonitor.record(40_000_000L)
        val report = DiagnosticsReport.build(ApplicationProvider.getApplicationContext<Context>())
        assertTrue(report.startsWith("DevNotch "))
        assertTrue("Pille: 1 Bilder" in report)
        assertTrue("— ANR / Abstürze (Android) —" in report)
    }
}
