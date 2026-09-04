package ru.runetchecker.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.runetchecker.domain.NetworkState

class PopupLimiterTest {

    @Test
    fun firstState_isAllowed() {
        assertTrue(
            PopupLimiter.shouldPopup(
                newState = NetworkState.ONLINE,
                lastCheckState = null,
                nowMs = 1_000L,
                lastPopupAtMs = null,
                popupTimesMs = emptyList(),
                unlimited = false,
                cooldownMs = 120_000L,
                maxPerHour = 6,
            ),
        )
    }

    @Test
    fun sameState_isBlocked() {
        assertFalse(
            PopupLimiter.shouldPopup(
                newState = NetworkState.ONLINE,
                lastCheckState = NetworkState.ONLINE,
                nowMs = 10_000L,
                lastPopupAtMs = 1_000L,
                popupTimesMs = listOf(1_000L),
                unlimited = true,
                cooldownMs = 120_000L,
                maxPerHour = 6,
            ),
        )
    }

    @Test
    fun cooldown_blocksEvenOnChange() {
        assertFalse(
            PopupLimiter.shouldPopup(
                newState = NetworkState.WHITELIST,
                lastCheckState = NetworkState.ONLINE,
                nowMs = 60_000L,
                lastPopupAtMs = 1_000L,
                popupTimesMs = listOf(1_000L),
                unlimited = false,
                cooldownMs = 120_000L,
                maxPerHour = 6,
            ),
        )
    }

    @Test
    fun afterCooldown_changeIsAllowedEvenIfAlreadyPoppedThatStateBefore() {
        assertTrue(
            PopupLimiter.shouldPopup(
                newState = NetworkState.ONLINE,
                lastCheckState = NetworkState.OFFLINE,
                nowMs = 200_000L,
                lastPopupAtMs = 1_000L,
                popupTimesMs = listOf(1_000L),
                unlimited = false,
                cooldownMs = 120_000L,
                maxPerHour = 6,
            ),
        )
    }

    @Test
    fun afterCooldown_pendingChangeIsRetried() {
        assertTrue(
            PopupLimiter.shouldPopup(
                newState = NetworkState.OFFLINE,
                lastCheckState = NetworkState.ONLINE,
                nowMs = 200_000L,
                lastPopupAtMs = 1_000L,
                popupTimesMs = listOf(1_000L),
                unlimited = false,
                cooldownMs = 120_000L,
                maxPerHour = 6,
            ),
        )
    }

    @Test
    fun unlimited_skipsCooldownAndHourlyCap() {
        assertTrue(
            PopupLimiter.shouldPopup(
                newState = NetworkState.OFFLINE,
                lastCheckState = NetworkState.ONLINE,
                nowMs = 2_000L,
                lastPopupAtMs = 1_000L,
                popupTimesMs = List(10) { 1_000L },
                unlimited = true,
                cooldownMs = 120_000L,
                maxPerHour = 6,
            ),
        )
    }

    @Test
    fun hourlyCap_blocksWhenReached() {
        val times = List(6) { index -> 1_000L + index }
        assertFalse(
            PopupLimiter.shouldPopup(
                newState = NetworkState.WHITELIST,
                lastCheckState = NetworkState.ONLINE,
                nowMs = 200_000L,
                lastPopupAtMs = 1_000L,
                popupTimesMs = times,
                unlimited = false,
                cooldownMs = 0L,
                maxPerHour = 6,
            ),
        )
    }
}
