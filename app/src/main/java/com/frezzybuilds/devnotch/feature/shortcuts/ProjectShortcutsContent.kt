package com.frezzybuilds.devnotch.feature.shortcuts

import android.app.Application
import com.frezzybuilds.devnotch.ui.RequestOverlayFocus
import com.frezzybuilds.devnotch.feature.billing.Paywall
import com.frezzybuilds.devnotch.feature.billing.ProFeature
import com.frezzybuilds.devnotch.feature.billing.ProLimits
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TileBackground = Color(0xFF242424)
private val Accent = Color(0xFF03DAC6)

/**
 * Projekt-Kacheln im Dev-Dashboard: 3-spaltiges Raster aus Apps und URLs.
 * Tippen startet, langes Drücken schaltet in den Bearbeiten-Modus (verschieben, löschen),
 * „+“ öffnet die Auswahl installierter Apps bzw. die URL-Eingabe direkt im Panel.
 *
 * @param onLaunched Notch einklappen, damit sie die gestartete App nicht verdeckt.
 */
@Composable
fun ProjectShortcutsContent(modifier: Modifier = Modifier, onLaunched: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel = viewModel {
        ShortcutsViewModel(context.applicationContext as Application, context.appContainer.shortcutsRepository)
    }
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val isPro by context.appContainer.proAccess.isPro.collectAsStateWithLifecycle()
    val canAdd = ProLimits.canAddShortcut(isPro, shortcuts.size)

    if (adding) {
        AddShortcutPane(viewModel, onDone = { adding = false }, modifier = modifier)
        return
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bearbeiten: ◀ ▶ verschieben, ✕ löschen", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.weight(1f))
                Text("Fertig", color = Accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { editing = false })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(shortcuts, key = { it.id }) { shortcut ->
                ShortcutTile(
                    shortcut = shortcut,
                    editing = editing,
                    onClick = {
                        ShortcutLauncher.launch(context, shortcut)
                        onLaunched()
                    },
                    onLongClick = { editing = true },
                    onMove = { viewModel.move(shortcut, it) },
                    onDelete = { viewModel.delete(shortcut) }
                )
            }
            item(key = "add") {
                AddTile(locked = !canAdd) {
                    editing = false
                    if (canAdd) {
                        adding = true
                    } else {
                        // Free: max. ProPlan.FREE_SHORTCUTS Kacheln – mehr gibt es mit Pro.
                        Paywall.open(context, ProFeature.UNLIMITED_SHORTCUTS)
                        onLaunched()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    shortcut: ProjectShortcut,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TileBackground)
            .combinedClickable(
                onClick = { if (!editing) onClick() },
                onLongClick = onLongClick,
                onLongClickLabel = "Kacheln bearbeiten"
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShortcutIcon(shortcut)
        Spacer(Modifier.height(4.dp))
        Text(
            shortcut.title,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                EditButton("◀", "Nach vorn") { onMove(-1) }
                EditButton("✕", "Löschen", Color(0xFFFF5252), onDelete)
                EditButton("▶", "Nach hinten") { onMove(1) }
            }
        }
    }
}

@Composable
private fun EditButton(label: String, description: String, tint: Color = Color.White, onClick: () -> Unit) {
    Text(
        label,
        color = tint,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(2.dp)
    )
}

/** App-Icon aus dem PackageManager; URLs bekommen ein Monogramm statt Favicon (kein Tracking). */
@Composable
private fun ShortcutIcon(shortcut: ProjectShortcut) {
    val context = LocalContext.current
    var icon by remember(shortcut.target) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shortcut.target) {
        if (shortcut.iconType == ShortcutType.SYSTEM_APP) {
            icon = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(shortcut.target).toBitmap(96, 96).asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF333333)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = icon
        when {
            bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            shortcut.iconType == ShortcutType.WEB_URL ->
                Text(monogram(shortcut), color = Accent, style = MaterialTheme.typography.labelLarge)
            // App nicht installiert: Tippen führt zur Installationsseite.
            else -> Text("⤓", color = Color.Gray, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun monogram(shortcut: ProjectShortcut): String =
    shortcut.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "🌐"

@Composable
private fun AddTile(locked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClickLabel = if (locked) "Mehr Shortcuts mit Pro" else "Shortcut hinzufügen", onClick = onClick)
            .padding(vertical = 18.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(if (locked) "+ ✦" else "+", color = Accent, style = MaterialTheme.typography.titleLarge)
    }
}

/** Hinzufügen direkt im Panel: installierte App wählen oder eigene URL eintragen. */
@Composable
private fun AddShortcutPane(viewModel: ShortcutsViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    // App-Suche und URL-Feld brauchen die Bildschirmtastatur.
    RequestOverlayFocus()
    var urlMode by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("App", active = !urlMode) { urlMode = false }
            Pill("URL", active = urlMode) { urlMode = true }
            Spacer(Modifier.weight(1f))
            Text("Abbrechen", color = Color.Gray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onDone))
        }
        if (urlMode) {
            UrlForm(onAdd = { title, url -> viewModel.addUrl(title, url); onDone() })
        } else {
            AppPicker(viewModel, onPick = { viewModel.addApp(it); onDone() })
        }
    }
}

@Composable
private fun AppPicker(viewModel: ShortcutsViewModel, onPick: (LaunchableApp) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadApps() }
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    InputField(query, { query = it }, "App suchen …")
    val list = apps
    if (list == null) {
        Text("Apps werden geladen …", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        return
    }
    val filtered = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(filtered, key = { it.packageName }) { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(app) }
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                ShortcutIcon(ProjectShortcut(title = app.label, iconType = ShortcutType.SYSTEM_APP, target = app.packageName, orderIndex = 0))
                Text(
                    app.label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun UrlForm(onAdd: (title: String, url: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val normalized = ShortcutLauncher.normalizeUrl(url)
    InputField(title, { title = it }, "Titel (optional)")
    InputField(url, { url = it }, "github.com/user/repo oder localhost:3000")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (url.isBlank()) "" else normalized ?: "Keine gültige Adresse",
            color = if (normalized == null) Color(0xFFFF5252) else Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Pill("Hinzufügen", active = normalized != null) { if (normalized != null) onAdd(title, url) }
    }
}

@Composable
private fun InputField(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) Text(placeholder, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Pill(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color.Black else Color.Gray,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Color.White else Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
