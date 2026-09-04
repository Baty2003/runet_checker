package ru.runetchecker.settings

import android.content.Context
import ru.runetchecker.domain.AUTO_CHECK_DEFAULT_SECONDS
import ru.runetchecker.domain.AUTO_CHECK_MIN_SECONDS
import ru.runetchecker.domain.NetworkState
import ru.runetchecker.domain.POPUP_COOLDOWN_DEFAULT_SECONDS
import ru.runetchecker.domain.POPUP_MAX_PER_HOUR_DEFAULT

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun language(): AppLanguage {
        val tag = prefs.getString(KEY_LANGUAGE, AppLanguage.RUSSIAN.tag)
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.RUSSIAN
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    fun themeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun autoCheckEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CHECK, false)

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun autoCheckIntervalSeconds(): Int =
        prefs.getInt(KEY_AUTO_INTERVAL, AUTO_CHECK_DEFAULT_SECONDS).coerceAtLeast(AUTO_CHECK_MIN_SECONDS)

    fun setAutoCheckIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_INTERVAL, seconds.coerceAtLeast(AUTO_CHECK_MIN_SECONDS)).apply()
    }

    fun popupUnlimited(): Boolean = prefs.getBoolean(KEY_POPUP_UNLIMITED, false)

    fun setPopupUnlimited(unlimited: Boolean) {
        prefs.edit().putBoolean(KEY_POPUP_UNLIMITED, unlimited).apply()
    }

    fun popupCooldownSeconds(): Int =
        prefs.getInt(KEY_POPUP_COOLDOWN, POPUP_COOLDOWN_DEFAULT_SECONDS).coerceAtLeast(0)

    fun setPopupCooldownSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_POPUP_COOLDOWN, seconds.coerceAtLeast(0)).apply()
    }

    fun popupMaxPerHour(): Int =
        prefs.getInt(KEY_POPUP_MAX_HOUR, POPUP_MAX_PER_HOUR_DEFAULT).coerceAtLeast(1)

    fun setPopupMaxPerHour(max: Int) {
        prefs.edit().putInt(KEY_POPUP_MAX_HOUR, max.coerceAtLeast(1)).apply()
    }

    fun lastCheckState(): NetworkState? {
        val raw = prefs.getString(KEY_LAST_CHECK_STATE, null) ?: return null
        return NetworkState.entries.firstOrNull { it.name == raw }
    }

    fun recordCheckState(state: NetworkState) {
        prefs.edit().putString(KEY_LAST_CHECK_STATE, state.name).apply()
    }

    fun lastPoppedState(): NetworkState? {
        val raw = prefs.getString(KEY_LAST_POPPED_STATE, null) ?: return null
        return NetworkState.entries.firstOrNull { it.name == raw }
    }

    fun lastPopupAtMs(): Long? {
        if (!prefs.contains(KEY_LAST_POPUP_AT)) return null
        return prefs.getLong(KEY_LAST_POPUP_AT, 0L)
    }

    fun popupTimesMs(): List<Long> {
        val raw = prefs.getString(KEY_POPUP_TIMES, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toLongOrNull() }
    }

    fun lastAlertNotificationId(): Int = prefs.getInt(KEY_ALERT_NOTIFICATION_ID, 0)

    fun setLastAlertNotificationId(id: Int) {
        prefs.edit().putInt(KEY_ALERT_NOTIFICATION_ID, id).apply()
    }

    fun recordPopup(state: NetworkState, nowMs: Long) {
        val hourAgo = nowMs - 3_600_000L
        val times = (popupTimesMs().filter { it >= hourAgo } + nowMs)
        prefs.edit()
            .putString(KEY_LAST_POPPED_STATE, state.name)
            .putLong(KEY_LAST_POPUP_AT, nowMs)
            .putString(KEY_POPUP_TIMES, times.joinToString(","))
            .apply()
    }

    fun migrateAlertPipeline() {
        if (prefs.getInt(KEY_ALERT_PIPELINE, 0) >= ALERT_PIPELINE_VERSION) return
        prefs.edit()
            .remove(KEY_LAST_CHECK_STATE)
            .remove(KEY_LAST_POPPED_STATE)
            .remove(KEY_LAST_POPUP_AT)
            .remove(KEY_POPUP_TIMES)
            .putInt(KEY_ALERT_PIPELINE, ALERT_PIPELINE_VERSION)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "runet_checker_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_AUTO_CHECK = "auto_check"
        private const val KEY_AUTO_INTERVAL = "auto_interval_sec"
        private const val KEY_POPUP_UNLIMITED = "popup_unlimited"
        private const val KEY_POPUP_COOLDOWN = "popup_cooldown_sec"
        private const val KEY_POPUP_MAX_HOUR = "popup_max_hour"
        private const val KEY_LAST_CHECK_STATE = "last_check_state"
        private const val KEY_LAST_POPPED_STATE = "last_popped_state"
        private const val KEY_LAST_POPUP_AT = "last_popup_at"
        private const val KEY_POPUP_TIMES = "popup_times"
        private const val KEY_ALERT_NOTIFICATION_ID = "alert_notification_id"
        private const val KEY_ALERT_PIPELINE = "alert_pipeline"
        private const val ALERT_PIPELINE_VERSION = 3
    }
}
