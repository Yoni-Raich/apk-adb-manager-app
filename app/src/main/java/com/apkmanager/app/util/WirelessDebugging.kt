package com.apkmanager.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log

/**
 * Snapshot of everything Wireless Debugging depends on, read from system settings.
 *
 * @param developerOptions Developer options are enabled.
 * @param wifi The active network is Wi-Fi (Wireless Debugging refuses to run otherwise).
 * @param wirelessDebugging `adb_wifi_enabled` is on.
 * @param canSelfEnable The app holds WRITE_SECURE_SETTINGS and can switch Wireless Debugging on itself.
 */
data class WirelessStatus(
    val developerOptions: Boolean = false,
    val wifi: Boolean = false,
    val wirelessDebugging: Boolean = false,
    val canSelfEnable: Boolean = false
)

/**
 * Reads and (when permitted) writes the system settings behind Wireless Debugging.
 *
 * Reading Settings.Global needs no permission. Writing `adb_wifi_enabled` needs
 * WRITE_SECURE_SETTINGS, which the app grants itself once over ADB
 * (`pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`), so later sessions can
 * turn Wireless Debugging back on after a reboot or Wi-Fi change without Settings.
 */
object WirelessDebugging {

    private const val TAG = "WirelessDebugging"

    /** Settings.Global key for Wireless Debugging (hidden constant ADB_WIFI_ENABLED). */
    const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    fun status(context: Context) = WirelessStatus(
        developerOptions = globalFlag(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        wifi = isOnWifi(context),
        wirelessDebugging = globalFlag(context, ADB_WIFI_ENABLED),
        canSelfEnable = canWriteSecureSettings(context)
    )

    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Turns Wireless Debugging on. Returns false when not permitted or not on Wi-Fi. */
    fun enable(context: Context): Boolean {
        if (!canWriteSecureSettings(context) || !isOnWifi(context)) return false
        return try {
            Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED, 1)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to enable Wireless Debugging", e)
            false
        }
    }

    private fun globalFlag(context: Context, key: String): Boolean =
        try {
            Settings.Global.getInt(context.contentResolver, key, 0) == 1
        } catch (_: Exception) {
            false
        }

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
