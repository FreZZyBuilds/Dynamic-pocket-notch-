package com.frezzybuilds.devnotch.ui.clipboard

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.appContainer

@Composable
fun ClipboardHistoryContent() {
    val context = LocalContext.current
    val viewModel = viewModel { ClipboardViewModel(context.appContainer.clipboardRepository) }
    val history by viewModel.history.collectAsStateWithLifecycle()
    var copiedText by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Letzte ${history.size} Einträge · Tippen zum Kopieren",
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
        if (history.isEmpty()) {
            Text(
                "Noch nichts gespeichert. Kopierte Texte erscheinen hier.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(history, key = { it.id }) { entry ->
                Text(
                    text = if (entry.text == copiedText) "✓ Kopiert" else entry.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .clickable {
                            viewModel.copy(entry)
                            copiedText = entry.text
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
