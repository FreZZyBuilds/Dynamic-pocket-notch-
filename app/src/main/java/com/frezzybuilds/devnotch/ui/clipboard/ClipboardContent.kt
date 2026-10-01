package com.frezzybuilds.devnotch.ui.clipboard

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.appContainer
import com.frezzybuilds.devnotch.data.clipboard.ClipboardItem
import com.frezzybuilds.devnotch.feature.billing.Paywall
import com.frezzybuilds.devnotch.feature.billing.ProFeature
import com.frezzybuilds.devnotch.feature.billing.ProPlan
import kotlinx.coroutines.delay

private val ItemBackground = Color(0xFF1E1E1E)
private val CopiedBackground = Color(0xFF123524)

/**
 * Kopierte Texte (Free: die letzten 5, Pro: unbegrenzt); Tippen legt einen Eintrag zurück in
 * die Zwischenablage. [onLeave] klappt die Notch ein, wenn die Paywall geöffnet wird.
 */
@Composable
fun ClipboardContent(onLeave: () -> Unit = {}) {
    val context = LocalContext.current
    val isPro by context.appContainer.proAccess.isPro.collectAsStateWithLifecycle()
    val view = LocalView.current
    val viewModel = viewModel { ClipboardViewModel(context.appContainer.clipboardRepository) }
    val history by viewModel.history.collectAsStateWithLifecycle()

    // Kurzes „✓ Kopiert“ direkt am Eintrag, verschwindet nach 1,5 s wieder.
    var copiedText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedText) {
        if (copiedText != null) {
            delay(1_500)
            copiedText = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isPro) "${history.size} Einträge · Tippen zum Kopieren"
                else "Letzte ${history.size}/${ProPlan.FREE_CLIPBOARD_ENTRIES} · Tippen zum Kopieren",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            if (history.isNotEmpty()) {
                Text(
                    "Leeren",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clickable(onClick = viewModel::clear)
                        .padding(4.dp)
                )
            }
        }
        if (!isPro) {
            Text(
                "✦ Unbegrenzter Verlauf mit Pro ›",
                color = Color(0xFFB388FF),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clickable {
                        Paywall.open(context, ProFeature.UNLIMITED_CLIPBOARD)
                        onLeave()
                    }
                    .padding(vertical = 2.dp)
            )
        }
        if (history.isEmpty()) {
            Text(
                "Noch nichts gespeichert. Kopierte Texte erscheinen hier.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Key = Text: Beim Zurückkopieren bekommt der Eintrag eine neue id (REPLACE),
            // der Text bleibt gleich – so animiert/merkt sich die Liste das richtige Element.
            items(history, key = { it.text }) { item ->
                ClipboardRow(
                    item = item,
                    copied = item.text == copiedText,
                    onClick = {
                        viewModel.copy(item)
                        copiedText = item.text
                        confirmCopy(context, view)
                    }
                )
            }
        }
    }
}

@Composable
private fun ClipboardRow(item: ClipboardItem, copied: Boolean, onClick: () -> Unit) {
    Text(
        text = if (copied) "✓ Kopiert" else item.text,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (copied) CopiedBackground else ItemBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/**
 * Haptik immer; Toast nur bis Android 12L. Ab Android 13 blendet das System beim Kopieren
 * selbst eine Bestätigung ein – ein eigener Toast wäre doppelt.
 */
private fun confirmCopy(context: Context, view: View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
    )
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
    }
}
