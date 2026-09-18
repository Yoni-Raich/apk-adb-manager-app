package com.apkmanager.app.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbDiscoveryTest {

    @Test
    fun testIsValidAdbPort() {
        assertFalse(AdbServiceDiscovery.isValidAdbPort(0))
        assertFalse(AdbServiceDiscovery.isValidAdbPort(80))
        assertFalse(AdbServiceDiscovery.isValidAdbPort(1023))
        assertTrue(AdbServiceDiscovery.isValidAdbPort(1024))
        assertTrue(AdbServiceDiscovery.isValidAdbPort(37215))
        assertTrue(AdbServiceDiscovery.isValidAdbPort(65535))
        assertFalse(AdbServiceDiscovery.isValidAdbPort(65536))
        assertFalse(AdbServiceDiscovery.isValidAdbPort(-1))
    }

    @Test
    fun testMergeServiceUpsertsByName() {
        val first = DiscoveredAdbService("adb-ABC", "192.168.1.10", 40001)
        val merged = AdbServiceDiscovery.mergeService(emptyList(), first)
        assertEquals(listOf(first), merged)

        // Same instance name re-announced with a new (dynamic) port replaces.
        val updated = DiscoveredAdbService("adb-ABC", "192.168.1.10", 41235)
        assertEquals(
            listOf(updated),
            AdbServiceDiscovery.mergeService(merged, updated)
        )
    }

    @Test
    fun testMergeServiceKeepsMultipleSorted() {
        val b = DiscoveredAdbService("adb-B", null, 40002)
        val a = DiscoveredAdbService("adb-A", "192.168.1.11", 40001)
        val merged = AdbServiceDiscovery.mergeService(listOf(b), a)
        assertEquals(listOf(a, b), merged)
    }

    @Test
    fun testRemoveService() {
        val a = DiscoveredAdbService("adb-A", null, 40001)
        val b = DiscoveredAdbService("adb-B", null, 40002)
        assertEquals(listOf(b), AdbServiceDiscovery.removeService(listOf(a, b), "adb-A"))
        // Removing an unknown name leaves the list untouched.
        assertEquals(listOf(a, b), AdbServiceDiscovery.removeService(listOf(a, b), "adb-Z"))
    }

    @Test
    fun testServiceTypeConstants() {
        // Documented mDNS service types; trailing dot is the NsdManager form.
        assertEquals("_adb-tls-connect._tcp.", AdbServiceDiscovery.SERVICE_TYPE_CONNECT)
        assertEquals("_adb-tls-pairing._tcp.", AdbServiceDiscovery.SERVICE_TYPE_PAIRING)
    }
}
