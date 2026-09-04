package ru.runetchecker.domain

object CheckPolicy {
    fun maxAttempts(vpnActive: Boolean): Int = if (vpnActive) VPN_MAX_ATTEMPTS else 1

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
}
