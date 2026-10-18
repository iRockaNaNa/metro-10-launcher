package com.metrolauncher.data

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Real system-service wiring for the Action Center quick toggles.
 *
 * Every toggle owns its permission story ("permissions per toggle"): each
 * function checks what it needs and returns false when the platform refused,
 * so the UI can degrade to a graceful "needs permission" state instead of
 * crashing. Toggles Android forbids third-party apps from switching directly
 * (wi-fi radio, airplane mode) are exposed as deep links to the matching
 * system settings page — an honest toggle, not a dead one.
 */
data class ToggleStates(
    val wifiOn: Boolean,
    val bluetoothOn: Boolean,
    val dndOn: Boolean,
    val airplaneOn: Boolean,
    val rotationLocked: Boolean,
    val brightness: Int, // 0..255
)

object SystemToggles {

    const val PERMISSION_CAMERA: String = Manifest.permission.CAMERA
    const val PERMISSION_BLUETOOTH_CONNECT: String = Manifest.permission.BLUETOOTH_CONNECT

    /** BLUETOOTH_CONNECT only exists as a runtime permission on API 31+. */
    fun needsBluetoothRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun hasCameraPermission(context: Context): Boolean =
        checkPermission(context, PERMISSION_CAMERA)

    fun hasBluetoothConnectPermission(context: Context): Boolean =
        !needsBluetoothRuntimePermission() ||
            checkPermission(context, PERMISSION_BLUETOOTH_CONNECT)

    private fun checkPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /** Special app-access permission (granted in system settings, not a dialog). */
    fun canWriteSettings(context: Context): Boolean =
        Settings.System.canWrite(context)

    fun hasNotificationPolicyAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted

    fun readStates(context: Context): ToggleStates {
        val cr = context.contentResolver
        return ToggleStates(
            wifiOn = readWifi(context),
            bluetoothOn = readBluetooth(context),
            dndOn = readDnd(context),
            airplaneOn = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            rotationLocked =
                Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 0,
            brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
                .coerceIn(0, 255),
        )
    }

    private fun readWifi(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: SecurityException) {
        false
    }

    private fun readBluetooth(context: Context): Boolean {
        if (!hasBluetoothConnectPermission(context)) return false
        return try {
            bluetoothAdapter(context)?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun readDnd(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return try {
            nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: SecurityException) {
            false
        }
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }

    /** First camera with a flash unit, or null when there isn't one. */
    fun findTorchCameraId(context: Context): String? {
        val cm = context.getSystemService(CameraManager::class.java) ?: return null
        return try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    /** False when the permission is missing or the platform refused. */
    fun setFlashlight(context: Context, on: Boolean): Boolean {
        if (!hasCameraPermission(context)) return false
        val cm = context.getSystemService(CameraManager::class.java) ?: return false
        val id = findTorchCameraId(context) ?: return false
        return try {
            cm.setTorchMode(id, on)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Turning Bluetooth on always goes through the system consent dialog —
     * launch the returned intent. Turning it off tries [BluetoothAdapter.disable];
     * false means the platform refused (API 33+), so open settings instead.
     */
    fun bluetoothEnableIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun tryDisableBluetooth(context: Context): Boolean {
        if (!hasBluetoothConnectPermission(context)) return false
        return try {
            bluetoothAdapter(context)?.disable() == true
        } catch (e: SecurityException) {
            false
        }
    }

    fun setDnd(context: Context, on: Boolean): Boolean {
        if (!hasNotificationPolicyAccess(context)) return false
        return try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL,
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun setRotationLocked(context: Context, locked: Boolean): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (locked) 0 else 1,
            )
        } catch (e: SecurityException) {
            false
        }
    }

    fun setBrightness(context: Context, value: Int): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            val cr = context.contentResolver
            Settings.System.putInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(1, 255),
            )
        } catch (e: SecurityException) {
            false
        }
    }

    // -- deep links: the honest answer where Android forbids direct toggles --

    fun internetPanelIntent(): Intent =
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun airplaneSettingsIntent(): Intent =
        Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun bluetoothSettingsIntent(): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationPolicyIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
