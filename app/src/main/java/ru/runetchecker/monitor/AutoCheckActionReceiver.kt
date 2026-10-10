package ru.runetchecker.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoCheckActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP_AUTO_CHECK) return
        MonitorService.setEnabled(context, false)
    }

    companion object {
        const val ACTION_STOP_AUTO_CHECK = "ru.runetchecker.action.STOP_AUTO_CHECK"
    }
}
