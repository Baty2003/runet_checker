package ru.runetchecker.domain

data class CheckResult(
    val state: NetworkState,
    val probes: List<ProbeResult>,
) {
    val globalReached: Int
        get() = probes.count { it.target.group == ProbeGroup.GLOBAL && it.reachable }

    val globalTotal: Int
        get() = probes.count { it.target.group == ProbeGroup.GLOBAL }

    val whitelistReached: Int
        get() = probes.count { it.target.group == ProbeGroup.WHITELIST && it.reachable }

    val whitelistTotal: Int
        get() = probes.count { it.target.group == ProbeGroup.WHITELIST }
}
