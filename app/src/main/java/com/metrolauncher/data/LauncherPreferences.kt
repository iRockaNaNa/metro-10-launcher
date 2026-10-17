package com.metrolauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "metro_launcher_prefs"
)

/**
 * Persists all user customization: font, icon style, accent color, theme,
 * pinned tiles, and first-run state. Everything is exposed as Flows so the
 * Compose UI updates live when the user changes a setting.
 */
class LauncherPreferences(private val context: Context) {

    private companion object {
        val FONT = stringPreferencesKey("font")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val ACCENT = stringPreferencesKey("accent")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val PINNED_TILES = stringPreferencesKey("pinned_tiles")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    }

    val font: Flow<LauncherFont> = context.dataStore.data.map { prefs ->
        prefs[FONT]?.let { runCatching { LauncherFont.valueOf(it) }.getOrNull() }
            ?: LauncherFont.SEGOE
    }

    val iconStyle: Flow<IconStyle> = context.dataStore.data.map { prefs ->
        prefs[ICON_STYLE]?.let { runCatching { IconStyle.valueOf(it) }.getOrNull() }
            ?: IconStyle.METRO_GLYPH
    }

    val accent: Flow<AccentColor> = context.dataStore.data.map { prefs ->
        prefs[ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
            ?: AccentColor.COBALT
    }

    /** Windows Phone defaulted to the dark theme; we do the same. */
    val darkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_THEME] ?: true
    }

    val pinnedTiles: Flow<List<PinnedTile>> = context.dataStore.data.map { prefs ->
        deserializeTiles(prefs[PINNED_TILES])
    }

    val firstRunDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[FIRST_RUN_DONE] ?: false
    }

    suspend fun setFont(font: LauncherFont) {
        context.dataStore.edit { it[FONT] = font.name }
    }

    suspend fun setIconStyle(style: IconStyle) {
        context.dataStore.edit { it[ICON_STYLE] = style.name }
    }

    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit { it[ACCENT] = accent.name }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = dark }
    }

    suspend fun setFirstRunDone(done: Boolean) {
        context.dataStore.edit { it[FIRST_RUN_DONE] = done }
    }

    suspend fun setPinnedTiles(tiles: List<PinnedTile>) {
        context.dataStore.edit { it[PINNED_TILES] = tiles.serializeAll() }
    }

    /** Adds [tile] unless an identical component is already pinned. */
    suspend fun pinTile(tile: PinnedTile) {
        val current = pinnedTiles.first().toMutableList()
        val exists = current.any {
            it.packageName == tile.packageName && it.activityName == tile.activityName
        }
        if (!exists) {
            current.add(tile)
            setPinnedTiles(current)
        }
    }

    suspend fun unpinTile(tile: PinnedTile) {
        val current = pinnedTiles.first()
            .filterNot {
                it.packageName == tile.packageName && it.activityName == tile.activityName
            }
        setPinnedTiles(current)
    }

    suspend fun updateTileSize(tile: PinnedTile, newSize: TileSize) {
        val current = pinnedTiles.first().map {
            if (it.packageName == tile.packageName && it.activityName == tile.activityName) {
                it.copy(size = newSize)
            } else it
        }
        setPinnedTiles(current)
    }

    suspend fun updateTileAccent(tile: PinnedTile, accent: AccentColor?) {
        val current = pinnedTiles.first().map {
            if (it.packageName == tile.packageName && it.activityName == tile.activityName) {
                it.copy(accentName = accent?.name)
            } else it
        }
        setPinnedTiles(current)
    }

    suspend fun updateTileLiveMode(tile: PinnedTile, live: Boolean) {
        val current = pinnedTiles.first().map {
            if (it.packageName == tile.packageName && it.activityName == tile.activityName) {
                it.copy(liveTile = live)
            } else it
        }
        setPinnedTiles(current)
    }
}
