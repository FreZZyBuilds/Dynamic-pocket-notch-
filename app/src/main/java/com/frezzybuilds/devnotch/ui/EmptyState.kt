package com.frezzybuilds.devnotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.ui.theme.Brand

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Einladender Leere-Zustand statt Fehlermeldung: Symbol im Markenverlauf, kurzer Nutzen,
 * ein klarer Button, der direkt in die passende Einstellung führt.
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brand.Card)
            .border(1.dp, Brand.Horizontal, CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Brand.Horizontal),
                contentAlignment = Alignment.Center
            ) { Text(icon, color = Color.White, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(text, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        if (action != null && onAction != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text(action, color = Color.Black, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
