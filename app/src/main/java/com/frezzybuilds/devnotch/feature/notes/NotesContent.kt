package com.frezzybuilds.devnotch.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frezzybuilds.devnotch.appContainer
import kotlinx.coroutines.delay

private val EditorBackground = Color(0xFF1E1E1E)
private val ChipBackground = Color(0xFF1A1A1A)
private val Accent = Color(0xFF03DAC6)
private val MonoStyle = TextStyle(color = Color(0xFFD4D4D4), fontFamily = FontFamily.Monospace, fontSize = 12.sp)

/**
 * Markdown-Notizen im Dev-Stil: Monospace-Editor, Schnellformatierung, mehrere Notizen mit
 * Anpinnen und Export als .md-Datei.
 *
 * @param onLeaveForExternalApp wird vor Teilen/Speichern aufgerufen – die Notch klappt ein,
 * damit sie den Teilen-Dialog nicht verdeckt.
 */
@Composable
fun NotesContent(
    modifier: Modifier = Modifier,
    onLeaveForExternalApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = viewModel { NotesViewModel(context.appContainer.notesRepository) }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val note = selected ?: return

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NoteChips(notes, note.id, onSelect = viewModel::select, onCreate = viewModel::create)

        // Editor-Zustand pro Notiz: Wechsel der Auswahl startet frisch mit deren Inhalt.
        key(note.id) {
            NoteEditor(
                note = note,
                onSave = { title, content -> viewModel.save(note.id, title, content) },
                onTogglePin = { viewModel.togglePin(note.id) },
                onShare = { current ->
                    onLeaveForExternalApp()
                    NoteExporter.share(context, current)
                },
                onSaveAs = { current ->
                    onLeaveForExternalApp()
                    NoteExporter.saveAs(context, current)
                },
                onDelete = { viewModel.delete(note.id) }
            )
        }
    }
}

@Composable
private fun NoteChips(notes: List<QuickNote>, selectedId: Long, onSelect: (Long) -> Unit, onCreate: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(notes, key = { it.id }) { note ->
            val active = note.id == selectedId
            Text(
                (if (note.isPinned) "★ " else "") + note.displayTitle,
                color = if (active) Color.Black else Color.Gray,
                style = MonoStyle.copy(fontSize = 11.sp, color = Color.Unspecified),
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Color.White else ChipBackground)
                    .clickable { onSelect(note.id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        item {
            Text(
                "+ Neu",
                color = Accent,
                style = MonoStyle.copy(fontSize = 11.sp, color = Color.Unspecified),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ChipBackground)
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun NoteEditor(
    note: QuickNote,
    onSave: (title: String, content: String) -> Unit,
    onTogglePin: () -> Unit,
    onShare: (QuickNote) -> Unit,
    onSaveAs: (QuickNote) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(TextFieldValue(note.title)) }
    var content by remember { mutableStateOf(TextFieldValue(note.content)) }
    val currentNote = note.copy(title = title.text, content = content.text)

    // Gebündelt speichern; beim Verlassen (Notiz-/Tabwechsel) sofort, damit nichts verloren geht.
    LaunchedEffect(title.text, content.text) {
        delay(400)
        onSave(title.text, content.text)
    }
    val latest by rememberUpdatedState(title.text to content.text)
    DisposableEffect(Unit) {
        onDispose { onSave(latest.first, latest.second) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Toolbar(
            isPinned = note.isPinned,
            onFormat = { action ->
                val result = MarkdownFormatter.apply(
                    EditState(content.text, content.selection.start, content.selection.end),
                    action
                )
                content = TextFieldValue(result.text, TextRange(result.selectionStart, result.selectionEnd))
            },
            onTogglePin = onTogglePin,
            onShare = { onShare(currentNote) },
            onSaveAs = { onSaveAs(currentNote) },
            onDelete = onDelete
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorBackground)
                .padding(8.dp)
        ) {
            Field(
                value = title,
                onValueChange = { title = it },
                placeholder = "# Titel",
                style = MonoStyle.copy(color = Color.White, fontWeight = FontWeight.Bold),
                singleLine = true
            )
            Spacer(Modifier.height(4.dp))
            Field(
                value = content,
                onValueChange = { content = it },
                placeholder = "// Code, Todos oder Notizen hier eintippen…",
                style = MonoStyle,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun Field(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    Box(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            singleLine = singleLine,
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.text.isEmpty()) {
            Text(placeholder, style = style.copy(color = Color(0xFF5A5A5A), fontWeight = FontWeight.Normal))
        }
    }
}

@Composable
private fun Toolbar(
    isPinned: Boolean,
    onFormat: (MarkdownAction) -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onDelete: () -> Unit
) {
    // Löschen in zwei Schritten (kein Popup im Overlay): erst scharf schalten, dann bestätigen.
    var deleteArmed by remember { mutableStateOf(false) }
    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            delay(3_000)
            deleteArmed = false
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        MarkdownAction.entries.forEach { action ->
            ToolButton(action.label, action.description) { onFormat(action) }
        }
        Spacer(Modifier.weight(1f))
        ToolButton(if (isPinned) "★" else "☆", if (isPinned) "Lösen" else "Anpinnen", tint = Color(0xFFFFD54F), onClick = onTogglePin)
        ToolButton("↗", "Als .md teilen", tint = Accent, onClick = onShare)
        ToolButton("⤓", "Speichern unter", tint = Accent, onClick = onSaveAs)
        ToolButton(
            if (deleteArmed) "✓?" else "✕",
            if (deleteArmed) "Löschen bestätigen" else "Löschen",
            tint = if (deleteArmed) Color(0xFFFF5252) else Color.Gray
        ) {
            if (deleteArmed) onDelete() else deleteArmed = true
        }
    }
}

@Composable
private fun ToolButton(label: String, description: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = tint, style = MonoStyle.copy(color = Color.Unspecified, fontWeight = FontWeight.Bold))
    }
}
