package ru.runetchecker.settings

import android.content.Context

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

    companion object {
        private const val PREFS_NAME = "runet_checker_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
    }
}
