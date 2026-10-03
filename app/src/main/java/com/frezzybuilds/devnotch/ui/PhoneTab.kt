package com.frezzybuilds.devnotch.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.frezzybuilds.devnotch.ui.theme.Brand

/** Wahltasten mit Buchstaben wie auf dem iPhone. */
private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to ""
)

/** Nur Zeichen, die eine Telefonnummer haben darf (eingefügter Text wird bereinigt). */
fun cleanPhoneNumber(raw: String): String = raw.filter { it.isDigit() || it in "+*#" }.let { n ->
    // „+“ nur am Anfang.
    if (n.isEmpty()) n else n.first() + n.drop(1).replace("+", "")
}

/**
 * Telefon in der Notch: Tastenfeld links, Nummer, Löschen, Einfügen und „Anrufen“ rechts.
 * Mit erlaubtem Direktwählen ruft DevNotch sofort an, sonst öffnet die Telefon-App mit der Nummer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneTabContent(onLeave: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var number by rememberSaveable { mutableStateOf("") }
    val tones = if (LocalInspectionMode.current) null else remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 55) }.getOrNull()
    }
    DisposableEffect(tones) { onDispose { tones?.release() } }

    fun press(key: String) {
        if (number.length >= 20) return
        number += key
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tones?.startTone(dtmfTone(key), 120)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Tastenfeld 3 × 4.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KEYS.chunked(3).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (digit, letters) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .combinedClickable(
                                    role = Role.Button,
                                    onLongClick = if (digit == "0") ({ press("+") }) else null,
                                    onClick = { press(digit) }
                                )
                                .semantics { contentDescription = digit },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(digit, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                                if (letters.isNotEmpty()) {
                                    Text(letters, color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp, lineHeight = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Nummer und Aktionen.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Text(
                number.ifEmpty { "Nummer" },
                color = if (number.isEmpty()) Color.White.copy(alpha = 0.35f) else Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SmallKey("⌫", "Löschen", Modifier.weight(1f), onLongClick = { number = "" }) { number = number.dropLast(1) }
                SmallKey("📋", "Einfügen", Modifier.weight(1f)) {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text != null) number = cleanPhoneNumber(text).take(20)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(if (number.isEmpty()) Brand.Charge.copy(alpha = 0.35f) else Brand.Charge)
                    .combinedClickable(role = Role.Button, enabled = number.isNotEmpty(), onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        call(context, number)
                        number = ""
                        onLeave()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Text("📞  Anrufen", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallKey(symbol: String, label: String, modifier: Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .combinedClickable(role = Role.Button, onClickLabel = label, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, color = Color.White, style = MaterialTheme.typography.titleSmall) }
}

private fun dtmfTone(key: String): Int = when (key) {
    "*" -> ToneGenerator.TONE_DTMF_S
    "#" -> ToneGenerator.TONE_DTMF_P
    "+" -> ToneGenerator.TONE_DTMF_0
    else -> ToneGenerator.TONE_DTMF_0 + (key.toIntOrNull() ?: 0)
}

/** Direkt anrufen, wenn erlaubt; sonst Telefon-App mit vorausgefüllter Nummer. */
fun call(context: Context, number: String) {
    val uri = Uri.fromParts("tel", number, null)
    val direct = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val intent = Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
