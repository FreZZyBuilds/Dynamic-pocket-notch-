package com.frezzybuilds.devnotch.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Wetterlage für die animierte Szene. */
enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

data class WeatherNow(
    val temperature: Int,
    val sky: Sky,
    val description: String,
    val isDay: Boolean,
    val place: String?,
    val fetchedAt: Long
)

/**
 * Aktuelles Wetter für die Wetter-Seite: grober Standort (nur mit Erlaubnis) und Open-Meteo
 * (kostenlos, ohne Konto). Es wird nur die gerundete Position übertragen, höchstens alle 30 Minuten.
 */
object WeatherRepo {
    sealed interface State {
        data object NeedsPermission : State
        data object Loading : State
        data class Ready(val weather: WeatherNow) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "notch-weather").apply { isDaemon = true } }
    @Volatile
    private var loading = false

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Auffrischen, wenn älter als 30 Minuten (oder [force]). */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!hasPermission(app)) {
            _state.value = State.NeedsPermission
            return
        }
        val current = (_state.value as? State.Ready)?.weather
        if (!force && current != null && System.currentTimeMillis() - current.fetchedAt < MAX_AGE_MS) return
        if (loading) return
        loading = true
        if (current == null) _state.value = State.Loading
        worker.execute {
            val result = runCatching { load(app) }
            loading = false
            _state.value = result.fold(
                onSuccess = { State.Ready(it) },
                onFailure = { current?.let { c -> State.Ready(c) } ?: State.Failed("Wetter gerade nicht erreichbar") }
            )
        }
    }

    @SuppressLint("MissingPermission") // geprüft in refresh()
    private fun load(context: Context): WeatherNow {
        val location = lastLocation(context) ?: error("Kein Standort")
        // Auf zwei Nachkommastellen (~1 km) gerundet – mehr braucht das Wetter nicht.
        val lat = "%.2f".format(Locale.US, location.latitude)
        val lon = "%.2f".format(Locale.US, location.longitude)
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day")
        val body = (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 8_000
            readTimeout = 8_000
            try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
        }
        val weather = parse(body)
        val place = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.GERMANY).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.locality
        }.getOrNull()
        return weather.copy(place = place, fetchedAt = System.currentTimeMillis())
    }

    /** Open-Meteo-Antwort → Wetter (öffentlich für Tests). */
    fun parse(json: String): WeatherNow {
        val current = Json.parseToJsonElement(json).jsonObject["current"]!!.jsonObject
        val temp = current["temperature_2m"]!!.jsonPrimitive.content.toDouble()
        val code = current["weather_code"]!!.jsonPrimitive.content.toDouble().toInt()
        val day = current["is_day"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() != 0
        val (sky, text) = describe(code)
        return WeatherNow(Math.round(temp).toInt(), sky, text, day, null, 0L)
    }

    /** WMO-Wettercodes → Szene und deutscher Text. */
    fun describe(code: Int): Pair<Sky, String> = when (code) {
        0 -> Sky.CLEAR to "Klar"
        1 -> Sky.PARTLY to "Überwiegend klar"
        2 -> Sky.PARTLY to "Teilweise bewölkt"
        3 -> Sky.CLOUDY to "Bewölkt"
        45, 48 -> Sky.FOG to "Nebel"
        in 51..57 -> Sky.RAIN to "Nieselregen"
        in 61..67, in 80..82 -> Sky.RAIN to "Regen"
        in 71..77, 85, 86 -> Sky.SNOW to "Schnee"
        in 95..99 -> Sky.STORM to "Gewitter"
        else -> Sky.PARTLY to "Wechselhaft"
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null) return known
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        // Kein gespeicherter Standort: einmal grob bestimmen (wartet höchstens 10 s).
        val latch = java.util.concurrent.CountDownLatch(1)
        var fresh: Location? = null
        runCatching {
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { fresh = it; latch.countDown() }
        }.onFailure { latch.countDown() }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return fresh
    }

    /** Nur für Tests und Vorschauen. */
    internal fun setForTest(state: State) {
        _state.value = state
    }

    private const val MAX_AGE_MS = 30 * 60_000L
}
