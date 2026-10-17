package com.metrolauncher.data

/**
 * A tile pinned to the Start screen.
 *
 * @param accentName optional [AccentColor] name overriding the global accent
 * for this tile; null means "use the global accent".
 * @param liveTile when true, the tile flips on the live cadence (~10s) and
 * shows live content where a provider exists (see [LiveTileContent]).
 */
data class PinnedTile(
    val packageName: String,
    val activityName: String,
    val label: String,
    val size: TileSize,
    val accentName: String? = null,
    val liveTile: Boolean = false,
)

/**
 * Minimal pipe-delimited serialization so we can persist tiles in DataStore
 * without adding a serialization library. Format per tile:
 * `package|activity|label|SIZE|accentName|live` (accent may be empty, live is
 * "1" when on). Tiles are joined with `;;`. Labels are sanitized on write.
 * Rows written before the live flag existed (4–5 fields) still parse.
 */
private const val FIELD_SEPARATOR = "|"
private const val TILE_SEPARATOR = ";;"

private fun sanitize(value: String): String =
    value.replace(FIELD_SEPARATOR, " ").replace(TILE_SEPARATOR, " ")

fun PinnedTile.serialize(): String = listOf(
    packageName,
    activityName,
    sanitize(label),
    size.name,
    accentName ?: "",
    if (liveTile) "1" else ""
).joinToString(FIELD_SEPARATOR)

fun deserializeTile(raw: String): PinnedTile? {
    val parts = raw.split(FIELD_SEPARATOR)
    if (parts.size < 4) return null
    val size = runCatching { TileSize.valueOf(parts[3]) }.getOrNull() ?: return null
    return PinnedTile(
        packageName = parts[0],
        activityName = parts[1],
        label = parts[2],
        size = size,
        accentName = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
        liveTile = parts.getOrNull(5) == "1",
    )
}

fun List<PinnedTile>.serializeAll(): String =
    joinToString(TILE_SEPARATOR) { it.serialize() }

fun deserializeTiles(raw: String?): List<PinnedTile> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(TILE_SEPARATOR).mapNotNull { deserializeTile(it) }
}
