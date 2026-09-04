package ru.runetchecker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.runetchecker.RuNetCheckerApp
import ru.runetchecker.check.CheckRepository

class CheckViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CheckRepository = (application as RuNetCheckerApp).repository
    val uiState: StateFlow<CheckUiState> = repository.uiState

    private var checkJob: Job? = null

    fun check() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            repository.runCheck(auto = false)
        }
    }
}
