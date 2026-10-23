package com.metrolauncher.ui

/**
 * Hand-rolled navigation destinations. Kept deliberately simple (no
 * navigation library) since the launcher has a shallow hierarchy:
 * Start <-> App list <-> Settings sub-screens.
 */
sealed class Screen {
    data object Start : Screen()
    data object AppList : Screen()
    data object Settings : Screen()
    data object FontPicker : Screen()
    data object IconStylePicker : Screen()
    data object AccentPicker : Screen()
    data object ActionCenter : Screen()
    data object Feed : Screen()
}
