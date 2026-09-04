package ru.runetchecker.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager

data class RadioSnapshot(
    val airplaneMode: Boolean,
    val wifiEnabled: Boolean,
    val mobileDataEnabled: Boolean,
    val ethernetConnected: Boolean,
) {
    val shouldPauseChecks: Boolean
        get() = shouldPauseChecks(
            airplaneMode = airplaneMode,
            wifiEnabled = wifiEnabled,
            mobileDataEnabled = mobileDataEnabled,
            ethernetConnected = ethernetConnected,
        )
}

fun shouldPauseChecks(
    airplaneMode: Boolean,
    wifiEnabled: Boolean,
    mobileDataEnabled: Boolean,
    ethernetConnected: Boolean,
): Boolean {
    if (airplaneMode) return true
    return !wifiEnabled && !mobileDataEnabled && !ethernetConnected
}

object LocalConnectivity {
    fun shouldPauseChecks(context: Context): Boolean = snapshot(context).shouldPauseChecks

    fun snapshot(context: Context): RadioSnapshot {
        val app = context.applicationContext
        return RadioSnapshot(
            airplaneMode = isAirplaneMode(app),
            wifiEnabled = isWifiEnabled(app),
            mobileDataEnabled = isMobileDataEnabled(app),
            ethernetConnected = hasEthernet(app),
        )
    }

    private fun isAirplaneMode(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (_: Exception) {
        false
    }

    private fun isWifiEnabled(context: Context): Boolean = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.isWifiEnabled == true
    } catch (_: Exception) {
        false
    }

    private fun isMobileDataEnabled(context: Context): Boolean = try {
        context.getSystemService(TelephonyManager::class.java)?.isDataEnabled == true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun hasEthernet(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivity.allNetworks.any { network ->
            val caps = connectivity.getNetworkCapabilities(network) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }
}
