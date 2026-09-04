package ru.runetchecker

import android.app.Application
import ru.runetchecker.check.CheckRepository
import ru.runetchecker.settings.SettingsStore

class RuNetCheckerApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: CheckRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = CheckRepository(this, settings)
    }
}
