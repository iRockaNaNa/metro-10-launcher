package com.metrolauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolauncher.ui.Screen

/**
 * Settings in the Windows Phone style: a lowercase section header followed
 * by a plain list of rows — the same Metro settings aesthetic MetroV
 * recreates from the Lumia days.
 */
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        SettingsRow(title = "fonts", subtitle = "Pick the typeface used across the launcher") {
            onNavigate(Screen.FontPicker)
        }
        SettingsRow(title = "icon style", subtitle = "Metro glyphs, modern rounded, or mono outline") {
            onNavigate(Screen.IconStylePicker)
        }
        SettingsRow(title = "accent color", subtitle = "The classic Windows Phone palette") {
            onNavigate(Screen.AccentPicker)
        }

        // Theme is a direct toggle — no sub-screen needed.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "dark theme", fontSize = 20.sp)
                Text(
                    text = if (darkTheme) "Black background, like the original"
                    else "White background",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
        }

        SettingsRow(title = "about", subtitle = "Version and open-source info") {
            showAbout = true
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Metro 10 Launcher") },
            text = {
                Text(
                    "An open-source Windows 10 Mobile-style launcher for Android.\n\n" +
                        "Version 1.0.0 (MVP)\n" +
                        "Licensed under the MIT License."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("ok") }
            },
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Lowercase titles are the Windows Phone settings signature.
            Text(text = title, fontSize = 20.sp)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
    }
}
