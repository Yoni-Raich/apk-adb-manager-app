package com.apkmanager.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DataModelTest {

    @Test
    fun testPackageInfoDisplayNameAndVersion() {
        val pkg = PackageInfo(
            packageName = "com.google.android.youtube",
            versionName = "19.34.42",
            versionCode = "1545678900"
        )
        assertEquals("Youtube", pkg.displayName)
        assertEquals("19.34.42 (1545678900)", pkg.versionDisplay)
    }

    @Test
    fun testPackageInfoSinglePartName() {
        val pkg = PackageInfo(
            packageName = "settings",
            versionName = "1.0"
        )
        assertEquals("Settings", pkg.displayName)
        assertEquals("1.0", pkg.versionDisplay)
    }

    @Test
    fun testConnectionStateStatusText() {
        assertEquals("Disconnected", ConnectionState.Disconnected.statusText)
        assertEquals("Connecting to port 5555...", ConnectionState.Connecting(5555).statusText)
        assertEquals("Connected (port 5555)", ConnectionState.Connected(5555).statusText)
        assertEquals("Error: Socket timeout", ConnectionState.Error("Socket timeout").statusText)
    }
}
