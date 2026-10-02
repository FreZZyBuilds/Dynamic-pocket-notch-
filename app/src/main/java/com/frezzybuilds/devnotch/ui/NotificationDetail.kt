package com.frezzybuilds.devnotch.ui

import android.app.PendingIntent
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frezzybuilds.devnotch.notify.NotchNotification
import com.frezzybuilds.devnotch.ui.theme.Brand
import kotlinx.coroutines.delay

/** Ergebnis eines Antwortversuchs, für die Anzeige unter dem Feld. */
private enum class ReplyState { IDLE, SENT, FAILED }

/**
 * Heruntergezogene Benachrichtigung: voller Text zum Lesen, darunter Direktantwort (falls die App
 * eine anbietet) und die übrigen Aktionen. Gesperrt wird nicht geantwortet.
 */
@Composable
fun ColumnScope.NotificationDetail(
    notification: NotchNotification,
    locked: Boolean,
    onSend: (PendingIntent?) -> Unit,
    onReplied: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val reply = notification.reply?.takeUnless { locked }
    var draft by remember(notification.key) { mutableStateOf("") }
    var state by remember(notification.key) { mutableStateOf(ReplyState.IDLE) }
    LaunchedEffect(state) {
        if (state == ReplyState.SENT) {
            delay(900)
            onReplied()
        }
    }

    // Kopf: Symbol, App und Zeit, zurück zu den Tabs.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = notification.icon
        if (icon != null) {
            val image = remember(icon) { icon.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Brand.Horizontal), contentAlignment = Alignment.Center) {
                Text(notification.appLabel.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(notification.title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = if (notification.postTime > 0) " · " + DateUtils.getRelativeTimeSpanString(notification.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else ""
            Text(notification.appLabel + time, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        ActionChip("Zurück", highlighted = false, onClick = onBack)
    }

    // Voller Text, scrollbar – auch lange Nachrichten bleiben lesbar.
    Box(
        Modifier
            .weight(1f, fill = true)
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        Text(
            notification.text ?: "",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }

    if (reply != null) {
        // Nur mit Antwortfeld darf das Fenster Fokus (und damit die Tastatur) bekommen.
        RequestOverlayFocus()
        val canSend = draft.isNotBlank() && state != ReplyState.SENT
        fun send() {
            if (!canSend) return
            state = if (reply.send(context, draft.trim())) ReplyState.SENT else ReplyState.FAILED
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Brand.Horizontal, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (draft.isEmpty()) {
                    Text("Antworten an ${notification.title} …", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it; if (state == ReplyState.FAILED) state = ReplyState.IDLE },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    cursorBrush = SolidColor(Brand.Cyan),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Antwort" }
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brand.Horizontal)
                    .alpha(if (canSend) 1f else 0.4f)
                    .clickable(enabled = canSend, role = Role.Button, onClickLabel = "Senden") { send() },
                contentAlignment = Alignment.Center
            ) { Text("➤", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
        when (state) {
            ReplyState.SENT -> Text("Gesendet ✓", color = Brand.Charge, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            ReplyState.FAILED -> Text("Senden fehlgeschlagen – öffne die App.", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            ReplyState.IDLE -> Unit
        }
    } else if (locked && notification.reply != null) {
        Text("Zum Antworten entsperren", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium)
    }

    // Übrige Aktionen der App und „Öffnen“.
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!locked) {
            notification.actions
                .filter { !it.needsInput && it.intent != null && it.title.isNotBlank() }
                .take(2)
                .forEach { action -> ActionChip(action.title, highlighted = false) { onSend(action.intent) } }
        }
        ActionChip("Öffnen", highlighted = true) { onSend(notification.contentIntent) }
    }
}
