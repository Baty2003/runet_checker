package ru.runetchecker.notify

import ru.runetchecker.domain.NetworkState

object PopupLimiter {
    fun shouldPopup(
        newState: NetworkState,
        lastCheckState: NetworkState?,
        nowMs: Long,
        lastPopupAtMs: Long?,
        popupTimesMs: List<Long>,
        unlimited: Boolean,
        cooldownMs: Long,
        maxPerHour: Int,
    ): Boolean {
        if (lastCheckState == newState) return false
        if (unlimited) return true
        if (lastPopupAtMs != null && nowMs - lastPopupAtMs < cooldownMs) return false
        val hourAgo = nowMs - HOUR_MS
        val count = popupTimesMs.count { it >= hourAgo }
        return count < maxPerHour
    }

    const val HOUR_MS = 3_600_000L
}
