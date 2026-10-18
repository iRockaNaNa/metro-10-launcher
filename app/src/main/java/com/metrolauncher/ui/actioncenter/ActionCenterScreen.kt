package com.metrolauncher.ui.actioncenter

import android.Manifest
import android.content.Intent
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolauncher.data.SystemToggles
import com.metrolauncher.ui.start.TilePermission
import com.metrolauncher.ui.start.rememberTilePermission
import kotlinx.coroutines.delay

/**
 * Action Center — every quick toggle is wired to its real system service.
 *
 * Each toggle owns its permission story and asks on first use, never in
 * bulk: flashlight asks for the camera, bluetooth for nearby-devices
 * (API 31+), rotation lock and brightness need the "modify system settings"
 * grant, and do-not-disturb needs notification-policy access. Where Android
 * forbids third-party apps from flipping the switch directly (wi-fi radio,
 * airplane mode), the row shows the real state and opens the matching
 * system settings page instead of pretending.
 */
@Composable
fun ActionCenterScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var states by remember { mutableStateOf(SystemToggles.readStates(context)) }
    fun refresh() {
        states = SystemToggles.readStates(context)
    }

    // Keep real state fresh while the screen is visible (cheap settings reads).
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(4000)
        }
    }

    // -- per-toggle runtime permissions (asked on first use, never in bulk) --
    val (cameraPerm, requestCamera) = rememberTilePermission(Manifest.permission.CAMERA)
    val (btPerm, requestBt) = rememberTilePermission(Manifest.permission.BLUETOOTH_CONNECT)
    val btGranted = !SystemToggles.needsBluetoothRuntimePermission() ||
        btPerm == TilePermission.GRANTED

    // -- flashlight state follows the real torch via a system callback -------
    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(cameraPerm) {
        val cm = context.getSystemService(CameraManager::class.java)
        val cameraId = if (cameraPerm == TilePermission.GRANTED) {
            SystemToggles.findTorchCameraId(context)
        } else {
            null
        }
        var callback: CameraManager.TorchCallback? = null
        if (cm != null && cameraId != null) {
            val torchId = cameraId
            callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(changedId: String, enabled: Boolean) {
                    if (changedId == torchId) torchOn = enabled
                }
            }
            cm.registerTorchCallback(callback, null)
        }
        onDispose {
            callback?.let { cm?.unregisterTorchCallback(it) }
        }
    }

    val canWrite = SystemToggles.canWriteSettings(context)
    val dndAccess = SystemToggles.hasNotificationPolicyAccess(context)

    fun open(intent: Intent) {
        context.startActivity(intent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "action center",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        // wi-fi: Android 10+ forbids direct toggling — show state, open the
        // sanctioned system panel.
        ToggleRow(
            icon = Icons.Filled.Wifi,
            title = "wi-fi",
            checked = states.wifiOn,
            subtitle = "opens system panel",
            onToggle = { open(SystemToggles.internetPanelIntent()) },
        )

        ToggleRow(
            icon = Icons.Filled.Bluetooth,
            title = "bluetooth",
            checked = states.bluetoothOn,
            subtitle = if (!btGranted) "needs permission — tap to allow" else null,
            onToggle = { wantOn ->
                if (!btGranted) {
                    if (SystemToggles.needsBluetoothRuntimePermission()) requestBt()
                } else if (wantOn) {
                    // System consent dialog — the proper way to enable.
                    open(SystemToggles.bluetoothEnableIntent())
                } else if (!SystemToggles.tryDisableBluetooth(context)) {
                    // Platform refused the direct call — hand off to settings.
                    open(SystemToggles.bluetoothSettingsIntent())
                }
                refresh()
            },
        )

        ToggleRow(
            icon = Icons.Filled.FlashlightOn,
            title = "flashlight",
            checked = torchOn,
            subtitle = when (cameraPerm) {
                TilePermission.GRANTED -> null
                TilePermission.DENIED -> "camera permission denied — tap to retry"
                TilePermission.UNKNOWN -> "needs camera permission"
            },
            onToggle = { wantOn ->
                if (cameraPerm == TilePermission.GRANTED) {
                    SystemToggles.setFlashlight(context, wantOn)
                    // State arrives via the torch callback.
                } else {
                    requestCamera()
                }
            },
        )

        ToggleRow(
            icon = Icons.Filled.DoNotDisturbOn,
            title = "do not disturb",
            checked = states.dndOn,
            subtitle = if (!dndAccess) "needs access — tap to grant" else null,
            onToggle = { wantOn ->
                if (!dndAccess) {
                    open(SystemToggles.notificationPolicyIntent())
                } else {
                    SystemToggles.setDnd(context, wantOn)
                    refresh()
                }
            },
        )

        // airplane mode is read-only for third-party apps — honest deep link.
        ToggleRow(
            icon = Icons.Filled.AirplanemodeActive,
            title = "airplane mode",
            checked = states.airplaneOn,
            subtitle = "opens settings",
            onToggle = { open(SystemToggles.airplaneSettingsIntent()) },
        )

        ToggleRow(
            icon = Icons.Filled.ScreenRotation,
            title = "rotation lock",
            checked = states.rotationLocked,
            subtitle = if (!canWrite) "needs permission — tap to grant" else null,
            onToggle = { wantLocked ->
                if (!canWrite) {
                    open(SystemToggles.writeSettingsIntent(context))
                } else {
                    SystemToggles.setRotationLocked(context, wantLocked)
                    refresh()
                }
            },
        )

        BrightnessRow(
            level = states.brightness,
            needsPermission = !canWrite,
            onStep = { delta ->
                if (!canWrite) {
                    open(SystemToggles.writeSettingsIntent(context))
                } else {
                    SystemToggles.setBrightness(context, states.brightness + delta)
                    refresh()
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    subtitle: String?,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 20.sp)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun BrightnessRow(
    level: Int,
    needsPermission: Boolean,
    onStep: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Brightness6,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "brightness", fontSize = 20.sp)
            if (needsPermission) {
                Text(
                    text = "needs permission — tap + or − to grant",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onStep(-25) }) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = "dimmer")
        }
        Text(
            text = "${(level * 100) / 255}%",
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { onStep(25) }) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "brighter")
        }
    }
}
