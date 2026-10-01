package com.frezzybuilds.devnotch.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.appContainer
import kotlinx.coroutines.delay

/** Notizzettel im Drawer: tippen und vergessen – wird automatisch gespeichert. */
@Composable
fun QuickNotesContent(modifier: Modifier = Modifier) {
    val store = LocalContext.current.appContainer.quickNotesStore
    var text by remember { mutableStateOf(store.text) }
    // Gebündelt speichern statt bei jedem Tastendruck.
    LaunchedEffect(text) {
        delay(400)
        store.text = text
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
                .padding(10.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxSize()
            )
            if (text.isEmpty()) {
                Text("Notiz schreiben …", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("${text.length} Zeichen · gespeichert", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            if (text.isNotEmpty()) {
                Text(
                    "Leeren",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { text = "" }
                )
            }
        }
    }
}
