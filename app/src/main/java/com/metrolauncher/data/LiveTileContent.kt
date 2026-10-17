package com.metrolauncher.data

/**
 * Built-in live tile providers.
 *
 * A live tile flips between faces on a ~10s cadence (see MetroTile) and
 * shows updating content instead of a static icon. v2 ships four providers —
 * Clock, Calendar, Weather, Photos — and [PinnedTile.liveTile] is the hook
 * they all hang off.
 *
 * The built-in tiles are synthetic: they have no real component, so
 * [LauncherRepository] special-cases them when listing apps (so they can be
 * pinned from the app list) and when launching (each opens a sensible
 * target: clock, calendar, browser, gallery).
 */
object LiveTileContent {

    /** Pseudo-package identifying the built-in Clock live tile. */
    const val CLOCK_PACKAGE = "com.metrolauncher.builtin.clock"

    /** Pseudo-activity for the Clock tile; not a real component. */
    const val CLOCK_ACTIVITY = "$CLOCK_PACKAGE.ClockTile"

    /** Pseudo-package identifying the built-in Calendar live tile. */
    const val CALENDAR_PACKAGE = "com.metrolauncher.builtin.calendar"

    /** Pseudo-activity for the Calendar tile; not a real component. */
    const val CALENDAR_ACTIVITY = "$CALENDAR_PACKAGE.CalendarTile"

    /** Pseudo-package identifying the built-in Weather live tile. */
    const val WEATHER_PACKAGE = "com.metrolauncher.builtin.weather"

    /** Pseudo-activity for the Weather tile; not a real component. */
    const val WEATHER_ACTIVITY = "$WEATHER_PACKAGE.WeatherTile"

    /** Pseudo-package identifying the built-in Photos live tile. */
    const val PHOTOS_PACKAGE = "com.metrolauncher.builtin.photos"

    /** Pseudo-activity for the Photos tile; not a real component. */
    const val PHOTOS_ACTIVITY = "$PHOTOS_PACKAGE.PhotosTile"

    fun isClockTile(tile: PinnedTile): Boolean =
        tile.packageName == CLOCK_PACKAGE

    fun isCalendarTile(tile: PinnedTile): Boolean =
        tile.packageName == CALENDAR_PACKAGE

    fun isWeatherTile(tile: PinnedTile): Boolean =
        tile.packageName == WEATHER_PACKAGE

    fun isPhotosTile(tile: PinnedTile): Boolean =
        tile.packageName == PHOTOS_PACKAGE

    /** True for any synthetic built-in live tile. */
    fun isBuiltinTile(tile: PinnedTile): Boolean =
        isBuiltinPackage(tile.packageName)

    /** True for any synthetic built-in live tile package. */
    fun isBuiltinPackage(packageName: String): Boolean = packageName == CLOCK_PACKAGE ||
        packageName == CALENDAR_PACKAGE ||
        packageName == WEATHER_PACKAGE ||
        packageName == PHOTOS_PACKAGE

    /** The built-in Clock live tile: flips between time and date faces. */
    fun clockTile(size: TileSize = TileSize.WIDE): PinnedTile = PinnedTile(
        packageName = CLOCK_PACKAGE,
        activityName = CLOCK_ACTIVITY,
        label = "Clock",
        size = size,
        liveTile = true,
    )

    /** The built-in Calendar live tile: flips through upcoming events. */
    fun calendarTile(size: TileSize = TileSize.WIDE): PinnedTile = PinnedTile(
        packageName = CALENDAR_PACKAGE,
        activityName = CALENDAR_ACTIVITY,
        label = "Calendar",
        size = size,
        liveTile = true,
    )

    /** The built-in Weather live tile: flips between now and today's high/low. */
    fun weatherTile(size: TileSize = TileSize.WIDE): PinnedTile = PinnedTile(
        packageName = WEATHER_PACKAGE,
        activityName = WEATHER_ACTIVITY,
        label = "Weather",
        size = size,
        liveTile = true,
    )

    /** The built-in Photos live tile: cycles recent photos on every flip. */
    fun photosTile(size: TileSize = TileSize.WIDE): PinnedTile = PinnedTile(
        packageName = PHOTOS_PACKAGE,
        activityName = PHOTOS_ACTIVITY,
        label = "Photos",
        size = size,
        liveTile = true,
    )
}
