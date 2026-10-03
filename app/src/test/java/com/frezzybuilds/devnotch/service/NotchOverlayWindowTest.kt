package com.frezzybuilds.devnotch.service

import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** Das echte Overlay-Fenster des Service: Flags, die über Ruckeln und Eingaben entscheiden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotchOverlayWindowTest {

    @Test
    fun `overlay window is hardware accelerated, not focusable and wraps its content when collapsed`() {
        ShadowSettings.setCanDrawOverlays(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(Settings.canDrawOverlays(context))

        val controller = Robolectric.buildService(NotchOverlayService::class.java).create().startCommand(0, 1)
        val windowManager = controller.get().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val views = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views
        assertEquals(1, views.size)
        val params = views.single().layoutParams as WindowManager.LayoutParams

        assertTrue(
            "Overlay muss hardwarebeschleunigt sein (GPU-Rendering der Animationen)",
            params.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0
        )
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height)

        controller.destroy()
    }
}
