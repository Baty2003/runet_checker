package ru.runetchecker.ui

import ru.runetchecker.domain.CheckResult

data class CheckUiState(
    val isChecking: Boolean = false,
    val result: CheckResult? = null,
    val errorMessage: String? = null,
)
