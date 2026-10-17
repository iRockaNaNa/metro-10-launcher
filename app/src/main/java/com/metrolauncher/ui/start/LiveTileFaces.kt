package com.metrolauncher.ui.start

import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.metrolauncher.data.CalendarEvent
import com.metrolauncher.data.PinnedTile
import com.metrolauncher.data.TileSize
import com.metrolauncher.data.WeatherNow
import com.metrolauncher.data.fetchWeather
import com.metrolauncher.data.queryRecentPhotos
import com.metrolauncher.data.queryUpcomingEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Permission helper
// ---------------------------------------------------------------------------

/** Whether a runtime permission was granted, denied, or never asked for. */
enum class TilePermission { GRANTED, UNKNOWN, DENIED }

/**
 * Tracks one runtime permission and exposes a [request] that pops the
 * system dialog. The tile face decides what to show per status; tapping
 * the tile calls [request] when access is still missing.
 */
@Composable
fun rememberTilePermission(permission: String): Pair<TilePermission, () -> Unit> {
    val context = LocalContext.current
    var status by remember(permission) {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
            ) TilePermission.GRANTED else TilePermission.UNKNOWN
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            status = if (granted) TilePermission.GRANTED else TilePermission.DENIED
        }
    return status to { launcher.launch(permission) }
}

// ---------------------------------------------------------------------------
// Data state holders
// ---------------------------------------------------------------------------

/** Upcoming events, or null while the permission is missing. Refreshes every 15 minutes. */
@Composable
fun rememberCalendarEvents(hasPermission: Boolean): List<CalendarEvent>? {
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            events = null
        } else {
            while (true) {
                events = queryUpcomingEvents(context)
                delay(15L * 60 * 1000)
            }
        }
    }
    return events
}

/**
 * Current weather plus whether a fetch has been attempted yet (so the face
 * can tell "loading" apart from "unavailable"). Refreshes every 30 minutes
 * while the tile is alive; [fetchWeather] also caches, so this is cheap.
 */
@Composable
fun rememberWeatherState(wantLocation: Boolean): Pair<WeatherNow?, Boolean> {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    var attempted by remember { mutableStateOf(false) }
    LaunchedEffect(wantLocation) {
        while (true) {
            weather = fetchWeather(context, wantLocation)
            attempted = true
            delay(30L * 60 * 1000)
        }
    }
    return weather to attempted
}

/** Recent photo URIs, newest first; empty while the permission is missing. */
@Composable
fun rememberRecentPhotos(hasPermission: Boolean): List<Uri> {
    val context = LocalContext.current
    var uris by remember { mutableStateOf(emptyList<Uri>()) }
    LaunchedEffect(hasPermission) {
        uris = if (hasPermission) queryRecentPhotos(context) else emptyList()
    }
    return uris
}

/** Loads a thumbnail bitmap for [uri], or null while loading/on failure. */
@Composable
fun rememberPhotoBitmap(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = uri?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(it, Size(512, 512), null)
                        .asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

// ---------------------------------------------------------------------------
// Shared face pieces (Metro styling matches ClockTileFace)
// ---------------------------------------------------------------------------

/** Face frame with the app name bottom-left — the Windows Phone signature. */
@Composable
private fun FaceScaffold(
    label: String,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp, end = 8.dp),
        )
    }
}

/** Centered two-line placeholder: a small caps title over a hint. */
@Composable
private fun FacePlaceholder(title: String, hint: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Text(
                    text = hint,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // Spacer matching the bottom label so the text stays centered.
        Box(Modifier.height(18.dp))
    }
}

/** "Today 3:30 pm" / "Tomorrow 9:00 am" / "Tue Oct 21 9:00 am". */
private fun formatEventTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val today = LocalDateTime.now(zone).toLocalDate()
    val day = when (start.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> start.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
    return "$day ${start.format(DateTimeFormatter.ofPattern("h:mm a")).lowercase()}"
}

// ---------------------------------------------------------------------------
// Calendar face
// ---------------------------------------------------------------------------

/**
 * Front face: the next appointment, big. Back face: the following two, or
 * a "that's everything" note when there's only one.
 */
@Composable
fun CalendarTileFace(
    tile: PinnedTile,
    events: List<CalendarEvent>?,
    hasPermission: Boolean,
    back: Boolean,
) {
    FaceScaffold(label = tile.label) {
        when {
            !hasPermission -> FacePlaceholder("calendar", "tap tile to allow access")
            events == null -> FacePlaceholder("calendar", "loading…")
            events.isEmpty() -> FacePlaceholder("calendar", "no upcoming events")
            else -> {
                val shown = if (back) events.drop(1).take(2) else events.take(1)
                if (shown.isEmpty()) {
                    FacePlaceholder("calendar", "that's everything")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                shown.forEachIndexed { i, event ->
                                    if (i > 0) {
                                        Text(
                                            text = "· · ·",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        )
                                    }
                                    EventLine(
                                        event = event,
                                        big = !back && tile.size == TileSize.WIDE,
                                    )
                                }
                            }
                        }
                        Box(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLine(event: CalendarEvent, big: Boolean) {
    Text(
        text = event.title,
        color = Color.White,
        fontSize = if (big) 20.sp else 16.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Text(
        text = formatEventTime(event.startMillis),
        color = Color.White.copy(alpha = 0.85f),
        fontSize = if (big) 15.sp else 13.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp),
    )
}

// ---------------------------------------------------------------------------
// Weather face
// ---------------------------------------------------------------------------

/** Front face: current temp + condition. Back face: today's high/low. */
@Composable
fun WeatherTileFace(
    tile: PinnedTile,
    weather: WeatherNow?,
    attempted: Boolean,
    back: Boolean,
) {
    FaceScaffold(label = tile.label) {
        when {
            weather == null && !attempted -> FacePlaceholder("weather", "loading…")
            weather == null -> FacePlaceholder("weather", "unavailable")
            else -> {
                val big = tile.size == TileSize.WIDE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!back) {
                                Text(
                                    text = "${weather.tempF.roundToInt()}°",
                                    color = Color.White,
                                    fontSize = if (big) 46.sp else 32.sp,
                                    maxLines = 1,
                                )
                                Text(
                                    text = weather.description,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = if (big) 16.sp else 13.sp,
                                    maxLines = 1,
                                )
                            } else {
                                Text(
                                    text = "${weather.highF.roundToInt()}° / ${weather.lowF.roundToInt()}°",
                                    color = Color.White,
                                    fontSize = if (big) 30.sp else 22.sp,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "today's high / low",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = if (big) 16.sp else 13.sp,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = weather.placeLabel,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Box(Modifier.height(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Photos face
// ---------------------------------------------------------------------------

/**
 * Cycles recent photos — front and back faces show consecutive photos so
 * every flip reveals the next one, like the Windows Phone Photos tile.
 */
@Composable
fun PhotosTileContent(
    tile: PinnedTile,
    uris: List<Uri>,
    hasPermission: Boolean,
    back: Boolean,
) {
    var tick by remember { mutableStateOf(0) }
    // Advance on the same ~10s cadence the tile flips on.
    LaunchedEffect(uris.size) {
        while (true) {
            delay(10_000)
            tick++
        }
    }
    when {
        !hasPermission || uris.isEmpty() -> FaceScaffold(label = tile.label) {
            FacePlaceholder(
                "photos",
                if (!hasPermission) "tap tile to allow access" else "no photos yet",
            )
        }
        else -> {
            val index = (tick + if (back) 1 else 0).mod(uris.size.coerceAtLeast(1))
            val bitmap = rememberPhotoBitmap(uris.getOrNull(index))
            if (bitmap != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Bottom scrim so the label stays readable on bright photos.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                                )
                            ),
                    )
                    Text(
                        text = tile.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 6.dp, end = 8.dp),
                    )
                }
            } else {
                FaceScaffold(label = tile.label) {
                    FacePlaceholder("photos", "loading…")
                }
            }
        }
    }
}
