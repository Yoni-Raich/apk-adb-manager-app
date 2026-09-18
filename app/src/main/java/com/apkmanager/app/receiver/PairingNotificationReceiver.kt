package com.apkmanager.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.apkmanager.app.ApkManagerApplication
import com.apkmanager.app.util.PairingNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PairingNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAIR_INPUT = "com.apkmanager.app.ACTION_PAIR_INPUT"
        private const val TAG = "PairingReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PAIR_INPUT) {
            val bundle = RemoteInput.getResultsFromIntent(intent) ?: return
            val input = bundle.getCharSequence(PairingNotificationHelper.KEY_PAIRING_INPUT)?.toString()?.trim() ?: return

            Log.d(TAG, "Received pairing input")

            val app = context.applicationContext as ApkManagerApplication
            val parsed = parseInput(input)

            if (parsed == null) {
                PairingNotificationHelper.showPairingNotification(
                    context,
                    statusMessage = "Could not parse input '$input'. Please enter: Port Code (e.g. 37215 123456)"
                )
                return
            }

            val (port, code) = parsed

            PairingNotificationHelper.showPairingNotification(
                context,
                statusMessage = "Pairing to port $port..."
            )

            val pendingResult = goAsync()

            scope.launch {
                try {
                    val success = app.adbRepository.pair(port, code)
                    if (success) {
                        PairingNotificationHelper.showPairingNotification(
                            context,
                            statusMessage = "Device paired successfully! Tap to open app.",
                            isSuccess = true
                        )
                    } else {
                        PairingNotificationHelper.showPairingNotification(
                            context,
                            statusMessage = "Pairing failed. Please make sure the dialog is still open in Settings and try again."
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Pairing error", e)
                    PairingNotificationHelper.showPairingNotification(
                        context,
                        statusMessage = "Error: ${e.message}"
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun parseInput(input: String): Pair<Int, String>? {
        // Handle formats like:
        // "37215 123456"
        // "37215, 123456"
        // "192.168.1.20:37215 123456"
        // "37215:123456"
        val clean = input.replace(",", " ").replace(":", " ")
        val tokens = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }

        var port: Int? = null
        var code: String? = null

        for (token in tokens) {
            // Check if token is 6 digits (pairing code)
            if (token.length == 6 && token.all { it.isDigit() } && code == null) {
                code = token
            } else if (token.all { it.isDigit() } && token.length in 4..5 && port == null) {
                port = token.toIntOrNull()
            }
        }

        // Fallback: if two numbers provided in order [port, code]
        if (tokens.size >= 2) {
            if (port == null) {
                val p = tokens[0].filter { it.isDigit() }.toIntOrNull()
                if (p != null && p in 1024..65535) port = p
            }
            if (code == null) {
                val c = tokens[1].filter { it.isDigit() }
                if (c.length == 6) code = c
            }
        }

        return if (port != null && code != null) {
            Pair(port, code)
        } else {
            null
        }
    }
}
