package ru.runetchecker.ui

import ru.runetchecker.domain.CheckResult
import ru.runetchecker.domain.PROBE_TIMEOUT_SECONDS
import ru.runetchecker.domain.VpnStatus

data class CheckUiState(
    val isChecking: Boolean = false,
    val result: CheckResult? = null,
    val errorMessage: String? = null,
    val vpn: VpnStatus? = null,
    val countdownSeconds: Int = PROBE_TIMEOUT_SECONDS,
    val checkAttempt: Int = 1,
    val maxAttempts: Int = 1,
    val autoCheck: Boolean = false,
    val radiosOff: Boolean = false,
    val airplaneMode: Boolean = false,
) {
    val checksPaused: Boolean get() = radiosOff || airplaneMode
}
