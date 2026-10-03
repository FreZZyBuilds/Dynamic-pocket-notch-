package com.frezzybuilds.devnotch.feature.billing

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.appContainer

private val ProGradient = Brush.linearGradient(listOf(Color(0xFFE040FB), Color(0xFF7C4DFF), Color(0xFF18FFFF)))

/**
 * Paywall über der MainActivity. [highlight] ist das Feature, das der Nutzer gerade
 * freischalten wollte – es steht oben und ist hervorgehoben. Schließt sich nach erfolgreichem
 * Kauf/Wiederherstellen selbst.
 */
@Composable
fun PaywallDialog(highlight: ProFeature?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel = viewModel { PaywallViewModel(container.billing, container.proAccess) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.unlocked) { if (state.unlocked) onDismiss() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PaywallContent(
            state = state,
            highlight = highlight,
            onSelect = viewModel::select,
            onPurchase = { (context as? Activity)?.let(viewModel::purchase) },
            onRestore = viewModel::restore,
            onDismiss = onDismiss,
            debugUnlock = if (container.proAccess.canDebugUnlock) {
                { container.proAccess.setDebugUnlock(true) }
            } else {
                null
            }
        )
    }
}

/** Zustandslose Paywall-Oberfläche (für Vorschau/Tests ohne RevenueCat). */
@Composable
fun PaywallContent(
    state: PaywallState,
    highlight: ProFeature?,
    onSelect: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    debugUnlock: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF121212),
        modifier = Modifier.padding(20.dp).fillMaxWidth()
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(ProGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("✦", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("DevNotch Pro", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        highlight?.let { "„${it.title}“ ist ein Pro-Feature" } ?: "Mehr aus deiner Notch herausholen",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = onDismiss) { Text("✕", color = Color.Gray) }
            }

            // Gewünschtes Feature zuerst, dann die übrigen.
            val features = listOfNotNull(highlight) + ProFeature.entries.filter { it != highlight }
            features.forEach { feature -> FeatureRow(feature, highlighted = feature == highlight) }

            Text(
                "Kostenlos bleiben: Notch & Edge-Player, Mediensteuerung, Focus Timer, Notizen.",
                color = Color(0xFF8A8A8A),
                style = MaterialTheme.typography.labelSmall
            )

            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                }
                state.packages.isNotEmpty() -> state.packages.forEach { pkg ->
                    PackageOption(pkg, selected = pkg.id == state.selectedId) { onSelect(pkg.id) }
                }
            }

            state.message?.let { Text(it, color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = onPurchase,
                enabled = state.selected != null && !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    when {
                        state.busy -> "Einen Moment …"
                        state.selected != null -> "Pro freischalten – ${state.selected!!.price}"
                        else -> "Pro freischalten"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRestore, enabled = !state.busy) { Text("Käufe wiederherstellen", color = Color.Gray) }
                TextButton(onClick = onDismiss) { Text("Später", color = Color.Gray) }
            }
            if (debugUnlock != null) {
                OutlinedButton(
                    onClick = debugUnlock,
                    border = BorderStroke(1.dp, Color(0xFF444444)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Testweise freischalten (nur Debug)", color = Color.Gray) }
            }
            Text(
                "Abos verlängern sich automatisch und sind jederzeit im Play Store kündbar.",
                color = Color(0xFF6A6A6A),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun FeatureRow(feature: ProFeature, highlighted: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.border(1.dp, ProGradient, RoundedCornerShape(12.dp)).padding(10.dp)
                else Modifier.padding(horizontal = 10.dp)
            ),
        verticalAlignment = Alignment.Top
    ) {
        Text("✓", color = Color(0xFF18FFFF), fontWeight = FontWeight.Bold)
        Column(Modifier.padding(start = 10.dp)) {
            Text(feature.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(feature.description, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PackageOption(pkg: PaywallPackage, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0xFF333333)),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(pkg.title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            pkg.note?.let { Text(it, color = Color(0xFF18FFFF), style = MaterialTheme.typography.labelSmall) }
        }
        Text(pkg.price, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Platzhalter für gesperrte Pro-Inhalte in der Notch: kurzer Hinweis + „Freischalten“. */
@Composable
fun LockedFeatureCard(feature: ProFeature, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, ProGradient, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("✦ ${feature.title} · Pro", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(feature.description, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "Freischalten ›",
            color = Color.Black,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .background(Color.White, CircleShape)
                .clickable(onClick = onUnlock)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}
