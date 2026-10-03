package com.frezzybuilds.devnotch.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Leistungs-Modus: misst, wo die Notch ruckelt. Nur solange die Notch zeichnet (Animation,
 * Peek, Aufklappen) laufen Bild-Callbacks mit – im Ruhezustand kostet es nichts. Jedes Bild,
 * das deutlich länger als ein Bildschirm-Takt braucht, wird mit dem aktuellen Zustand
 * („aufgeklappt · Timer“, „Peek · System“ …) gemerkt. Alles bleibt auf dem Gerät, bis der
 * Nutzer den Bericht selbst teilt.
 */
object PerfMonitor {
    /** Ein zu langsames Bild. */
    data class Jank(val atMs: Long, val frameMs: Float, val state: String)

    /** Bilder und Ruckler je Zustand. */
    data class Stat(val frames: Int, val janky: Int, val worstMs: Float)

    private val main = Handler(Looper.getMainLooper())

    /** Aktueller Zustand der Notch (vom NotchContainer gesetzt). */
    @Volatile
    var state: String = "Pille"

    private val _janks = MutableStateFlow<List<Jank>>(emptyList())
    val janks: StateFlow<List<Jank>> = _janks.asStateFlow()

    private val stats = LinkedHashMap<String, Stat>()

    @Synchronized
    fun stats(): Map<String, Stat> = LinkedHashMap(stats)

    private var view: View? = null
    private var periodNs = 16_666_667L
    private var lastFrameNs = 0L
    private var lastDrawMs = 0L
    private var running = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNs != 0L) record(frameTimeNanos - lastFrameNs)
            lastFrameNs = frameTimeNanos
            // Nur weiter messen, solange zuletzt gezeichnet wurde – sonst schläft die Messung.
            if (SystemClock.uptimeMillis() - lastDrawMs < IDLE_AFTER_MS) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                running = false
                lastFrameNs = 0L
            }
        }
    }

    private val drawListener = ViewTreeObserver.OnDrawListener {
        lastDrawMs = SystemClock.uptimeMillis()
        if (!running) {
            running = true
            lastFrameNs = 0L
            main.post { Choreographer.getInstance().postFrameCallback(frameCallback) }
        }
    }

    /** An das Overlay hängen (Main-Thread). Erneutes Anhängen ersetzt das alte. */
    fun attach(target: View) {
        detach()
        view = target
        @Suppress("DEPRECATION")
        val refresh = target.display?.refreshRate?.takeIf { it > 1f } ?: 60f
        periodNs = (1_000_000_000L / refresh).toLong()
        target.viewTreeObserver.addOnDrawListener(drawListener)
    }

    fun detach() {
        view?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnDrawListener(drawListener)
        view = null
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        running = false
        lastFrameNs = 0L
    }

    @Synchronized
    internal fun record(deltaNs: Long) {
        // Lücken über 500 ms sind Pausen, keine Ruckler.
        if (deltaNs <= 0 || deltaNs > 500_000_000L) return
        val key = state
        val janky = deltaNs > periodNs * 3 / 2
        val ms = deltaNs / 1_000_000f
        val old = stats[key] ?: Stat(0, 0, 0f)
        stats[key] = Stat(old.frames + 1, old.janky + if (janky) 1 else 0, if (janky) maxOf(old.worstMs, ms) else old.worstMs)
        if (janky) _janks.value = (_janks.value + Jank(System.currentTimeMillis(), ms, key)).takeLast(MAX_JANKS)
    }

    @Synchronized
    fun reset() {
        stats.clear()
        _janks.value = emptyList()
    }

    /** Kurzfassung für Bericht und Einstellungen. */
    fun summary(): String {
        val s = stats()
        if (s.isEmpty()) return "Noch keine Messung – Leistungs-Modus an und die Notch benutzen."
        return s.entries.sortedByDescending { it.value.janky }.joinToString("\n") { (k, v) ->
            val pct = if (v.frames > 0) 100f * v.janky / v.frames else 0f
            "%s: %d Bilder, %d ruckelig (%.0f %%), schlimmstes %.0f ms".format(Locale.GERMANY, k, v.frames, v.janky, pct, v.worstMs)
        }
    }

    private const val MAX_JANKS = 200
    private const val IDLE_AFTER_MS = 600L
}

/**
 * Abstürze und „App reagiert nicht“: Absturz-Stacktraces landen in einer kleinen Datei im
 * App-Speicher; ANRs und Abstürze liest Android ab Version 11 selbst mit (ApplicationExitInfo).
 */
object CrashLog {
    private const val FILE = "crash_log.txt"
    private const val MAX_BYTES = 64 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val file = File(app.filesDir, FILE)
                val entry = "=== ${stamp(System.currentTimeMillis())} · Thread ${thread.name} · Zustand ${PerfMonitor.state}\n" +
                    error.stackTraceToString() + "\n"
                val old = if (file.exists()) file.readText() else ""
                file.writeText((old + entry).takeLast(MAX_BYTES))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun crashes(context: Context): String =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    /** Letzte Beendigungen durch Android (ANR, Absturz, Speicher) – ab Android 11. */
    fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "(erst ab Android 11 verfügbar)"
        val am = context.getSystemService(ActivityManager::class.java) ?: return ""
        val infos = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 8) }.getOrDefault(emptyList())
        val relevant = infos.filter {
            it.reason in setOf(ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_LOW_MEMORY)
        }
        if (relevant.isEmpty()) return "Keine ANRs oder Abstürze gemeldet."
        return relevant.joinToString("\n\n") { info ->
            val kind = when (info.reason) {
                ApplicationExitInfo.REASON_ANR -> "ANR (reagiert nicht)"
                ApplicationExitInfo.REASON_CRASH -> "Absturz"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Absturz (nativ)"
                else -> "Wenig Speicher"
            }
            // Bei ANRs liefert Android die Stacks aller Threads – der Anfang (Main-Thread) reicht.
            val trace = if (info.reason == ApplicationExitInfo.REASON_ANR) {
                runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText().take(6_000) } }.getOrNull().orEmpty()
            } else ""
            "${stamp(info.timestamp)} · $kind · ${info.description.orEmpty()}" + if (trace.isNotEmpty()) "\n$trace" else ""
        }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    internal fun stamp(ms: Long): String = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(Date(ms))
}

/** Bericht zum Teilen (nur, wenn der Nutzer es selbst auslöst). */
object DiagnosticsReport {
    fun build(context: Context): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val janks = PerfMonitor.janks.value.takeLast(40).joinToString("\n") {
            "${CrashLog.stamp(it.atMs)}  %.0f ms  ${it.state}".format(Locale.GERMANY, it.frameMs)
        }
        return buildString {
            appendLine("DevNotch $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("— Ruckeln je Zustand —")
            appendLine(PerfMonitor.summary())
            if (janks.isNotEmpty()) {
                appendLine()
                appendLine("— Letzte ruckelige Bilder —")
                appendLine(janks)
            }
            appendLine()
            appendLine("— ANR / Abstürze (Android) —")
            appendLine(CrashLog.exitReasons(context))
            val crashes = CrashLog.crashes(context)
            if (crashes.isNotBlank()) {
                appendLine()
                appendLine("— Absturz-Protokoll —")
                appendLine(crashes.takeLast(20_000))
            }
        }
    }

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "DevNotch Diagnose")
            .putExtra(Intent.EXTRA_TEXT, build(context))
        context.startActivity(Intent.createChooser(send, "Diagnose teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
