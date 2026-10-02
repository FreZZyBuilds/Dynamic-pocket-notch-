package com.frezzybuilds.devnotch.service

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.frezzybuilds.devnotch.ui.NotchContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class LockStateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `device lock decision`() {
        assertEquals(DeviceLock.SCREEN_OFF, DeviceLock.from(screenOn = false, keyguardLocked = true))
        assertEquals(DeviceLock.LOCKED, DeviceLock.from(screenOn = true, keyguardLocked = true))
        assertEquals(DeviceLock.UNLOCKED, DeviceLock.from(screenOn = true, keyguardLocked = false))

        assertTrue(DeviceLock.hideOverlay(DeviceLock.SCREEN_OFF, LockscreenMode.SHOW))
        assertTrue(DeviceLock.hideOverlay(DeviceLock.LOCKED, LockscreenMode.HIDE))
        assertFalse(DeviceLock.hideOverlay(DeviceLock.LOCKED, LockscreenMode.SHOW))
        assertFalse(DeviceLock.hideOverlay(DeviceLock.UNLOCKED, LockscreenMode.HIDE))
    }

    @Test
    fun `accessibility window only while locked, wanted and available`() {
        fun host(lock: DeviceLock, mode: LockscreenMode = LockscreenMode.SHOW, cover: Boolean = false, a11y: Boolean = true) =
            OverlayHost.choose(lock, mode, cover, a11y)
        assertEquals(OverlayHost.ACCESSIBILITY, host(DeviceLock.LOCKED))
        assertEquals("schon bei Bildschirm aus umhängen", OverlayHost.ACCESSIBILITY, host(DeviceLock.SCREEN_OFF))
        assertEquals("entsperrt ohne Überdecken unter der Statusleiste", OverlayHost.APP, host(DeviceLock.UNLOCKED))
        assertEquals(OverlayHost.APP, host(DeviceLock.LOCKED, mode = LockscreenMode.HIDE))
        assertEquals(OverlayHost.APP, host(DeviceLock.LOCKED, a11y = false))
        // Statusleiste überdecken: auch entsperrt über der Statusleiste – nur mit Bedienungshilfe.
        assertEquals(OverlayHost.ACCESSIBILITY, host(DeviceLock.UNLOCKED, cover = true))
        assertEquals(OverlayHost.APP, host(DeviceLock.UNLOCKED, cover = true, a11y = false))
        assertEquals("Tastatur nur im normalen Overlay", OverlayHost.APP,
            OverlayHost.choose(DeviceLock.UNLOCKED, LockscreenMode.SHOW, coverStatusBar = true, accessibilityConnected = true, needsKeyboard = true))
    }

    @Test
    fun `locked dashboard shows only the timer, no private tabs`() {
        compose.setContent {
            NotchContainer(
                layout = NotchLayout(NotchLayoutMode.NOTCH_TOP, pill = PillGeometry(120, 36, 0, 8)),
                onExpandRequest = {},
                locked = true
            )
        }
        compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, 150f), 300) }
        compose.waitForIdle()
        compose.onNodeWithText("Timer").assertExists()
        listOf("Notizen", "Clip", "Dev", "AI").forEach { private ->
            assertEquals("$private darf gesperrt nicht erscheinen", 0, compose.onAllNodesWithText(private).fetchSemanticsNodes().size)
        }
    }
}
