package com.frezzybuilds.devnotch

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.frezzybuilds.devnotch.service.NotchLayoutMode
import com.frezzybuilds.devnotch.service.NotchOverlayService
import com.frezzybuilds.devnotch.ui.theme.DevNotchTheme

class MainActivity : ComponentActivity() {

    private var overlayGranted by mutableStateOf(false)
    private var listenerGranted by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            DevNotchTheme {
                Scaffold { padding ->
                    SetupScreen(
                        overlayGranted = overlayGranted,
                        listenerGranted = listenerGranted,
                        onRequestOverlay = ::openOverlaySettings,
                        onRequestListener = ::openNotificationListenerSettings,
                        onStart = ::startNotch,
                        onStop = ::stopNotch,
                        initialGitHubToken = appContainer.gitHubTokenStore.token.orEmpty(),
                        onSaveGitHubToken = { appContainer.gitHubTokenStore.token = it },
                        initialDisplayMode = appContainer.notchSettings.displayMode,
                        onDisplayModeChange = { appContainer.notchSettings.displayMode = it },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
        listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openNotificationListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun startNotch() {
        NotchOverlayService.start(this)
    }

    private fun stopNotch() {
        stopService(Intent(this, NotchOverlayService::class.java))
    }
}

@Composable
private fun SetupScreen(
    overlayGranted: Boolean,
    listenerGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestListener: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    initialGitHubToken: String,
    onSaveGitHubToken: (String) -> Unit,
    initialDisplayMode: NotchLayoutMode,
    onDisplayModeChange: (NotchLayoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("DevNotch Setup")
        OutlinedButton(onClick = onRequestOverlay) {
            Text(if (overlayGranted) "Overlay-Berechtigung ✓" else "Overlay-Berechtigung erteilen")
        }
        OutlinedButton(onClick = onRequestListener) {
            Text(if (listenerGranted) "Benachrichtigungszugriff ✓" else "Benachrichtigungszugriff erteilen")
        }
        Button(onClick = onStart, enabled = overlayGranted) {
            Text("Notch starten")
        }
        OutlinedButton(onClick = onStop) {
            Text("Notch stoppen")
        }
        Text("Einstellungen")
        DisplayModeToggle(initialDisplayMode, onDisplayModeChange)
        Text("GitHub")
        var token by remember { mutableStateOf(initialGitHubToken) }
        var savedToken by remember { mutableStateOf(initialGitHubToken) }
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        OutlinedButton(onClick = {
            onSaveGitHubToken(token)
            savedToken = token
        }) {
            Text(if (token == savedToken && token.isNotBlank()) "Token gespeichert ✓" else "Token speichern")
        }
    }
}

/** Display Mode: [Punchhole Center | Floating Edge Bar]. Wirkt sofort auf die laufende Notch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayModeToggle(
    initialMode: NotchLayoutMode,
    onModeChange: (NotchLayoutMode) -> Unit
) {
    var selected by remember { mutableStateOf(initialMode) }
    Text("Display Mode")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        NotchLayoutMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = {
                    selected = mode
                    onModeChange(mode)
                },
                shape = SegmentedButtonDefaults.itemShape(index, NotchLayoutMode.entries.size)
            ) {
                Text(mode.label, maxLines = 1)
            }
        }
    }
}
