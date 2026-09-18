package com.apkmanager.app.adb

import android.util.Log
import com.flyfishxu.kadb.Kadb

/**
 * Implements the ADB Wireless Debugging pairing protocol.
 */
class AdbPairing {

    companion object {
        private const val TAG = "AdbPairing"

        /**
         * Pairs with the local ADB daemon using the wireless debugging pairing protocol.
         *
         * @param host The host to connect to (usually "127.0.0.1")
         * @param port The pairing port from Wireless Debugging settings
         * @param pairingCode The 6-digit pairing code from Wireless Debugging settings
         * @return PairingResult
         */
        suspend fun pair(
            host: String = "127.0.0.1",
            port: Int,
            pairingCode: String
        ): PairingResult {
            return try {
                Log.d(TAG, "Starting pairing with $host:$port")

                Kadb.pair(host, port, pairingCode, "apk-manager")
                Log.d(TAG, "Pairing completed successfully")
                PairingResult.Success
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed with exception", e)
                PairingResult.Failure(e.message ?: "Unknown pairing error")
            }
        }
    }

    /**
     * Result of a pairing operation.
     */
    sealed class PairingResult {
        data object Success : PairingResult()
        data class Failure(val error: String) : PairingResult()
    }
}
