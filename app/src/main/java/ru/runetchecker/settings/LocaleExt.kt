package ru.runetchecker.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

fun Context.withAppLocale(language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.tag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
