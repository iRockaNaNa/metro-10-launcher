package com.metrolauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.metrolauncher.data.AccentColor
import com.metrolauncher.data.AppEntry
import com.metrolauncher.data.IconStyle
import com.metrolauncher.data.LauncherFont
import com.metrolauncher.data.LauncherPreferences
import com.metrolauncher.data.LauncherRepository
import com.metrolauncher.data.LiveTileContent
import com.metrolauncher.data.PinnedTile
import com.metrolauncher.ui.Screen
import com.metrolauncher.ui.actioncenter.ActionCenterScreen
import com.metrolauncher.ui.applist.AppListScreen
import com.metrolauncher.ui.feed.FeedScreen
import com.metrolauncher.ui.settings.AccentPickerScreen
import com.metrolauncher.ui.settings.FontPickerScreen
import com.metrolauncher.ui.settings.IconStylePickerScreen
import com.metrolauncher.ui.settings.SettingsScreen
import com.metrolauncher.ui.start.PickALookDialog
import com.metrolauncher.ui.start.StartScreen
import com.metrolauncher.ui.theme.MetroLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val currentScreen = mutableStateOf<Screen>(Screen.Start)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back always returns to Start first, like a real launcher.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreen.value != Screen.Start) {
                    currentScreen.value = Screen.Start
                } else {
                    finish()
                }
            }
        })

        val prefs = LauncherPreferences(applicationContext)
        val repository = LauncherRepository(applicationContext)

        setContent {
            val font by prefs.font.collectAsState(initial = LauncherFont.SEGOE)
            val iconStyle by prefs.iconStyle.collectAsState(initial = IconStyle.METRO_GLYPH)
            val accent by prefs.accent.collectAsState(initial = AccentColor.COBALT)
            val darkTheme by prefs.darkTheme.collectAsState(initial = true)
            val pinnedTiles by prefs.pinnedTiles.collectAsState(initial = emptyList())
            val firstRunDone by prefs.firstRunDone.collectAsState(initial = true)
            val scope = rememberCoroutineScope()

            var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
            val screen = currentScreen.value

            // Refresh the app list every time we open it (cheap enough, and
            // keeps installs/uninstalls accurate).
            LaunchedEffect(screen) {
                if (screen == Screen.AppList) {
                    apps = withContext(Dispatchers.IO) { repository.getInstalledApps() }
                }
            }

            MetroLauncherTheme(darkTheme = darkTheme, accent = accent.color, font = font) {
                // First-run onboarding: pick a look, then seed default tiles.
                if (!firstRunDone) {
                    PickALookDialog { pickedDark, pickedAccent ->
                        scope.launch {
                            prefs.setDarkTheme(pickedDark)
                            prefs.setAccent(pickedAccent)
                            prefs.setPinnedTiles(repository.seedDefaultTiles())
                            prefs.setFirstRunDone(true)
                        }
                    }
                }

                // Gesture nav, Windows Phone style: on Start, swipe left ->
                // app list, swipe right -> the Feed panel, and swipe down
                // from the top edge -> Action Center (that's how WP opened
                // it). On the Feed, swipe left -> back to Start.
                val swipeNav = when (screen) {
                    Screen.Start -> Modifier.pointerInput(Unit) {
                        var totalX = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> totalX += dragAmount },
                            onDragEnd = {
                                when {
                                    totalX < -120 -> currentScreen.value = Screen.AppList
                                    totalX > 120 -> currentScreen.value = Screen.Feed
                                }
                            },
                        )
                    }
                    Screen.Feed -> Modifier.pointerInput(Unit) {
                        var totalX = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> totalX += dragAmount },
                            onDragEnd = {
                                if (totalX < -120) currentScreen.value = Screen.Start
                            },
                        )
                    }
                    else -> Modifier
                }

                // Top-edge pull-down strip for the Action Center, only on
                // Start. A thin invisible grab zone at the very top (like
                // pulling down the WP status bar) so it never fights the
                // tile grid's own vertical scrolling.
                val actionCenterPull = if (screen == Screen.Start) {
                    Modifier.pointerInput(Unit) {
                        var totalY = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount -> totalY += dragAmount },
                            onDragEnd = {
                                if (totalY > 90) {
                                    currentScreen.value = Screen.ActionCenter
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                }

                Box(modifier = Modifier.fillMaxSize().then(swipeNav)) {
                    when (screen) {
                        Screen.Start -> StartScreen(
                            tiles = pinnedTiles,
                            iconStyle = iconStyle,
                            globalAccent = accent,
                            loadIcon = { tile ->
                                repository.loadIcon(
                                    AppEntry(tile.label, tile.packageName, tile.activityName)
                                )
                            },
                            onTileClick = { tile -> repository.launchTile(tile) },
                            onTileSizeChange = { tile, size ->
                                scope.launch { prefs.updateTileSize(tile, size) }
                            },
                            onTileAccentChange = { tile, tileAccent ->
                                scope.launch { prefs.updateTileAccent(tile, tileAccent) }
                            },
                            onTileLiveModeChange = { tile, live ->
                                scope.launch { prefs.updateTileLiveMode(tile, live) }
                            },
                            onUnpinTile = { tile ->
                                scope.launch { prefs.unpinTile(tile) }
                            },
                            onOpenAppList = { currentScreen.value = Screen.AppList },
                            onOpenActionCenter = { currentScreen.value = Screen.ActionCenter },
                        )

                        Screen.AppList -> SubScreenScaffold(
                            title = "apps",
                            onBack = { currentScreen.value = Screen.Start },
                            onOpenSettings = { currentScreen.value = Screen.Settings },
                        ) {
                            AppListScreen(
                                apps = apps,
                                accent = accent,
                                onAppClick = { app -> repository.launchApp(app) },
                                onPinApp = { app, size ->
                                    scope.launch {
                                        prefs.pinTile(
                                            PinnedTile(
                                                packageName = app.packageName,
                                                activityName = app.activityName,
                                                label = app.label,
                                                size = size,
                                                // The built-in live tiles
                                                // (Clock, Calendar, Weather,
                                                // Photos) are live from the
                                                // moment they're pinned.
                                                liveTile = LiveTileContent.isBuiltinPackage(
                                                    app.packageName
                                                ),
                                            )
                                        )
                                    }
                                },
                                isPinned = { app ->
                                    pinnedTiles.any {
                                        it.packageName == app.packageName &&
                                            it.activityName == app.activityName
                                    }
                                },
                            )
                        }

                        Screen.Settings -> SubScreenScaffold(
                            title = "settings",
                            onBack = { currentScreen.value = Screen.Start },
                            onOpenSettings = null,
                        ) {
                            SettingsScreen(
                                darkTheme = darkTheme,
                                onDarkThemeChange = { scope.launch { prefs.setDarkTheme(it) } },
                                onNavigate = { currentScreen.value = it },
                            )
                        }

                        Screen.FontPicker -> SubScreenScaffold(
                            title = "fonts",
                            onBack = { currentScreen.value = Screen.Settings },
                            onOpenSettings = null,
                        ) {
                            FontPickerScreen(
                                selected = font,
                                onSelect = { scope.launch { prefs.setFont(it) } },
                            )
                        }

                        Screen.IconStylePicker -> SubScreenScaffold(
                            title = "icon style",
                            onBack = { currentScreen.value = Screen.Settings },
                            onOpenSettings = null,
                        ) {
                            IconStylePickerScreen(
                                selected = iconStyle,
                                accent = accent,
                                onSelect = { scope.launch { prefs.setIconStyle(it) } },
                            )
                        }

                        Screen.AccentPicker -> SubScreenScaffold(
                            title = "accent color",
                            onBack = { currentScreen.value = Screen.Settings },
                            onOpenSettings = null,
                        ) {
                            AccentPickerScreen(
                                selected = accent,
                                onSelect = { scope.launch { prefs.setAccent(it) } },
                            )
                        }

                        Screen.Feed -> SubScreenScaffold(
                            title = "feed",
                            onBack = { currentScreen.value = Screen.Start },
                            onOpenSettings = null,
                        ) {
                            FeedScreen()
                        }

                        Screen.ActionCenter -> SubScreenScaffold(
                            title = "action center",
                            onBack = { currentScreen.value = Screen.Start },
                            onOpenSettings = { currentScreen.value = Screen.Settings },
                        ) {
                            ActionCenterScreen()
                        }
                    }

                    // Invisible top-edge grab zone: pull down -> Action
                    // Center. Drawn last so it sits above the Start grid.
                    if (screen == Screen.Start) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(28.dp)
                                .then(actionCenterPull)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple top bar for sub-screens: back arrow, lowercase title, and an
 * optional settings shortcut (used on the app list, like the original).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "back")
                    }
                },
                actions = {
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            content()
        }
    }
}
