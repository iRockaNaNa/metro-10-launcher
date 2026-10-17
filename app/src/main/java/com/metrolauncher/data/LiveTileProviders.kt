package com.metrolauncher.data

import android.content.ContentUris
import android.content.Context
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------------------
// Calendar
// ---------------------------------------------------------------------------

/** One upcoming calendar event. */
data class CalendarEvent(val title: String, val startMillis: Long)

/**
 * The next [max] upcoming events in the coming 7 days, soonest first.
 * Returns an empty list on any failure (caller must hold READ_CALENDAR).
 */
suspend fun queryUpcomingEvents(context: Context, max: Int = 3): List<CalendarEvent> =
    withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath((now + 7L * 24 * 60 * 60 * 1000).toString())
                .build()
            val events = mutableListOf<CalendarEvent>()
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                while (cursor.moveToNext() && events.size < max) {
                    val title = cursor.getString(titleIdx)?.takeIf { it.isNotBlank() }
                        ?: "(no title)"
                    events += CalendarEvent(title, cursor.getLong(beginIdx))
                }
            }
            events
        }.getOrDefault(emptyList())
    }

// ---------------------------------------------------------------------------
// Weather (Open-Meteo, free, no API key)
// ---------------------------------------------------------------------------

/** Mesa, AZ — home fallback when location is unavailable or denied. */
const val FALLBACK_LATITUDE = 33.4152
const val FALLBACK_LONGITUDE = -111.8315
const val FALLBACK_PLACE = "Mesa, AZ"

/** Current conditions plus today's high/low, in Fahrenheit. */
data class WeatherNow(
    val tempF: Double,
    val highF: Double,
    val lowF: Double,
    val description: String,
    val placeLabel: String,
)

/** WMO weather-code to a short human description. */
fun weatherCodeDescription(code: Int): String = when (code) {
    0 -> "clear sky"
    1 -> "mostly clear"
    2 -> "partly cloudy"
    3 -> "overcast"
    45, 48 -> "foggy"
    51, 53, 55 -> "drizzle"
    56, 57 -> "freezing drizzle"
    61, 63 -> "rain"
    65 -> "heavy rain"
    66, 67 -> "freezing rain"
    71, 73, 75, 77 -> "snow"
    80, 81, 82 -> "rain showers"
    85, 86 -> "snow showers"
    95 -> "thunderstorm"
    96, 99 -> "storm, hail"
    else -> "—"
}

/**
 * Fetches current weather, cached in memory for 30 minutes so the tile
 * doesn't hammer the API. Returns null when the fetch fails; the tile then
 * shows a graceful "unavailable" face and keeps the last good reading in
 * the meantime via the cache.
 */
suspend fun fetchWeather(context: Context, useDeviceLocation: Boolean): WeatherNow? =
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        weatherCache?.takeIf { now - weatherFetchedAt < WEATHER_TTL_MS }
            ?.let { return@withContext it }
        runCatching {
            val (lat, lon, label) = if (useDeviceLocation) {
                deviceLocation(context)?.let { (a, b) -> Triple(a, b, "your location") }
                    ?: Triple(FALLBACK_LATITUDE, FALLBACK_LONGITUDE, FALLBACK_PLACE)
            } else {
                Triple(FALLBACK_LATITUDE, FALLBACK_LONGITUDE, FALLBACK_PLACE)
            }
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min" +
                    "&temperature_unit=fahrenheit&timezone=auto"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                WeatherNow(
                    tempF = current.getDouble("temperature_2m"),
                    highF = daily.getJSONArray("temperature_2m_max").getDouble(0),
                    lowF = daily.getJSONArray("temperature_2m_min").getDouble(0),
                    description = weatherCodeDescription(current.getInt("weather_code")),
                    placeLabel = label,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.also {
            weatherCache = it
            weatherFetchedAt = now
        }
    }

private var weatherCache: WeatherNow? = null
private var weatherFetchedAt: Long = 0
private const val WEATHER_TTL_MS = 30L * 60 * 1000

/** Last-known coarse device location; null when unavailable. */
private fun deviceLocation(context: Context): Pair<Double, Double>? = runCatching {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: return null
    location.latitude to location.longitude
}.getOrNull()

// ---------------------------------------------------------------------------
// Photos
// ---------------------------------------------------------------------------

/**
 * Recent photo content URIs, newest first. Returns an empty list on any
 * failure (caller must hold the storage permission).
 */
suspend fun queryRecentPhotos(context: Context, max: Int = 24): List<Uri> =
    withContext(Dispatchers.IO) {
        runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uris = mutableListOf<Uri>()
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext() && uris.size < max) {
                    uris += ContentUris.withAppendedId(collection, cursor.getLong(idIdx))
                }
            }
            uris
        }.getOrDefault(emptyList())
    }

/** The storage permission the Photos tile needs on this OS version. */
fun photosPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
