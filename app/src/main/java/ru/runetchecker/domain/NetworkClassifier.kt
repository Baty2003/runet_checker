package ru.runetchecker.domain

class NetworkClassifier {
    fun classify(results: List<ProbeResult>): NetworkState {
        if (results.isEmpty()) return NetworkState.UNKNOWN

        val globalOk = results.any { it.target.group == ProbeGroup.GLOBAL && it.reachable }
        val whitelistOk = results.any { it.target.group == ProbeGroup.WHITELIST && it.reachable }

        return when {
            globalOk -> NetworkState.ONLINE
            whitelistOk -> NetworkState.WHITELIST
            else -> NetworkState.OFFLINE
        }
    }
}
