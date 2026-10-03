package com.frezzybuilds.devnotch.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormatterTest {

    private fun apply(text: String, start: Int, end: Int = start, action: MarkdownAction) =
        MarkdownFormatter.apply(EditState(text, start, end), action)

    @Test
    fun `inline code wraps the selection and keeps it selected`() {
        val result = apply("run gradle now", 4, 10, MarkdownAction.INLINE_CODE)
        assertEquals("run `gradle` now", result.text)
        assertEquals(EditState("run `gradle` now", 5, 11), result)
    }

    @Test
    fun `inline code without selection puts the cursor between the backticks`() {
        assertEquals(EditState("a``", 2), apply("a", 1, action = MarkdownAction.INLINE_CODE))
    }

    @Test
    fun `code block wraps on separate lines`() {
        assertEquals("```\nls -la\n```", apply("ls -la", 0, 6, MarkdownAction.CODE_BLOCK).text)
    }

    @Test
    fun `checkbox is added to the start of the current line and toggled off again`() {
        val text = "Einkaufen\nTests schreiben"
        val on = apply(text, 13, action = MarkdownAction.CHECKBOX) // Cursor in Zeile 2
        assertEquals("Einkaufen\n- [ ] Tests schreiben", on.text)
        assertEquals(19, on.selectionStart)

        val off = MarkdownFormatter.apply(on, MarkdownAction.CHECKBOX)
        assertEquals(text, off.text)
        assertEquals(13, off.selectionStart)
    }

    @Test
    fun `line prefix works on the first line and right after a line break`() {
        assertEquals("- a", apply("a", 0, action = MarkdownAction.BULLET).text)
        assertEquals("a\n# ", apply("a\n", 2, action = MarkdownAction.HEADING).text)
    }

    @Test
    fun `export file has the title as heading and a safe file name`() {
        val note = QuickNote(title = "Sprint: API & Tests!", content = "- [ ] Login", updatedAt = 0)
        assertEquals("# Sprint: API & Tests!\n\n- [ ] Login\n", MarkdownFormatter.toMarkdownFile(note))
        assertEquals("sprint-api-tests.md", MarkdownFormatter.fileName(note))
        // Ohne Titel: erste Inhaltszeile, sonst „Ohne Titel“.
        assertEquals("todo-liste.md", MarkdownFormatter.fileName(QuickNote(title = "", content = "\nTodo-Liste\n- a", updatedAt = 0)))
        assertEquals("ohne-titel.md", MarkdownFormatter.fileName(QuickNote(title = "", content = "", updatedAt = 0)))
    }
}
