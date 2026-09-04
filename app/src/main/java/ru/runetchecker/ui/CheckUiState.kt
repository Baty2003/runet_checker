package ru.runetchecker.ui

import ru.runetchecker.domain.CheckResult
import ru.runetchecker.domain.VpnStatus

data class CheckUiState(
    val isChecking: Boolean = false,
    val result: CheckResult? = null,
    val errorMessage: String? = null,
    val vpn: VpnStatus? = null,
)
