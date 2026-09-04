package ru.runetchecker.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalConnectivityTest {

    @Test
    fun wifiAndMobileOff_pausesChecks() {
        assertTrue(
            shouldPauseChecks(
                airplaneMode = false,
                wifiEnabled = false,
                mobileDataEnabled = false,
                ethernetConnected = false,
            ),
        )
    }

    @Test
    fun airplaneMode_pausesEvenIfWifiIsOn() {
        assertTrue(
            shouldPauseChecks(
                airplaneMode = true,
                wifiEnabled = true,
                mobileDataEnabled = false,
                ethernetConnected = false,
            ),
        )
    }

    @Test
    fun wifiToggleOn_runsChecks() {
        assertFalse(
            shouldPauseChecks(
                airplaneMode = false,
                wifiEnabled = true,
                mobileDataEnabled = false,
                ethernetConnected = false,
            ),
        )
    }

    @Test
    fun mobileDataOn_runsChecks() {
        assertFalse(
            shouldPauseChecks(
                airplaneMode = false,
                wifiEnabled = false,
                mobileDataEnabled = true,
                ethernetConnected = false,
            ),
        )
    }

    @Test
    fun ethernet_runsChecks() {
        assertFalse(
            shouldPauseChecks(
                airplaneMode = false,
                wifiEnabled = false,
                mobileDataEnabled = false,
                ethernetConnected = true,
            ),
        )
    }
}
