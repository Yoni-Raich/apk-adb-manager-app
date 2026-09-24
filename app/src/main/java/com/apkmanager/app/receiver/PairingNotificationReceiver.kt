package com.apkmanager.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.apkmanager.app.ApkManagerApplication
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.util.PairingNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the inline reply on the pairing notification. The user types only the
 * 6-digit code; the pairing port is found by mDNS. "port code" is still accepted.
 */
class PairingNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAIR_INPUT = "com.apkmanager.app.ACTION_PAIR_INPUT"
        private const val TAG = "PairingReceiver"

        /**
         * Parses a reply into (port or null, code). Accepts "482916", "37215 482916",
         * "37215:482916" and "192.168.1.20:37215 482916".
         */
        fun parseInput(input: String): Pair<Int?, String>? {
            val tokens = input.split(Regex("[\\s,:]+")).filter { it.isNotBlank() && it.all(Char::isDigit) }
            val code = tokens.lastOrNull { it.length == 6 } ?: return null
            val port = tokens.firstOrNull { it != code && it.length in 4..5 }?.toIntOrNull()
                ?.takeIf { it in 1024..65535 }
            return port to code
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAIR_INPUT) return

        val input = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PairingNotificationHelper.KEY_PAIRING_INPUT)?.toString()?.trim()
            ?: return
        val parsed = parseInput(input)
        if (parsed == null) {
            PairingNotificationHelper.showPairingNotification(
                context,
                statusMessage = "Reply with the 6-digit code shown in Settings."
            )
            return
        }

        val (port, code) = parsed
        val adb = (context.applicationContext as ApkManagerApplication).adbRepository
        PairingNotificationHelper.showPairingNotification(context, statusMessage = "Pairing…")

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (val outcome = adb.pairAndConnect(code, port)) {
                    AdbRepository.PairOutcome.Connected -> PairingNotificationHelper.showPairingNotification(
                        context, statusMessage = "Paired and connected. You can go back to APK Manager.", isSuccess = true
                    )
                    AdbRepository.PairOutcome.PairedNotConnected -> PairingNotificationHelper.showPairingNotification(
                        context, statusMessage = "Paired. Open APK Manager to finish connecting.", isSuccess = true
                    )
                    is AdbRepository.PairOutcome.Failed -> PairingNotificationHelper.showPairingNotification(
                        context, statusMessage = outcome.message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pairing error", e)
                PairingNotificationHelper.showPairingNotification(context, statusMessage = "Pairing failed: ${e.message}")
            } finally {
                adb.pairingDiscovery.stopDiscovery()
                pendingResult.finish()
            }
        }
    }
}
