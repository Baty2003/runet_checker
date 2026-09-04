package ru.runetchecker.monitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

fun Context.ignoresBatteryOptimizations(): Boolean {
    val power = getSystemService(Context.POWER_SERVICE) as PowerManager
    return power.isIgnoringBatteryOptimizations(packageName)
}

fun Context.requestIgnoreBatteryOptimizations() {
    if (ignoresBatteryOptimizations()) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }
    runCatching { startActivity(intent) }
}

fun Context.openBatteryOptimizationSettings() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { startActivity(intent) }
}
