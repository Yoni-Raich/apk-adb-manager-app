package com.apkmanager.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens the system Wireless Debugging screen when possible.
 *
 * Device finding (Nothing Phone (3a), Android 16, NothingSettings):
 * - There is NO exported `Settings$WirelessDebuggingActivity`
 *   (verified via `dumpsys package com.android.settings` and a failed
 *   explicit launch) and no Intent action that targets Wireless Debugging.
 * - The fragment `com.android.settings.development.WirelessDebuggingFragment`
 *   exists inside the Settings APK, so as a best effort this navigator asks
 *   Development Settings to show that fragment via the
 *   `:settings:show_fragment` extra. OEMs are free to ignore the extra, in
 *   which case plain Developer Options opens — hence the [OpenResult].
 *
 * Every launch is wrapped in try/catch because none of this is contractual
 * across OEMs or Android versions.
 */
object WirelessDebuggingNavigator {

    /** Extra honored by AOSP Settings hosts to show a nested fragment. */
    const val SHOW_FRAGMENT_EXTRA = ":settings:show_fragment"

    /** Fragment found in the on-device Settings APK (NothingSettings). */
    const val WIRELESS_DEBUGGING_FRAGMENT =
        "com.android.settings.development.WirelessDebuggingFragment"

    enum class OpenResult {
        /** Direct intent dispatched (system may still fall back to Dev Options). */
        DIRECT,

        /** Fell back to plain Developer Options. */
        DEV_SETTINGS,

        /** Nothing could be opened. */
        FAILED
    }

    /**
     * App -> "Enable Wireless Debugging": tries to open the Wireless
     * Debugging screen directly, otherwise Developer Options.
     */
    fun openWirelessDebugging(context: Context): OpenResult {
        try {
            context.startActivity(newTaskFlags(context).let { flags ->
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .putExtra(SHOW_FRAGMENT_EXTRA, WIRELESS_DEBUGGING_FRAGMENT)
                    .apply { if (flags != 0) addFlags(flags) }
            })
            return OpenResult.DIRECT
        } catch (_: Exception) {
            // Fall through to plain Developer Options.
        }
        return if (openDeveloperSettings(context)) OpenResult.DEV_SETTINGS else OpenResult.FAILED
    }

    /** Plain Developer Options fallback. Returns false if nothing opened. */
    fun openDeveloperSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            val flags = newTaskFlags(context)
            if (flags != 0) intent.addFlags(flags)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun newTaskFlags(context: Context): Int =
        if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
}
