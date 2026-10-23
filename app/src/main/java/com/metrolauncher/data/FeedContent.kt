package com.metrolauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * One story in the Feed panel.
 *
 * [publishedMillis] is 0 when the feed's date string couldn't be parsed —
 * the card then simply shows no time label.
 */
data class FeedArticle(
    val title: String,
    val link: String,
    val description: String,
    val category: String?,
    val imageUrl: String?,
    val publishedMillis: Long,
)

/** The Feed's single source, per the roadmap: Windows Central's RSS feed. */
const val FEED_URL = "https://www.windowscentral.com/rss"
const val FEED_SOURCE_NAME = "windows central"

/**
 * Fetches the latest Windows Central stories, newest first, capped at
 * [max]. Cached in memory for 30 minutes, same as the weather tile, so
 * reopening the panel doesn't hammer the site. Returns an empty list on
 * any failure — the panel shows its "couldn't load" state and offers a
 * retry instead of crashing.
 */
suspend fun fetchFeedArticles(max: Int = 20): List<FeedArticle> =
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        feedCache?.takeIf { now - feedFetchedAt < FEED_TTL_MS }
            ?.let { return@withContext it.take(max) }
        runCatching {
            val conn = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                // The /rss URL 301-redirects to the canonical feeds.xml;
                // HttpURLConnection follows same-protocol redirects itself.
                setRequestProperty("User-Agent", "Metro10Launcher (Android)")
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return@runCatching emptyList()
                }
                val xml = conn.inputStream.bufferedReader().readText()
                parseRss(xml, max)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(emptyList()).also {
            if (it.isNotEmpty()) {
                feedCache = it
                feedFetchedAt = now
            }
        }
    }

private var feedCache: List<FeedArticle>? = null
private var feedFetchedAt: Long = 0
private const val FEED_TTL_MS = 30L * 60 * 1000

/**
 * Minimal RSS 2.0 parser over XmlPullParser (namespace awareness off, so
 * tag names keep their prefixes: "media:content", "content:encoded").
 *
 * Per item it reads title / link / description / category / pubDate, and
 * the hero image from the enclosure, falling back to media:content. The
 * big content:encoded HTML body is skipped — the card only needs the
 * short description.
 */
internal fun parseRss(xml: String, max: Int): List<FeedArticle> {
    val articles = mutableListOf<FeedArticle>()
    val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xml))
    }

    var inItem = false
    var title = ""
    var link = ""
    var description = ""
    var category: String? = null
    var imageUrl: String? = null
    var published = 0L

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT && articles.size < max) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "item" -> {
                    inItem = true
                    title = ""
                    link = ""
                    description = ""
                    category = null
                    imageUrl = null
                    published = 0L
                }
                "title" -> if (inItem) title = parser.nextText().trim()
                "link" -> if (inItem && link.isEmpty()) {
                    parser.nextText().trim().takeIf { it.isNotEmpty() }?.let { link = it }
                }
                "description" -> if (inItem) {
                    description = stripHtml(parser.nextText())
                }
                "category" -> if (inItem && category == null) {
                    category = parser.nextText().trim().takeIf { it.isNotEmpty() }
                }
                "pubDate" -> if (inItem) {
                    published = parsePubDate(parser.nextText().trim())
                }
                "enclosure" -> if (inItem && imageUrl == null) {
                    imageUrl = parser.getAttributeValue(null, "url")
                        ?.takeIf { it.isNotBlank() }
                }
                "media:content" -> if (inItem && imageUrl == null) {
                    imageUrl = parser.getAttributeValue(null, "url")
                        ?.takeIf { it.isNotBlank() }
                }
                // Inside an item, skip the full-body HTML in one jump.
                "content:encoded" -> if (inItem) skipTag(parser)
            }
            XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                inItem = false
                if (title.isNotEmpty() && link.isNotEmpty()) {
                    articles += FeedArticle(
                        title = title,
                        link = link,
                        description = description,
                        category = category,
                        imageUrl = imageUrl,
                        publishedMillis = published,
                    )
                }
            }
        }
        event = parser.next()
    }
    return articles
}

/** Consumes events until the current tag's matching END_TAG. */
private fun skipTag(parser: XmlPullParser) {
    var depth = 1
    while (depth > 0) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

/** Removes tags/entities from a description that arrived with markup. */
private fun stripHtml(raw: String): String =
    raw.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

/** RSS dates: "Thu, 22 Oct 2026 18:18:01 +0000" (GMT spelling also seen). */
private fun parsePubDate(raw: String): Long {
    val normalized = raw.replace(" GMT", " +0000").replace(" UT", " +0000")
    val patterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss Z",
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).parse(normalized)
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return 0L
}
