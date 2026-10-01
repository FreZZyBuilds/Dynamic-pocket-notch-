package com.frezzybuilds.devnotch.service

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.appContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun boot(action: String = Intent.ACTION_BOOT_COMPLETED) =
        BootReceiver().onReceive(app, Intent(action))

    private fun startedService() = shadowOf(app).nextStartedService

    @Test
    fun `starts the notch after boot when it was enabled`() {
        app.appContainer.notchSettings.notchEnabled = true
        ShadowSettings.setCanDrawOverlays(true)

        boot()

        assertEquals(NotchOverlayService::class.java.name, startedService()?.component?.className)
    }

    @Test
    fun `also restarts after an app update`() {
        app.appContainer.notchSettings.notchEnabled = true
        ShadowSettings.setCanDrawOverlays(true)

        boot(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertNotNull(startedService())
    }

    @Test
    fun `stays off when the user had disabled it`() {
        app.appContainer.notchSettings.notchEnabled = false
        ShadowSettings.setCanDrawOverlays(true)

        boot()

        assertNull(startedService())
    }

    @Test
    fun `stays off when the overlay permission was revoked`() {
        app.appContainer.notchSettings.notchEnabled = true
        ShadowSettings.setCanDrawOverlays(false)

        boot()

        assertNull(startedService())
    }

    @Test
    fun `ignores unrelated broadcasts`() {
        app.appContainer.notchSettings.notchEnabled = true
        ShadowSettings.setCanDrawOverlays(true)

        boot(Intent.ACTION_SCREEN_ON)

        assertNull(startedService())
    }

    @Test
    fun `service posts a discreet foreground notification on devnotch_service`() {
        ShadowSettings.setCanDrawOverlays(false) // Overlay wird nicht aufgebaut, nur Benachrichtigung
        val service = Robolectric.buildService(NotchOverlayService::class.java).create().startCommand(0, 1).get()

        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotchOverlayService.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)
        assertFalse(channel.canShowBadge())
        assertNull(manager.getNotificationChannel("notch_overlay"))

        val notification = shadowOf(service).lastForegroundNotification
        assertEquals(NotchOverlayService.CHANNEL_ID, notification.channelId)
        assertEquals(1, notification.actions.size) // „Beenden“
    }

    @Test
    fun `stop action disables autostart`() {
        app.appContainer.notchSettings.notchEnabled = true
        ShadowSettings.setCanDrawOverlays(true)
        Robolectric.buildService(
            NotchOverlayService::class.java,
            Intent(app, NotchOverlayService::class.java).setAction(NotchOverlayService.ACTION_STOP)
        ).create().startCommand(0, 1)

        assertFalse(app.appContainer.notchSettings.notchEnabled)
    }
}
