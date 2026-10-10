package ru.runetchecker.domain

object CheckPolicy {
    fun maxAttempts(
        vpnActive: Boolean,
        configuredMax: Int = CHECK_ATTEMPTS_DEFAULT,
        lastCompletedState: NetworkState? = null,
    ): Int {
        if (!vpnActive) return 1
        if (lastCompletedState == NetworkState.OFFLINE) return 1
        return configuredMax.coerceIn(CHECK_ATTEMPTS_MIN, CHECK_ATTEMPTS_MAX)
    }

    fun hasBothGroups(result: CheckResult): Boolean =
        result.globalReached >= 1 && result.whitelistReached >= 1

    fun shouldRetry(
        vpnActive: Boolean,
        result: CheckResult,
        attempt: Int,
        maxAttempts: Int,
    ): Boolean {
        if (!vpnActive) return false
        if (attempt >= maxAttempts) return false
        return !hasBothGroups(result)
    }

    fun watchdogDeadlineMs(maxAttempts: Int, probeTimeoutSeconds: Int): Long =
        (maxAttempts.coerceAtLeast(1) + 1L) * probeTimeoutSeconds.coerceAtLeast(1) * 1000L
}
