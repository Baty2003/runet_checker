package ru.runetchecker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.runetchecker.domain.CheckResult
import ru.runetchecker.domain.NetworkChecker
import ru.runetchecker.domain.NetworkClassifier
import ru.runetchecker.domain.NetworkState
import ru.runetchecker.probe.DefaultProbeTargets
import ru.runetchecker.probe.HttpsResourceProbe
import kotlin.coroutines.cancellation.CancellationException

class CheckViewModel : ViewModel() {

    private val checker: NetworkChecker = NetworkChecker(
        probe = HttpsResourceProbe(),
        targets = DefaultProbeTargets.targets,
        classifier = NetworkClassifier(),
    )

    private val _uiState = MutableStateFlow(CheckUiState())
    val uiState: StateFlow<CheckUiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null

    fun check() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, errorMessage = null) }
            try {
                val result = checker.check()
                _uiState.update { it.copy(isChecking = false, result = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        result = CheckResult(state = NetworkState.UNKNOWN, probes = emptyList()),
                        errorMessage = error.message,
                    )
                }
            }
        }
    }
}
