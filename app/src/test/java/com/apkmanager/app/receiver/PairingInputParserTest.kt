package com.apkmanager.app.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingInputParserTest {

    @Test
    fun codeOnlyLeavesPortToDiscovery() {
        assertEquals(null to "482916", PairingNotificationReceiver.parseInput("482916"))
    }

    @Test
    fun portAndCodeInAnyCommonFormat() {
        assertEquals(37215 to "482916", PairingNotificationReceiver.parseInput("37215 482916"))
        assertEquals(37215 to "482916", PairingNotificationReceiver.parseInput("37215:482916"))
        assertEquals(37215 to "482916", PairingNotificationReceiver.parseInput("37215, 482916"))
        assertEquals(37215 to "482916", PairingNotificationReceiver.parseInput("192.168.1.20:37215 482916"))
    }

    @Test
    fun rejectsInputWithoutSixDigitCode() {
        assertNull(PairingNotificationReceiver.parseInput("37215"))
        assertNull(PairingNotificationReceiver.parseInput("hello"))
    }
}
