package com.metrolauncher.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
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
import com.metrolauncher.data.AccentColor
import com.metrolauncher.data.IconStyle
import com.metrolauncher.data.PinnedTile

/**
 * The Start screen: a 4-column tile grid like Windows 10 Mobile.
 *
 * Tile aspect ratios are derived from [com.metrolauncher.data.TileSize]:
 * small = 1x1, medium = 2x2, wide = 4x2 grid units.
 */
@Composable
fun StartScreen(
    tiles: List<PinnedTile>,
    iconStyle: IconStyle,
    globalAccent: AccentColor,
    loadIcon: (PinnedTile) -> android.graphics.drawable.Drawable?,
    onTileClick: (PinnedTile) -> Unit,
    onTileSizeChange: (PinnedTile, com.metrolauncher.data.TileSize) -> Unit,
    onTileAccentChange: (PinnedTile, AccentColor?) -> Unit,
    onTileLiveModeChange: (PinnedTile, Boolean) -> Unit,
    onUnpinTile: (PinnedTile) -> Unit,
    onOpenAppList: () -> Unit,
    onOpenActionCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingTile by remember { mutableStateOf<PinnedTile?>(null) }
    var accentEditingTile by remember { mutableStateOf<PinnedTile?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (tiles.isEmpty()) {
            // First-run empty state pointing at the app list.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "start",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pin apps here from the app list to build your Start screen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOpenAppList) {
                    Text("open app list")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = tiles,
                    key = { it.packageName + "/" + it.activityName },
                    span = { tile -> GridItemSpan(tile.size.columns) },
                ) { tile ->
                    MetroTile(
                        tile = tile,
                        tileColor = tile.resolveColor(globalAccent),
                        iconStyle = iconStyle,
                        loadIcon = loadIcon,
                        onClick = { onTileClick(tile) },
                        // Long-press opens resize/unpin; a second tap in the
                        // dialog could open the color override — kept simple:
                        // size dialog first, color via dialog's flow below.
                        onLongClick = { editingTile = tile },
                        modifier = Modifier.aspectRatio(
                            tile.size.columns.toFloat() / tile.size.rows.toFloat()
                        ),
                    )
                }
            }
        }

        editingTile?.let { tile ->
            TileEditDialog(
                tile = tile,
                onSizeChange = {
                    onTileSizeChange(tile, it)
                    editingTile = null
                    // Offer the color override right after resizing, mirroring
                    // the deep per-tile customization MetroV is known for.
                    accentEditingTile = tile
                },
                onUnpin = {
                    onUnpinTile(tile)
                    editingTile = null
                },
                onLiveModeChange = { live ->
                    onTileLiveModeChange(tile, live)
                    editingTile = null
                },
                onDismiss = { editingTile = null },
            )
        }

        accentEditingTile?.let { tile ->
            TileAccentPickerDialog(
                tile = tile,
                globalAccent = globalAccent,
                onAccentChange = { onTileAccentChange(tile, it) },
                onDismiss = { accentEditingTile = null },
            )
        }
    }
}
