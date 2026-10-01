package com.frezzybuilds.devnotch.feature.notes

/** Markdown-Schnellaktionen der Notiz-Toolbar. */
enum class MarkdownAction(val label: String, val description: String) {
    INLINE_CODE("`", "Inline-Code"),
    CODE_BLOCK("{ }", "Code-Block"),
    CHECKBOX("☐", "Checkbox"),
    BULLET("•", "Aufzählung"),
    HEADING("H", "Überschrift"),
    BOLD("B", "Fett")
}

/** Text mit Auswahl (Start/Ende als Zeichenindex) – unabhängig von Compose testbar. */
data class EditState(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

object MarkdownFormatter {

    /**
     * Wendet eine Aktion an der Cursorposition an:
     * - Umschließen (Code, Fett, Block): markierten Text einrahmen, ohne Auswahl Cursor in die Mitte.
     * - Zeilenpräfix (Checkbox, Aufzählung, Überschrift): an den Anfang der aktuellen Zeile setzen;
     *   ist das Präfix schon da, wird es entfernt (Umschalten).
     */
    fun apply(state: EditState, action: MarkdownAction): EditState = when (action) {
        MarkdownAction.INLINE_CODE -> wrap(state, "`", "`")
        MarkdownAction.BOLD -> wrap(state, "**", "**")
        MarkdownAction.CODE_BLOCK -> wrap(state, "```\n", "\n```")
        MarkdownAction.CHECKBOX -> toggleLinePrefix(state, "- [ ] ")
        MarkdownAction.BULLET -> toggleLinePrefix(state, "- ")
        MarkdownAction.HEADING -> toggleLinePrefix(state, "# ")
    }

    private fun wrap(state: EditState, before: String, after: String): EditState {
        val start = minOf(state.selectionStart, state.selectionEnd)
        val end = maxOf(state.selectionStart, state.selectionEnd)
        val selected = state.text.substring(start, end)
        val text = state.text.substring(0, start) + before + selected + after + state.text.substring(end)
        return if (selected.isEmpty()) {
            EditState(text, start + before.length)
        } else {
            EditState(text, start + before.length, start + before.length + selected.length)
        }
    }

    private fun toggleLinePrefix(state: EditState, prefix: String): EditState {
        val cursor = minOf(state.selectionStart, state.text.length)
        // Rückwärts ab dem Zeichen vor dem Cursor; ohne Treffer (-1) → Textanfang.
        val lineStart = state.text.lastIndexOf('\n', cursor - 1) + 1
        val line = state.text.substring(lineStart)
        return if (line.startsWith(prefix)) {
            val text = state.text.removeRange(lineStart, lineStart + prefix.length)
            EditState(text, (state.selectionStart - prefix.length).coerceAtLeast(lineStart),
                (state.selectionEnd - prefix.length).coerceAtLeast(lineStart))
        } else {
            val text = state.text.substring(0, lineStart) + prefix + state.text.substring(lineStart)
            EditState(text, state.selectionStart + prefix.length, state.selectionEnd + prefix.length)
        }
    }

    /** Ganze Notiz als Markdown-Datei: Titel als Überschrift, dann der Inhalt. */
    fun toMarkdownFile(note: QuickNote): String = buildString {
        if (note.title.isNotBlank()) append("# ").append(note.title.trim()).append("\n\n")
        append(note.content)
        if (!endsWith("\n")) append("\n")
    }

    /** Dateiname aus dem Titel: nur sichere Zeichen, Endung .md. */
    fun fileName(note: QuickNote): String {
        val slug = note.displayTitle.lowercase()
            .replace(Regex("[^a-z0-9äöüß]+"), "-")
            .trim('-')
            .take(40)
            .ifEmpty { "notiz" }
        return "$slug.md"
    }
}
