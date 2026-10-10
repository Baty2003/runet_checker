package ru.runetchecker.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

class CheckCoordinator(
    private val checker: NetworkChecker,
    private val vpnActive: () -> Boolean,
    private val lookupCountryIso: suspend () -> String?,
) {
    suspend fun run(
        maxAttempts: Int,
        probeTimeoutSeconds: Int,
        onAttempt: (attempt: Int, maxAttempts: Int) -> Unit,
    ): Pair<CheckResult, VpnStatus> {
        val vpnOn = vpnActive()
        val attempts = maxAttempts.coerceAtLeast(1)
        val geoTimeoutMs = probeTimeoutSeconds.coerceAtLeast(1) * 1000L
        return coroutineScope {
            val geoDeferred = if (vpnOn) {
                async {
                    withTimeoutOrNull(geoTimeoutMs) { lookupCountryIso() }
                }
            } else {
                null
            }
            var last = CheckResult(state = NetworkState.UNKNOWN, probes = emptyList())
            for (attempt in 1..attempts) {
                coroutineContext.ensureActive()
                onAttempt(attempt, attempts)
                last = checker.check()
                if (!CheckPolicy.shouldRetry(vpnOn, last, attempt, attempts)) {
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