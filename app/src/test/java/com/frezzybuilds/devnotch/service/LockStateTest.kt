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
        assertEquals(OverlayHost.ACCESSIBILITY, OverlayHost.choose(DeviceLock.LOCKED, LockscreenMode.SHOW, accessibilityConnected = true))
        assertEquals("schon bei Bildschirm aus umhängen", OverlayHost.ACCESSIBILITY, OverlayHost.choose(DeviceLock.SCREEN_OFF, LockscreenMode.SHOW, true))
        assertEquals("entsperrt nie über der Benachrichtigungsleiste", OverlayHost.APP, OverlayHost.choose(DeviceLock.UNLOCKED, LockscreenMode.SHOW, true))
        assertEquals(OverlayHost.APP, OverlayHost.choose(DeviceLock.LOCKED, LockscreenMode.HIDE, true))
        assertEquals(OverlayHost.APP, OverlayHost.choose(DeviceLock.LOCKED, LockscreenMode.SHOW, accessibilityConnected = false))
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
