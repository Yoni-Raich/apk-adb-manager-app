package com.apkmanager.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.apkmanager.app.MainActivity
import com.apkmanager.app.receiver.PairingNotificationReceiver

object PairingNotificationHelper {

    const val CHANNEL_ID = "adb_pairing_channel"
    const val NOTIFICATION_ID = 1001
    const val KEY_PAIRING_INPUT = "key_pairing_input"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ADB Pairing"
            val descriptionText = "Notifications for Wireless Debugging pairing"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPairingNotification(context: Context, statusMessage: String? = null, isSuccess: Boolean = false) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Remote input for entering port and pairing code without leaving Settings
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_INPUT)
            .setLabel("Format: Port Code (e.g. 37215 123456)")
            .build()

        // Receiver intent for the action
        val replyIntent = Intent(context, PairingNotificationReceiver::class.java).apply {
            action = PairingNotificationReceiver.ACTION_PAIR_INPUT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Enter Port & Code",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Settings action: best-effort direct Wireless Debugging screen
        // (falls back to Developer Options inside the navigator path —
        // here the extra is simply ignored where unsupported).
        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(
                WirelessDebuggingNavigator.SHOW_FRAGMENT_EXTRA,
                WirelessDebuggingNavigator.WIRELESS_DEBUGGING_FRAGMENT
            )
        val settingsPendingIntent = PendingIntent.getActivity(
            context, 2, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val settingsAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_preferences,
            "Open Settings",
            settingsPendingIntent
        ).build()

        val contentText = statusMessage ?: "1. Open Wireless Debugging in Settings\n2. Tap 'Pair with code'\n3. Pull down this bar & reply with: Port Code"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(if (isSuccess) "Pairing Succeeded!" else "Wireless Pairing Active")
            .setContentText(if (isSuccess) "Device paired successfully. Tap to open app." else contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(!isSuccess)
            .setAutoCancel(isSuccess)
            .setContentIntent(openAppPendingIntent)

        if (!isSuccess) {
            builder.addAction(replyAction)
            builder.addAction(settingsAction)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun dismissNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
