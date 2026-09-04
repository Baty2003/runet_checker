package ru.runetchecker.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class CheckCoordinator(
    private val checker: NetworkChecker,
    private val vpnActive: () -> Boolean,
    private val lookupCountryIso: suspend () -> String?,
) {
    suspend fun run(onAttempt: (attempt: Int, maxAttempts: Int) -> Unit): Pair<CheckResult, VpnStatus> {
        val vpnOn = vpnActive()
        val maxAttempts = CheckPolicy.maxAttempts(vpnOn)
        return coroutineScope {
            val geoDeferred = if (vpnOn) async { lookupCountryIso() } else null
            var last = CheckResult(state = NetworkState.UNKNOWN, probes = emptyList())
            for (attempt in 1..maxAttempts) {
                coroutineContext.ensureActive()
                onAttempt(attempt, maxAttempts)
                last = checker.check()
                if (!CheckPolicy.shouldRetry(vpnOn, last, attempt, maxAttempts)) {
                    break
                }
            }
            val vpn = if (vpnOn) {
                VpnStatus(active = true, countryCode = geoDeferred?.await())
            } else {
                VpnStatus(active = false)
            }
            last to vpn
        }
    }
}
