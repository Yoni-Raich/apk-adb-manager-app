package com.apkmanager.app.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Discovered ADB-over-Wi-Fi endpoint (mDNS service).
 *
 * @param serviceName mDNS instance name, e.g. `adb-00152154B002517-X3NiBU`.
 * @param host Resolved host address as text, or null while unresolved.
 * @param port TCP port from the SRV record. Android assigns this dynamically.
 */
data class DiscoveredAdbService(
    val serviceName: String,
    val host: String?,
    val port: Int
)

/**
 * Discovers ADB Wireless Debugging endpoints via Android NSD (mDNS/DNS-SD).
 *
 * - [SERVICE_TYPE_CONNECT] (`_adb-tls-connect._tcp`) advertises the normal
 *   ADB connection endpoint: resolving it yields the dynamic connection
 *   port, so the app never has to hardcode or guess it.
 * - [SERVICE_TYPE_PAIRING] (`_adb-tls-pairing._tcp`) is only advertised
 *   while the system "Pair device with pairing code" UI is active. It is
 *   kept separate on purpose: the pairing port must never be used as the
 *   connection port.
 *
 * Lifecycle rules (callers must honor these):
 * - call [startDiscovery] in `onResume`, [stopDiscovery] in `onPause`;
 * - discovery is idempotent: starting twice for the same type is a no-op;
 * - each session and service has its own listener; late callbacks are ignored;
 * - resolution failures (stale records) are ignored, not fatal;
 * - Wi-Fi off / discovery start failures surface via [error] instead of
 *   crashing.
 *
 * Connection advertisements are checked against local interfaces and probed
 * on loopback before publication. Pairing advertisements are never probed.
 */
class AdbServiceDiscovery(context: Context) {

    companion object {
        private const val TAG = "AdbServiceDiscovery"

        const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp."
        const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp."

        /** Ports outside the user range can never be valid ADB ports. */
        fun isValidAdbPort(port: Int): Boolean = port in 1024..65535

        /**
         * Pure merge helper (unit-testable): upserts [service] into [current]
         * by instance name and returns the list sorted by name.
         */
        fun mergeService(
            current: List<DiscoveredAdbService>,
            service: DiscoveredAdbService
        ): List<DiscoveredAdbService> {
            return (current.filterNot { it.serviceName == service.serviceName } + service)
                .sortedBy { it.serviceName }
        }

        /** Pure helper (unit-testable): removes the instance [serviceName]. */
        fun removeService(
            current: List<DiscoveredAdbService>,
            serviceName: String
        ): List<DiscoveredAdbService> {
            return current.filterNot { it.serviceName == serviceName }
        }
    }

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _services = MutableStateFlow<List<DiscoveredAdbService>>(emptyList())
    val services: StateFlow<List<DiscoveredAdbService>> = _services.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val lock = Any()
    private var activeServiceType: String? = null
    private var generation = 0L
    private var activeListener: NsdManager.DiscoveryListener? = null
    private val serviceTokens = mutableMapOf<String, Any>()
    private val infoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun isCurrent(session: Long, name: String, token: Any): Boolean =
        generation == session && activeServiceType != null && serviceTokens[name] === token

    private fun publish(serviceInfo: NsdServiceInfo, session: Long, token: Any) {
        val service = DiscoveredAdbService(
            serviceInfo.serviceName, resolveHostAddress(serviceInfo), serviceInfo.port
        )
        if (!isValidAdbPort(service.port)) return
        val type = synchronized(lock) {
            if (!isCurrent(session, service.serviceName, token)) return
            activeServiceType
        }
        probeScope.launch {
            val reachable = type != SERVICE_TYPE_CONNECT || isReachableLocalService(service)
            synchronized(lock) {
                if (!isCurrent(session, service.serviceName, token)) return@synchronized
                _services.value = if (reachable) mergeService(_services.value, service)
                    else removeService(_services.value, service.serviceName)
                Log.d(TAG, "Resolved ${service.serviceName} host=${service.host} port=${service.port} reachable=$reachable")
            }
        }
    }

    private fun isReachableLocalService(service: DiscoveredAdbService): Boolean {
        return try {
            val localAddresses = NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }.map { it.hostAddress?.substringBefore('%') }
            if (service.host?.substringBefore('%') !in localAddresses) return false
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", service.port), 1000) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun createResolveListener(session: Long, token: Any) = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Stale advertisement; a fresh announce will arrive if still live.
            Log.d(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            publish(serviceInfo, session, token)
        }
    }

    private fun createDiscoveryListener(session: Long) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "Discovery started: $serviceType")
            synchronized(lock) { if (generation == session) _error.value = null }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped: $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            synchronized(lock) {
                if (generation != session) return
                val wanted = activeServiceType ?: return
                if (serviceInfo.serviceType.trimEnd('.') != wanted.trimEnd('.')) return
                if (serviceTokens.containsKey(serviceInfo.serviceName)) return
                val token = Any()
                serviceTokens[serviceInfo.serviceName] = token
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val callback = object : NsdManager.ServiceInfoCallback {
                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                synchronized(lock) {
                                    if (generation != session || infoCallbacks[serviceInfo.serviceName] !== this) return
                                    serviceTokens.remove(serviceInfo.serviceName)
                                    infoCallbacks.remove(serviceInfo.serviceName)
                                    _error.value = discoveryErrorMessage(errorCode)
                                }
                            }
                            override fun onServiceUpdated(info: NsdServiceInfo) {
                                // A new update invalidates any older in-flight probe.
                                val updateToken = synchronized(lock) {
                                    if (generation != session || infoCallbacks[serviceInfo.serviceName] !== this) return
                                    Any().also { serviceTokens[serviceInfo.serviceName] = it }
                                }
                                publish(info, session, updateToken)
                            }
                            override fun onServiceLost() {
                                synchronized(lock) {
                                    if (generation != session || infoCallbacks[serviceInfo.serviceName] !== this) return
                                    serviceTokens[serviceInfo.serviceName] = Any()
                                    _services.value = removeService(_services.value, serviceInfo.serviceName)
                                }
                            }
                            override fun onServiceInfoCallbackUnregistered() = Unit
                        }
                        infoCallbacks[serviceInfo.serviceName] = callback
                        nsdManager.registerServiceInfoCallback(serviceInfo, java.util.concurrent.Executor { it.run() }, callback)
                    } else {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(serviceInfo, createResolveListener(session, token))
                    }
                } catch (e: Exception) {
                    serviceTokens.remove(serviceInfo.serviceName)
                    infoCallbacks.remove(serviceInfo.serviceName)
                    Log.d(TAG, "Resolution failed for ${serviceInfo.serviceName}", e)
                }
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(lock) {
                if (generation != session) return
                serviceTokens.remove(serviceInfo.serviceName)
                if (Build.VERSION.SDK_INT >= 34) {
                    infoCallbacks.remove(serviceInfo.serviceName)?.let {
                        runCatching { nsdManager.unregisterServiceInfoCallback(it) }
                    }
                }
                _services.value = removeService(_services.value, serviceInfo.serviceName)
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "Start discovery failed: $serviceType code=$errorCode")
            synchronized(lock) {
                if (generation != session) return
                stopDiscoveryLocked()
                _error.value = discoveryErrorMessage(errorCode)
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "Stop discovery failed: $serviceType code=$errorCode")
        }
    }

    /**
     * Starts discovery for [serviceType] ([SERVICE_TYPE_CONNECT] or
     * [SERVICE_TYPE_PAIRING]). No-op if already discovering that type.
     */
    fun startDiscovery(serviceType: String = SERVICE_TYPE_CONNECT) {
        synchronized(lock) {
            if (activeServiceType == serviceType) return
            stopDiscoveryLocked()
            _services.value = emptyList()
            _error.value = null
            val listener = createDiscoveryListener(generation)
            activeListener = listener
            activeServiceType = serviceType
            try {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
            } catch (e: Exception) {
                stopDiscoveryLocked()
                Log.w(TAG, "discoverServices threw", e)
                _error.value = "Could not start discovery. Check that Wi-Fi is on."
            }
        }
    }

    /** Stops discovery if running. Safe to call when idle. */
    fun stopDiscovery() {
        synchronized(lock) { stopDiscoveryLocked() }
    }

    private fun stopDiscoveryLocked() {
        generation++
        val listener = activeListener
        activeListener = null
        activeServiceType = null
        serviceTokens.clear()
        _services.value = emptyList()
        probeScope.coroutineContext.cancelChildren()
        if (Build.VERSION.SDK_INT >= 34) {
            infoCallbacks.values.forEach { runCatching { nsdManager.unregisterServiceInfoCallback(it) } }
        }
        infoCallbacks.clear()
        if (listener == null) return
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Listener was not registered; nothing to stop.
        } catch (e: Exception) {
            Log.d(TAG, "stopServiceDiscovery threw", e)
        } finally {
            activeServiceType = null
        }
    }

    private fun resolveHostAddress(serviceInfo: NsdServiceInfo): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            serviceInfo.host?.hostAddress
        }
    }

    private fun discoveryErrorMessage(errorCode: Int): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            errorCode == NsdManager.FAILURE_INTERNAL_ERROR
        ) {
            "Discovery unavailable. Check that Wi-Fi is on and connected."
        } else {
            "Discovery unavailable (code $errorCode). Check that Wi-Fi is on."
        }
    }
}
