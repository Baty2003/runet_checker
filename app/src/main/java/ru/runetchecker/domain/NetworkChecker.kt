package ru.runetchecker.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

class NetworkChecker(
    private val probe: ResourceProbe,
    private val targets: List<ProbeTarget>,
    private val classifier: NetworkClassifier,
) {
    suspend fun check(): CheckResult {
        if (targets.isEmpty()) {
            return CheckResult(state = NetworkState.UNKNOWN, probes = emptyList())
        }

        val results = coroutineScope {
            targets.map { target ->
                async {
                    try {
                        probe.probe(target)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        ProbeResult(
                            target = target,
                            reachable = false,
                            errorMessage = "checker failed",
                        )
                    }
                }
            }.awaitAll()
        }

        return CheckResult(
            state = classifier.classify(results),
            probes = results,
        )
    }
}
