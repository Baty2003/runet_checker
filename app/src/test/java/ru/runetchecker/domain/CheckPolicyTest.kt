package ru.runetchecker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPolicyTest {

    @Test
    fun maxAttempts_oneWithoutVpn() {
        assertEquals(1, CheckPolicy.maxAttempts(vpnActive = false, configuredMax = 5))
    }

    @Test
    fun maxAttempts_usesSettingWhenVpnOn() {
        assertEquals(2, CheckPolicy.maxAttempts(vpnActive = true, configuredMax = 2))
        assertEquals(5, CheckPolicy.maxAttempts(vpnActive = true, configuredMax = 9))
        assertEquals(1, CheckPolicy.maxAttempts(vpnActive = true, configuredMax = 0))
    }

    @Test
    fun maxAttempts_oneAfterOfflineEvenWithVpn() {
        assertEquals(
            1,
            CheckPolicy.maxAttempts(
                vpnActive = true,
                configuredMax = 5,
                lastCompletedState = NetworkState.OFFLINE,
            ),
        )
        assertEquals(
            4,
            CheckPolicy.maxAttempts(
                vpnActive = true,
                configuredMax = 4,
                lastCompletedState = NetworkState.ONLINE,
            ),
        )
    }

    @Test
    fun hasBothGroups_requiresOneEach() {
        assertFalse(CheckPolicy.hasBothGroups(result(global = 1, whitelist = 0)))
        assertFalse(CheckPolicy.hasBothGroups(result(global = 0, whitelist = 1)))
        assertTrue(CheckPolicy.hasBothGroups(result(global = 1, whitelist = 1)))
    }

    @Test
    fun shouldRetry_stopsWhenBothGroupsHit() {
        assertFalse(
            CheckPolicy.shouldRetry(
                vpnActive = true,
                result = result(global = 1, whitelist = 1),
                attempt = 1,
                maxAttempts = 3,
            ),
        )
    }

    @Test
    fun shouldRetry_continuesIfAGroupIsMissing() {
        assertTrue(
            CheckPolicy.shouldRetry(
                vpnActive = true,
                result = result(global = 1, whitelist = 0),
                attempt = 1,
                maxAttempts = 3,
            ),
        )
        assertTrue(
            CheckPolicy.shouldRetry(
                vpnActive = true,
                result = result(global = 0, whitelist = 1),
                attempt = 2,
                maxAttempts = 3,
            ),
        )
    }

    @Test
    fun shouldRetry_neverWithoutVpn() {
        assertFalse(
            CheckPolicy.shouldRetry(
                vpnActive = false,
                result = result(global = 0, whitelist = 0),
                attempt = 1,
                maxAttempts = 5,
            ),
        )
    }

    @Test
    fun shouldRetry_stopsAtMaxAttempts() {
        assertFalse(
            CheckPolicy.shouldRetry(
                vpnActive = true,
                result = result(global = 0, whitelist = 0),
                attempt = 3,
                maxAttempts = 3,
            ),
        )
    }

    @Test
    fun watchdogDeadline_includesGeoSlack() {
        assertEquals(20_000L, CheckPolicy.watchdogDeadlineMs(maxAttempts = 3, probeTimeoutSeconds = 5))
        assertEquals(10_000L, CheckPolicy.watchdogDeadlineMs(maxAttempts = 1, probeTimeoutSeconds = 5))
    }

    private fun result(global: Int, whitelist: Int): CheckResult {
        val probes = buildList {
            repeat(2) { index ->
                add(
                    ProbeResult(
                        ProbeTarget("g$index.com", ProbeGroup.GLOBAL),
                        reachable = index < global,
                    ),
                )
            }
            repeat(2) { index ->
                add(
                    ProbeResult(
                        ProbeTarget("w$index.ru", ProbeGroup.WHITELIST),
                        reachable = index < whitelist,
                    ),
                )
            }
        }
        return CheckResult(state = NetworkClassifier().classify(probes), probes = probes)
    }
}
