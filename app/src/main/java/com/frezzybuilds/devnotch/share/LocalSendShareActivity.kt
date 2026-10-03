package com.frezzybuilds.devnotch.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.share.localsend.LocalSend
import com.frezzybuilds.devnotch.share.localsend.Peer
import com.frezzybuilds.devnotch.ui.glass.AuroraBackground
import com.frezzybuilds.devnotch.ui.glass.Glass
import com.frezzybuilds.devnotch.ui.glass.GlassButton
import com.frezzybuilds.devnotch.ui.glass.GlassCard
import com.frezzybuilds.devnotch.ui.glass.gradientText
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlinx.coroutines.delay

/**
 * „Teilen“ → LocalSend: zeigt Geräte im selben WLAN (iPhone, Mac, Windows, Android mit LocalSend
 * oder DevNotch) und sendet dorthin. Fortschritt und Ergebnis erscheinen anschließend in der Notch.
 */
class LocalSendShareActivity : ComponentActivity() {

    private var uris: List<Uri> = emptyList()

    private val picker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { picked ->
        if (picked.isEmpty()) finish() else uris = picked
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        LocalSend.acquire(this, USER)
        uris = sharedUris(intent)
        if (intent.action == ACTION_PICK_FILES && savedInstanceState == null) picker.launch("*/*")
        else if (uris.isEmpty()) finish()

        setContent {
            MaterialTheme(colorScheme = Glass.AppScheme) {
                AuroraBackground {
                    PeerPicker(
                        onPick = { peer ->
                            if (uris.isNotEmpty()) {
                                LocalSend.acquire(this@LocalSendShareActivity, "send")
                                LocalSend.send(this@LocalSendShareActivity, peer, uris)
                            }
                            finish()
                        },
                        onCancel = ::finish
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        LocalSend.release(USER)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }

    companion object {
        const val ACTION_PICK_FILES = "com.frezzybuilds.devnotch.action.LOCALSEND_PICK"
        private const val USER = "picker"
    }
}

@Composable
private fun PeerPicker(onPick: (Peer) -> Unit, onCancel: () -> Unit) {
    val peers by LocalSend.peers.collectAsStateWithLifecycle()
    // Regelmäßig neu ankündigen – Geräte antworten darauf.
    LaunchedEffect(Unit) {
        while (true) {
            LocalSend.refresh()
            delay(3_000)
        }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Senden", style = gradientText(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Black)
        Text(
            "Geräte im selben WLAN mit LocalSend (iPhone, iPad, Mac, Windows, Linux, Android) oder DevNotch.",
            color = Glass.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        GlassCard(Modifier.fillMaxWidth().weight(1f)) {
            if (peers.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Brand.Cyan)
                    Spacer(Modifier.size(16.dp))
                    Text("Suche Geräte …", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Öffne LocalSend auf dem anderen Gerät. Beide müssen im selben WLAN sein.",
                        color = Glass.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(peers, key = { it.info.fingerprint }) { peer -> PeerRow(peer) { onPick(peer) } }
                }
            }
        }
        GlassButton("Abbrechen", onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    val desktop = peer.info.deviceType == "desktop"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Brand.Horizontal), contentAlignment = Alignment.Center) {
            Text(if (desktop) "💻" else "📱", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.info.alias, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(peer.info.deviceModel ?: peer.host, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text("Senden", color = Brand.Cyan, style = MaterialTheme.typography.labelLarge)
    }
}
