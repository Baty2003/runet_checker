package ru.runetchecker.check

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import ru.runetchecker.domain.CheckCoordinator
import ru.runetchecker.domain.CheckPolicy
import ru.runetchecker.domain.CheckResult
import ru.runetchecker.domain.NetworkChecker
import ru.runetchecker.domain.NetworkClassifier
import ru.runetchecker.domain.NetworkState
import ru.runetchecker.domain.VpnStatus
import ru.runetchecker.monitor.LocalConnectivity
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
    private val vpnDetector = VpnDetector(
        appContext,
        client,
        timeoutSeconds = settings::probeTimeoutSeconds,
    )
    private val coordinator = CheckCoordinator(
        checker = NetworkChecker(
            probe = HttpsResourceProbe(client, timeoutSeconds = settings::probeTimeoutSeconds),
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

    fun stopCheck() {
        interruptActiveCheck()
    }

    fun cancelOngoing() = notifier.cancelOngoing()

    fun showInterfacesOff() {
        applyLocalPause()
    }

    fun refreshOngoing() {
        publishOngoingIfNeeded()
    }

    private fun applyLocalPause() {
        val snapshot = LocalConnectivity.snapshot(appContext)
        _uiState.update {
            it.copy(
                isChecking = false,
                radiosOff = snapshot.shouldPauseChecks && !snapshot.airplaneMode,
                airplaneMode = snapshot.airplaneMode,
                countdownSeconds = 0,
            )
        }
        notifier.suppressAlerts()
        publishOngoingIfNeeded()
    }

    private fun publishOngoingIfNeeded() {
        if (settings.autoCheckEnabled()) {
            notifier.publishOngoing(_uiState.value)
        }
    }

    suspend fun runCheck(auto: Boolean = false) {
        mutex.withLock {
            if (LocalConnectivity.shouldPauseChecks(appContext)) {
                applyLocalPause()
                return
            }
            notifier.resumeAlerts()
            val timeoutSec = settings.probeTimeoutSeconds()
            val maxAttempts = CheckPolicy.maxAttempts(
                vpnActive = vpnDetector.isVpnActive(),
                configuredMax = settings.checkAttempts(),
                lastCompletedState = settings.lastCompletedState(),
            )
            val deadlineMs = CheckPolicy.watchdogDeadlineMs(maxAttempts, timeoutSec)
            _uiState.update {
                it.copy(
                    isChecking = true,
                    radiosOff = false,
                    airplaneMode = false,
                    errorMessage = null,
                    countdownSeconds = timeoutSec,
                    checkAttempt = 1,
                    maxAttempts = maxAttempts,
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
                            if (!state.isChecking || state.checksPaused) {
                                state
                            } else {
                                state.copy(countdownSeconds = (state.countdownSeconds - 1).coerceAtLeast(0))
                            }
                        }
                        if (!_uiState.value.checksPaused) {
                            publishOngoingIfNeeded()
                        }
                    }
                }
                try {
                    val (result, vpn) = withTimeout(deadlineMs) {
                        coordinator.run(maxAttempts, timeoutSec) { attempt, attempts ->
                            _uiState.update {
                                if (it.checksPaused) {
                                    it
                                } else {
                                    it.copy(
                                        checkAttempt = attempt,
                                        maxAttempts = attempts,
                                        countdownSeconds = timeoutSec,
                                    )
                                }
                            }
                            if (!_uiState.value.checksPaused) {
                                publishOngoingIfNeeded()
                            }
                        }
                    }
                    if (shouldKeepLocalPause()) {
                        applyLocalPause()
                        return@coroutineScope
                    }
                    settings.recordCompletedState(result.state)
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            radiosOff = false,
                            airplaneMode = false,
                            result = result,
                            vpn = vpn,
                            countdownSeconds = 0,
                        )
                    }
                    notifier.onCheckFinished(result, vpn, showOngoing = settings.autoCheckEnabled())
                } catch (timeout: TimeoutCancellationException) {
                    if (shouldKeepLocalPause()) {
                        applyLocalPause()
                        return@coroutineScope
                    }
                    finishUnknown(errorMessage = null)
                } catch (cancelled: CancellationException) {
                    if (shouldKeepLocalPause()) {
                        applyLocalPause()
                    } else {
                        _uiState.update { it.copy(isChecking = false, countdownSeconds = 0) }
                    }
                    throw cancelled
                } catch (error: Exception) {
                    if (shouldKeepLocalPause()) {
                        applyLocalPause()
                        return@coroutineScope
                    }
                    finishUnknown(error.message)
                } finally {
                    ticker.cancel()
                    activeCheckJob = null
                }
            }
        }
    }

    private fun finishUnknown(errorMessage: String?) {
        val unknown = CheckResult(state = NetworkState.UNKNOWN, probes = emptyList())
        settings.recordCompletedState(NetworkState.UNKNOWN)
        _uiState.update {
            it.copy(
                isChecking = false,
                radiosOff = false,
                airplaneMode = false,
                result = unknown,
                errorMessage = errorMessage,
                countdownSeconds = 0,
            )
        }
        notifier.onCheckFinished(
            unknown,
            _uiState.value.vpn ?: VpnStatus(active = false),
            showOngoing = settings.autoCheckEnabled(),
        )
    }

    private fun shouldKeepLocalPause(): Boolean =
        _uiState.value.checksPaused || LocalConnectivity.shouldPauseChecks(appContext)
}
