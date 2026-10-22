# Metro 10 Launcher

An open-source Android launcher that recreates the **Windows 10 Mobile** Start experience —
Live Tiles, the alphabetical app list with jumplist, and the classic 20-color accent palette —
with a few modern twists.

> **Design reference:** [MetroV Launcher](https://play.google.com/store/apps/details?id=com.tuzkituan.metrov)
> (by Tuan Nguyen) was used as the key reference for this build: its tile flip
> animations, Metro-styled settings menu, per-tile resize/color customization, font options,
> alphabetical app list with letter jumplist, and swipe-to-Action-Center gesture all
> informed the design here. MetroV is not affiliated with this project, and this project
> is not affiliated with Microsoft.

![screenshots](screenshots/) *(add screenshots here — see below)*

## Features

- **Start screen** — 4-column tile grid (small / medium / wide tiles) with the signature
  Live Tile flip animation on medium and wide tiles
- **Live Tiles** — Windows Phone-style: live tiles flip between faces every ~10 seconds.
  Ships with four built-in live tiles, all pinnable from the app list:
  **Clock** (time/date faces that update every minute; tap it to open your clock app),
  **Calendar** (your next appointments; tap it to open your calendar),
  **Weather** (current temp + condition, flips to today's high/low, via Open-Meteo;
  tap it to open the forecast site), and **Photos** (cycles your recent photos on
  every flip; tap it to open your gallery). Toggle live mode per tile from the
  tile's long-press menu (medium and wide tiles; built-in tiles are always live)
- **App list** — alphabetical sections with sticky letter headers, search, and a
  Windows Phone-style letter **jumplist** overlay for fast scrolling
- **Pin / unpin / resize** — long-press any app in the list to pin it to Start;
  long-press a tile to resize it or unpin it
- **Per-tile accent override** — give individual tiles their own color
- **Font picker** *(modern twist)* — Segoe, Roboto, Monospace, Serif, or Rounded;
  applies across tiles, app list, and settings
- **Icon style picker** *(modern twist)* — classic Metro glyphs, modern rounded,
  or monochrome outline treatments
- **Accent color picker** — the authentic 20-color Windows Phone palette
- **Dark / light theme**, Metro-styled settings menu (lowercase, like the original)
- **Action Center** — quick toggles wired to system services (flashlight, bluetooth, rotation lock, brightness, do not disturb; wi-fi and airplane mode deep-link to system settings)
- **"Pick a look" onboarding** — theme + accent setup on first run

## Screenshots

Drop PNGs into `screenshots/` and reference them here:

| Start | App list | Settings |
|---|---|---|
| ![start](screenshots/start.png) | ![app list](screenshots/applist.png) | ![settings](screenshots/settings.png) |

## Building

Requirements: **Android Studio Hedgehog or newer**, **JDK 17**.

```bash
# Clone
git clone https://github.com/<you>/metro-10-launcher.git
cd metro-10-launcher

# Open in Android Studio and press Run,
# or build from the command line:
./gradlew assembleDebug
# APK lands in: app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** the Gradle wrapper JAR is not checked in. If `./gradlew` complains,
> run `gradle wrapper` once with a local Gradle 8.7+ install, or just build
> from Android Studio.

### Google Play note

The app requests `QUERY_ALL_PACKAGES` so the app drawer can list installed apps
on Android 11+. Publishing on Google Play requires completing the
*Sensitive permissions* declaration form for this permission.

## Project structure

```
app/src/main/
├── AndroidManifest.xml            # HOME/DEFAULT intent filters → real launcher
├── java/com/metrolauncher/
│   ├── MainActivity.kt            # entry point, prefs wiring, hand-rolled nav,
│   │                              # swipe gestures (left: apps, right: action center)
│   ├── data/
│   │   ├── LauncherRepository.kt  # PackageManager queries, app launching, seed tiles
│   │   ├── LauncherPreferences.kt # DataStore: font, icons, accent, theme, pins
│   │   ├── PinnedTile.kt          # tile model + pipe-delimited serialization
│   │   ├── LiveTileContent.kt     # built-in live tile providers
│   │   │                              # (Clock, Calendar, Weather, Photos)
│   │   ├── LiveTileProviders.kt   # calendar / weather / photos data layer
│   │   │                              # (CalendarContract, Open-Meteo, MediaStore)
│   │   ├── TileSize.kt            # SMALL / MEDIUM / WIDE
│   │   ├── LauncherFont.kt        # 5 font choices
│   │   ├── IconStyle.kt           # 3 icon styles
│   │   └── AccentColor.kt         # 20 authentic WP accent colors
│   └── ui/
│       ├── Screen.kt              # navigation destinations
│       ├── theme/                 # Material3 theme driven by accent + font picks
│       ├── start/                 # Start grid, Live Tile flip + faces
│       │                          # (Clock/Calendar/Weather/Photos), edit dialogs,
│       │                          # per-tile color, "pick a look" onboarding
│       ├── applist/               # alphabetical list, search, jumplist overlay
│       ├── settings/              # Metro-style settings + pickers
│       └── actioncenter/          # quick toggles (UI scaffold)
└── res/                           # launcher icon, theme placeholder
```

## Roadmap

- Google Now-like Panel (swipe to the right) — Google Now card layout, content sourced from Windows Central (windowscentral.com)
- Drag-and-drop tile rearrangement
- Folders on Start
- Bundled Segoe UI-like and rounded font files under `res/font/`
- Home-screen widgets support
- Hide-apps feature
- Backup / restore layout

## Contributing

Issues and pull requests are welcome. Please keep the Metro aesthetic intact —
lowercase settings titles, sharp tile corners (except Modern icon style), and
the 20-color accent palette.

## License

MIT — see [LICENSE](LICENSE).
