package com.metrolauncher.ui.feed

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolauncher.data.FEED_SOURCE_NAME
import com.metrolauncher.data.FeedArticle
import com.metrolauncher.data.fetchFeedArticles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Feed — a Google Now-like panel of cards, reached by swiping right
 * on Start. Content comes from Windows Central's RSS feed (see
 * [fetchFeedArticles]); each card is a hero image over a headline, a
 * short description, and a category + time line, and tapping a card
 * opens the story in the browser.
 *
 * Sharp corners and lowercase labels keep it Metro, per the project
 * conventions; the stacked-card layout is the Google Now part.
 */
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var articles by remember { mutableStateOf<List<FeedArticle>?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        articles = fetchFeedArticles()
        attempted = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "feed",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Text(
            text = "from $FEED_SOURCE_NAME",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )

        when {
            articles == null && !attempted -> FeedMessage("loading…", null, null)
            articles.isNullOrEmpty() -> FeedMessage(
                title = "couldn't load the feed",
                actionLabel = "try again",
                onAction = {
                    attempted = false
                    articles = null
                    refreshKey++
                },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = articles.orEmpty(),
                    key = { it.link },
                ) { article ->
                    FeedCard(
                        article = article,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Centered loading / error placeholder, in the tile-face style. */
@Composable
private fun FeedMessage(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 18.sp)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** One Google Now-style card: image, meta line, headline, description. */
@Composable
private fun FeedCard(
    article: FeedArticle,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
    ) {
        val bitmap = rememberArticleImage(article.imageUrl)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!article.category.isNullOrBlank()) {
                    Text(
                        text = article.category.lowercase(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                val time = formatArticleTime(article.publishedMillis)
                if (time != null) {
                    Text(
                        text = if (article.category.isNullOrBlank()) time else "  ·  $time",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }

            Text(
                text = article.title,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (article.description.isNotBlank()) {
                Text(
                    text = article.description,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Loads a card's hero image straight from its URL (no image library in
 * the project — same hand-rolled approach as the weather tile). Decoded
 * at half size; the card crops it anyway. Null while loading/on failure,
 * in which case the card is text-only.
 */
@Composable
private fun rememberArticleImage(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = url?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(it).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        setRequestProperty("User-Agent", "Metro10Launcher (Android)")
                    }
                    try {
                        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                            null
                        } else {
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 2
                            }
                            conn.inputStream.use { stream ->
                                BitmapFactory.decodeStream(stream, null, options)
                            }?.asImageBitmap()
                        }
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()
            }
        }
    }
    return bitmap
}

/** "2h ago" / "25m ago" today, "yesterday", else "Oct 22". Null for 0. */
private fun formatArticleTime(millis: Long): String? {
    if (millis <= 0L) return null
    val zone = ZoneId.systemDefault()
    val then = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val now = LocalDateTime.now(zone)
    val ageMinutes = java.time.Duration.between(then, now).toMinutes()
    return when {
        ageMinutes < 1 -> "just now"
        ageMinutes < 60 -> "${ageMinutes}m ago"
        ageMinutes < 60 * 24 && then.toLocalDate() == now.toLocalDate() ->
            "${ageMinutes / 60}h ago"
        then.toLocalDate() == now.toLocalDate().minusDays(1) -> "yesterday"
        else -> then.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
