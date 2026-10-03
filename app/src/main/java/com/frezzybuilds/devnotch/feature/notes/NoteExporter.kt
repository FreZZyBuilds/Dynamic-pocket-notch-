package com.frezzybuilds.devnotch.feature.notes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Teilt eine Notiz als echte .md-Datei (nicht nur als Text): Empfänger wie Google Drive,
 * „Dateien“, Obsidian oder Mail bekommen eine Datei mit passendem Namen. Der Text liegt
 * zusätzlich in EXTRA_TEXT für Apps, die nur Text annehmen (Messenger).
 */
object NoteExporter {
    private const val MIME = "text/markdown"

    fun share(context: Context, note: QuickNote) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Alte Exporte aufräumen, damit der Cache nicht wächst.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, MarkdownFormatter.fileName(note))
        file.writeText(MarkdownFormatter.toMarkdownFile(note))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, note.content)
            putExtra(Intent.EXTRA_SUBJECT, note.displayTitle)
            putExtra(Intent.EXTRA_TITLE, file.name)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Notiz teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** „Speichern unter…“: Systemdialog zur Ablage in Google Drive oder lokalen Ordnern. */
    fun saveAs(context: Context, note: QuickNote) {
        context.startActivity(
            Intent(context, ExportNoteActivity::class.java)
                .putExtra(ExportNoteActivity.EXTRA_NOTE_ID, note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
