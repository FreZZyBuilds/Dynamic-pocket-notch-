package com.frezzybuilds.devnotch.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.peek.PeekCenter
import com.frezzybuilds.devnotch.share.ContactCard
import com.frezzybuilds.devnotch.share.NameDropSession
import com.frezzybuilds.devnotch.ui.theme.Brand
import com.frezzybuilds.devnotch.ui.glass.gradientText
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** QR-Code (schwarz auf weiß) – rein lokal erzeugt, z. B. die vCard für die iPhone-Kamera. */
fun qrBitmap(text: String, sizePx: Int = 360): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
    val pixels = IntArray(sizePx * sizePx) { i -> if (matrix[i % sizePx, i / sizePx]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

/**
 * NameDrop in der Insel: Wellen wie beim iPhone, solange das Handy als NFC-Kontaktkarte bereitsteht.
 * „QR“ schaltet auf einen QR-Code für iPhones (Kamera → „Kontakt hinzufügen“).
 */
@Composable
fun NameDropContent(pillHeight: Dp) {
    val state by NameDropSession.state.collectAsStateWithLifecycle()
    val card = when (val s = state) {
        is NameDropSession.State.Active -> s.card
        is NameDropSession.State.Sent -> s.card
        NameDropSession.State.Idle -> null
    }
    val context = LocalContext.current
    val nfc = remember { NameDropSession.nfcEnabled(context) }
    var showQr by remember { mutableStateOf(nfc != true) }
    // Insel zu → Sitzung beenden (Handy ist dann nicht mehr als Tag lesbar).
    DisposableEffect(Unit) { onDispose { if (NameDropSession.state.value is NameDropSession.State.Active) NameDropSession.stop() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(pillHeight))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                if (showQr && card != null) {
                    val qr = remember(card) { qrBitmap(card.toVCard()).asImageBitmap() }
                    Image(qr, contentDescription = "QR-Code mit deiner Kontaktkarte", contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)))
                } else {
                    NameDropRipples(sent = state is NameDropSession.State.Sent, initials = card?.name.initials())
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("NameDrop", style = gradientText(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(card?.name ?: "", color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    when {
                        state is NameDropSession.State.Sent -> "Gesendet ✓"
                        showQr -> "iPhone-Kamera auf den Code richten"
                        nfc == false -> "NFC ist aus – nur QR möglich"
                        else -> "Halte dein Handy oben an ein anderes Android-Handy"
                    },
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (nfc == true) ActionChip(if (showQr) "NFC" else "QR", highlighted = false) { showQr = !showQr }
                    ActionChip("Fertig", highlighted = true) { PeekCenter.current.value?.let(PeekCenter::dismiss) }
                }
            }
        }
    }
}

private fun String?.initials(): String =
    this?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.take(1).uppercase() }.orEmpty()

/** Drei Wellen laufen vom Kontaktkreis nach außen (Zeichenphase); grün, sobald gesendet. */
@Composable
private fun NameDropRipples(sent: Boolean, initials: String) {
    val progress: State<Float> = if (LocalInspectionMode.current) {
        remember { mutableFloatStateOf(0.35f) }
    } else {
        rememberInfiniteTransition(label = "namedrop").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart), label = "ripple"
        )
    }
    val tint = if (sent) Color(0xFF30D158) else Brand.Cyan
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(104.dp)) {
            val maxR = size.minDimension / 2
            repeat(3) { i ->
                val t = (progress.value + i / 3f) % 1f
                drawCircle(tint.copy(alpha = (1f - t) * 0.6f), radius = maxR * (0.42f + 0.58f * t), center = Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(if (sent) listOf(Color(0xFF30D158), Color(0xFF00C853)) else Brand.Colors)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (sent) "✓" else initials.ifEmpty { "👤" }, color = Color.Black, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Bezahlt: Karte mit grünem Haken, Händler und Betrag – wie die Apple-Pay-Bestätigung. */
@Composable
fun PaymentContent(merchant: String, amount: String, pillHeight: Dp) {
    val check: State<Float> = if (LocalInspectionMode.current) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "pay").animateFloat(0.85f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "payPulse")
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(pillHeight))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Kleine Karte mit Haken.
            Box(
                Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(20.dp).graphicsLayer { scaleX = check.value; scaleY = check.value }.clip(CircleShape).background(Color(0xFF30D158)),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bezahlt", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(merchant, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(amount, color = Color(0xFF30D158), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Vorschau/Tests: Kontaktkarte als Sitzung anzeigen, ohne NFC. */
internal fun previewCard() = ContactCard("Lena Sommer", "+49 151 2345678", "lena@example.com")
