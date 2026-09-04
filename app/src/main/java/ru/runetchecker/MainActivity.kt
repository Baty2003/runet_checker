package ru.runetchecker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.runetchecker.settings.SettingsStore
import ru.runetchecker.settings.withAppLocale
import ru.runetchecker.ui.CheckScreen
import ru.runetchecker.ui.RuNetCheckerTheme

class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsStore

    override fun attachBaseContext(newBase: Context) {
        val store = SettingsStore(newBase)
        super.attachBaseContext(newBase.withAppLocale(store.language()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode()) }
            RuNetCheckerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CheckScreen(
                        themeMode = themeMode,
                        language = settings.language(),
                        onThemeModeChange = { mode ->
                            settings.setThemeMode(mode)
                            themeMode = mode
                        },
                        onLanguageChange = { language ->
                            settings.setLanguage(language)
                            recreate()
                        },
                    )
                }
            }
        }
    }
}

