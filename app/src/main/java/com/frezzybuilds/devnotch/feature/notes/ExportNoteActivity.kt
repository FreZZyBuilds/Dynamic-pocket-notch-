package com.frezzybuilds.devnotch.feature.notes

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.frezzybuilds.devnotch.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unsichtbare Hilfs-Activity: Der Overlay-Service kann kein Ergebnis vom Datei-Dialog
 * (Storage Access Framework) empfangen – diese Activity öffnet „Speichern unter…“, schreibt
 * die Notiz in die gewählte Datei (lokal, SD-Karte, Google Drive …) und schließt sich wieder.
 */
class ExportNoteActivity : ComponentActivity() {

    private var note: QuickNote? = null

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
            if (uri == null) finish() else write(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_NOTE_ID, -1)
        lifecycleScope.launch {
            note = appContainer.notesRepository.get(id)
            val current = note ?: return@launch finish()
            // Nach einer Rotation läuft der Dialog bereits.
            if (savedInstanceState == null) createDocument.launch(MarkdownFormatter.fileName(current))
        }
    }

    private fun write(uri: Uri) {
        val current = note ?: return finish()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(MarkdownFormatter.toMarkdownFile(current).toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                this@ExportNoteActivity,
                if (ok) "Notiz gespeichert" else "Speichern fehlgeschlagen",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}
