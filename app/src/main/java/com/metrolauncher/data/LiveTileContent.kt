package com.metrolauncher.data

/**
 * Built-in live tile providers.
 *
 * A live tile flips between faces on a ~10s cadence (see MetroTile) and
 * shows updating content instead of a static icon. v1 ships one provider —
 * the Clock tile — and [PinnedTile.liveTile] is the hook future providers
 * (calendar, weather, photos) will hang off.
 *
 * The Clock tile is synthetic: it has no real component, so
 * [LauncherRepository] special-cases it when listing apps (so it can be
 * pinned from the app list) and when launching (it opens the system clock).
 */
object LiveTileContent {

    /** Pseudo-package identifying the built-in Clock live tile. */
    const val CLOCK_PACKAGE = "com.metrolauncher.builtin.clock"

    /** Pseudo-activity for the Clock tile; not a real component. */
    const val CLOCK_ACTIVITY = "$CLOCK_PACKAGE.ClockTile"

    fun isClockTile(tile: PinnedTile): Boolean =
        tile.packageName == CLOCK_PACKAGE

    /** The built-in Clock live tile: flips between time and date faces. */
    fun clockTile(size: TileSize = TileSize.WIDE): PinnedTile = PinnedTile(
        packageName = CLOCK_PACKAGE,
        activityName = CLOCK_ACTIVITY,
        label = "Clock",
        size = size,
        liveTile = true,
    )
}
