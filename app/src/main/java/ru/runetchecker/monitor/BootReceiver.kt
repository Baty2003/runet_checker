package ru.runetchecker.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.runetchecker.settings.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        if (SettingsStore(context).autoCheckEnabled()) {
            MonitorService.start(context)
        }
    }
}
