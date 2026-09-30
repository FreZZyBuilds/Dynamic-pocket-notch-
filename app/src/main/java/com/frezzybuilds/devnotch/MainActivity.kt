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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.frezzybuilds.devnotch.service.EdgeSide
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
                        onSetMode = { mode, side -> NotchOverlayService.setMode(this, mode, side) },
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
    onSetMode: (NotchLayoutMode, EdgeSide) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
        Text("Layout-Modus")
        OutlinedButton(onClick = { onSetMode(NotchLayoutMode.NOTCH_TOP, EdgeSide.RIGHT) }, enabled = overlayGranted) {
            Text("Oben (Kamera)")
        }
        OutlinedButton(onClick = { onSetMode(NotchLayoutMode.EDGE_SIDE, EdgeSide.LEFT) }, enabled = overlayGranted) {
            Text("Rand links")
        }
        OutlinedButton(onClick = { onSetMode(NotchLayoutMode.EDGE_SIDE, EdgeSide.RIGHT) }, enabled = overlayGranted) {
            Text("Rand rechts")
        }
    }
}
