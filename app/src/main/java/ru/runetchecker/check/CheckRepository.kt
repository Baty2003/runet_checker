package ru.runetchecker.check

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import ru.runetchecker.domain.CheckCoordinator
import ru.runetchecker.domain.CheckResult
import ru.runetchecker.domain.NetworkChecker
import ru.runetchecker.domain.NetworkClassifier
import ru.runetchecker.domain.NetworkState
import ru.runetchecker.domain.PROBE_TIMEOUT_SECONDS
import ru.runetchecker.domain.VpnStatus
import ru.runetchecker.notify.StatusNotifier
import ru.runetchecker.probe.DefaultProbeTargets
import ru.runetchecker.probe.HttpsResourceProbe
import ru.runetchecker.probe.SharedHttpClient
import ru.runetchecker.settings.SettingsStore
import ru.runetchecker.ui.CheckUiState
import ru.runetchecker.vpn.VpnDetector
import kotlin.coroutines.cancellation.CancellationException

class CheckRepository(
    context: Context,
    private val settings: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val client = SharedHttpClient.instance
    private val vpnDetector = VpnDetector(appContext, client)
    private val coordinator = CheckCoordinator(
        checker = NetworkChecker(
            probe = HttpsResourceProbe(client),
            targets = DefaultProbeTargets.targets,
            classifier = NetworkClassifier(),
        ),
        vpnActive = vpnDetector::isVpnActive,
        lookupCountryIso = vpnDetector::lookupCountryIso,
    )
    private val notifier = StatusNotifier(appContext, settings)
    @Volatile private var activeCheckJob: Job? = null

    private val _uiState = MutableStateFlow(CheckUiState())
    val uiState: StateFlow<CheckUiState> = _uiState.asStateFlow()

    fun placeholderOngoingNotification() = notifier.ongoingNotification(_uiState.value)

    fun isVpnActive(): Boolean = vpnDetector.isVpnActive()

    fun interruptActiveCheck() {
        activeCheckJob?.cancel()
    }

    fun cancelOngoing() = notifier.cancelOngoing()

    fun refreshOngoing() {
        publishOngoingIfNeeded()
    }

    private fun publishOngoingIfNeeded() {
        if (settings.autoCheckEnabled()) {
            notifier.publishOngoing(_uiState.value)
        }
    }

    suspend fun runCheck(auto: Boolean = false) {
        mutex.withLock {
            _uiState.update {
                it.copy(
                    isChecking = true,
                    errorMessage = null,
                    countdownSeconds = PROBE_TIMEOUT_SECONDS,
                    checkAttempt = 1,
                    maxAttempts = 1,
                    autoCheck = auto,
                )
            }
            publishOngoingIfNeeded()
            coroutineScope {
                activeCheckJob = coroutineContext[Job]
                val ticker = launch {
                    while (isActive) {
                        delay(1000)
                        _uiState.update { state ->
                            if (!state.isChecking) {
                                state
                            } else {
                                state.copy(countdownSeconds = (state.countdownSeconds - 1).coerceAtLeast(0))
                            }
                        }
                        publishOngoingIfNeeded()
                    }
                }
                try {
                    val (result, vpn) = coordinator.run { attempt, maxAttempts ->
                        _uiState.update {
                            it.copy(
                                checkAttempt = attempt,
                                maxAttempts = maxAttempts,
                                countdownSeconds = PROBE_TIMEOUT_SECONDS,
                            )
                        }
                        publishOngoingIfNeeded()
                    }
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            result = result,
                            vpn = vpn,
                            countdownSeconds = 0,
                        )
                    }
                    notifier.onCheckFinished(result, vpn, showOngoing = settings.autoCheckEnabled())
                } catch (cancelled: CancellationException) {
                    _uiState.update { it.copy(isChecking = false, countdownSeconds = 0) }
                    throw cancelled
                } catch (error: Exception) {
                    val unknown = CheckResult(state = NetworkState.UNKNOWN, probes = emptyList())
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            result = unknown,
                            errorMessage = error.message,
                            countdownSeconds = 0,
                        )
                    }
                    notifier.onCheckFinished(
                        unknown,
                        _uiState.value.vpn ?: VpnStatus(active = false),
                        showOngoing = settings.autoCheckEnabled(),
                    )
                } finally {
                    ticker.cancel()
                    activeCheckJob = null
                }
            }
        }
    }
}
