package com.frezzybuilds.devnotch

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.frezzybuilds.devnotch.data.settings.NotchSettings
import com.frezzybuilds.devnotch.notify.NotificationStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsCustomizeTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings get() = ApplicationProvider.getApplicationContext<Context>().appContainer.notchSettings

    @Test
    fun `card order and per-app styles survive a round trip`() {
        assertEquals(NotchSettings.DEFAULT_HOME_CARDS, settings.homeCards)
        settings.homeCards = listOf("TIMER", "INBOX")
        assertEquals(listOf("TIMER", "INBOX"), settings.homeCards)
        settings.notifyStyleApps = mapOf("com.whatsapp" to NotificationStyle.GLASS, "org.telegram.messenger" to NotificationStyle.APERTURE)
        assertEquals(NotificationStyle.GLASS, settings.notifyPrefs.styleOverrides["com.whatsapp"])
        assertEquals(NotificationStyle.APERTURE, settings.notifyStyleApps["org.telegram.messenger"])
    }

    @Test
    fun `search hides sections that do not match and opens the others`() {
        val query = mutableStateOf("lautlos")
        compose.setContent {
            CompositionLocalProvider(LocalSettingsQuery provides query.value) {
                androidx.compose.foundation.layout.Column {
                    SettingsSection(id = "t_system", icon = "◎", title = "Systemereignisse", subtitle = null, keywords = "lautlos vibration") {
                        Text("Inhalt System")
                    }
                    SettingsSection(id = "t_beam", icon = "✧", title = "Lichtlauf", subtitle = null) { Text("Inhalt Beam") }
                }
            }
        }
        compose.onNodeWithText("Systemereignisse").assertExists()
        compose.onNodeWithText("Inhalt System").assertExists()
        compose.onNodeWithText("Lichtlauf").assertDoesNotExist()
        query.value = ""
        compose.waitForIdle()
        compose.onNodeWithText("Lichtlauf").assertExists()
    }
}
