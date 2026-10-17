package com.metrolauncher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metrolauncher.data.AccentColor
import com.metrolauncher.data.LiveTileContent
import com.metrolauncher.data.PinnedTile
import com.metrolauncher.data.TileSize

/**
 * Long-press menu for a Start tile: resize (small/medium/wide, like
 * Windows 10 Mobile and MetroV), toggle live-tile mode, or unpin from Start.
 */
@Composable
fun TileEditDialog(
    tile: PinnedTile,
    onSizeChange: (TileSize) -> Unit,
    onLiveModeChange: (Boolean) -> Unit,
    onUnpin: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tile.label) },
        text = {
            Column {
                Text(
                    text = "tile size",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                TileSize.entries.forEach { size ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = tile.size == size,
                            onClick = { onSizeChange(size) },
                        )
                        Text(size.title)
                    }
                }
                // Live content needs room to breathe: small tiles stay static.
                if (tile.size != TileSize.SMALL) {
                    val isClock = LiveTileContent.isClockTile(tile)
                    Text(
                        text = "live tile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isClock) "clock is always live"
                            else "flip between faces every 10 seconds",
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = tile.liveTile,
                            onCheckedChange = onLiveModeChange,
                            // The Clock tile is live by nature; it can't be
                            // turned off.
                            enabled = !isClock,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("done") }
        },
        dismissButton = {
            TextButton(onClick = onUnpin) { Text("unpin from start") }
        },
    )
}

/** Per-tile accent override picker (null = follow the theme accent). */
@Composable
fun TileAccentPickerDialog(
    tile: PinnedTile,
    globalAccent: AccentColor,
    onAccentChange: (AccentColor?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedName = tile.accentName ?: globalAccent.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("tile color") },
        text = {
            Column {
                AccentColor.entries.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { accent ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable { onAccentChange(accent) }
                                    .background(accent.color, CircleShape)
                                    .size(36.dp)
                                    .border(
                                        width = if (selectedName == accent.name) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
                TextButton(onClick = { onAccentChange(null) }) {
                    Text("use theme accent")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("done") }
        },
    )
}
