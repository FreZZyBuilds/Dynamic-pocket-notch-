package com.frezzybuilds.devnotch

import android.content.Context
import android.content.Intent

/** Springt aus der Notch direkt zur passenden Karte in den Einstellungen. */
object Setup {
    const val EXTRA_SECTION = "com.frezzybuilds.devnotch.extra.SETUP_SECTION"

    enum class Section { GITHUB, AI }

    fun open(context: Context, section: Section) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SECTION, section.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    fun sectionFrom(intent: Intent?): Section? =
        intent?.getStringExtra(EXTRA_SECTION)?.let { name -> Section.entries.firstOrNull { it.name == name } }
}
