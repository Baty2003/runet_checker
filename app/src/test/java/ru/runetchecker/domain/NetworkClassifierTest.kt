package ru.runetchecker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkClassifierTest {

    private val classifier = NetworkClassifier()

    @Test
    fun allGlobalAndWhitelistReachable_isOnline() {
        assertEquals(
            NetworkState.ONLINE,
            classifier.classify(probes(globalReachable = 3, whitelistReachable = 3)),
        )
    }

    @Test
    fun oneGlobalReachable_isOnline() {
        assertEquals(
            NetworkState.ONLINE,
            classifier.classify(probes(globalReachable = 1, whitelistReachable = 3)),
        )
    }

    @Test
    fun oneGlobalAndNoWhitelist_isOnline() {
        assertEquals(
            NetworkState.ONLINE,
            classifier.classify(probes(globalReachable = 1, whitelistReachable = 0)),
        )
    }

    @Test
    fun noGlobalAllWhitelist_isWhitelist() {
        assertEquals(
            NetworkState.WHITELIST,
            classifier.classify(probes(globalReachable = 0, whitelistReachable = 3)),
        )
    }

    @Test
    fun noGlobalOneWhitelist_isWhitelist() {
        assertEquals(
            NetworkState.WHITELIST,
            classifier.classify(probes(globalReachable = 0, whitelistReachable = 1)),
        )
    }

    @Test
    fun nobodyReachable_isOffline() {
        assertEquals(
            NetworkState.OFFLINE,
            classifier.classify(probes(globalReachable = 0, whitelistReachable = 0)),
        )
    }

    @Test
    fun emptyResults_isUnknown() {
        assertEquals(NetworkState.UNKNOWN, classifier.classify(emptyList()))
    }

    private fun probes(globalReachable: Int, whitelistReachable: Int): List<ProbeResult> {
        val global = listOf("google.com", "cloudflare.com", "wikipedia.org").mapIndexed { index, host ->
            ProbeResult(ProbeTarget(host, ProbeGroup.GLOBAL), reachable = index < globalReachable)
        }
        val whitelist = listOf("max.ru", "yandex.ru", "vk.ru").mapIndexed { index, host ->
            ProbeResult(ProbeTarget(host, ProbeGroup.WHITELIST), reachable = index < whitelistReachable)
        }
        return global + whitelist
    }
}
