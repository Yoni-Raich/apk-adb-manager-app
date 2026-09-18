package com.apkmanager.app.data

/**
 * Represents the current state of the ADB connection.
 */
sealed class ConnectionState {
    /** No connection has been established. */
    data object Disconnected : ConnectionState()

    /** Currently pairing with the ADB daemon. */
    data class Pairing(val message: String = "Pairing...") : ConnectionState()

    /** Currently connecting to the ADB daemon. */
    data class Connecting(val port: Int) : ConnectionState()

    /** Successfully connected to the ADB daemon. */
    data class Connected(val port: Int) : ConnectionState()

    /** An error occurred during connection or pairing. */
    data class Error(val message: String) : ConnectionState()

    val isConnected: Boolean get() = this is Connected
    val isLoading: Boolean get() = this is Pairing || this is Connecting

    val statusText: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Pairing -> message
            is Connecting -> "Connecting to port $port..."
            is Connected -> "Connected (port $port)"
            is Error -> "Error: $message"
        }
}
