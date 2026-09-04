package ru.runetchecker.monitor

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.runetchecker.RuNetCheckerApp
import ru.runetchecker.notify.StatusNotifier
import kotlin.coroutines.cancellation.CancellationException

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var checkJob: Job? = null
    private var debounceJob: Job? = null
    private val runNow = Channel<Unit>(Channel.CONFLATED)
    private var lastVpnFingerprint: String = ""
    private val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val app = application as RuNetCheckerApp
        startForegroundInternal(app.repository.placeholderOngoingNotification())
        lastVpnFingerprint = networkFingerprint()
        registerNetworkCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as RuNetCheckerApp
        if (!app.settings.autoCheckEnabled()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { runLoop(app) }
        }
        return START_STICKY
    }

    private suspend fun runLoop(app: RuNetCheckerApp) {
        try {
            while (app.settings.autoCheckEnabled()) {
                checkJob = scope.launch {
                    app.repository.runCheck(auto = true)
                }
                checkJob?.join()
                val intervalMs = app.settings.autoCheckIntervalSeconds() * 1000L
                withTimeoutOrNull(intervalMs) {
                    runNow.receive()
                }
            }
        } catch (_: CancellationException) {
            return
        }
        app.repository.cancelOngoing()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerNetworkCallbacks() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        val refreshCallback = vpnRefreshCallback()
        val defaultCallback = vpnRefreshCallback()
        val vpnCallback = vpnRefreshCallback()
        val wifiCallback = vpnRefreshCallback()
        val cellCallback = vpnRefreshCallback()
        callbacks += refreshCallback
        callbacks += defaultCallback
        callbacks += vpnCallback
        callbacks += wifiCallback
        callbacks += cellCallback
        val allNetworks = NetworkRequest.Builder().build()
        val vpnRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cellRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        connectivity.registerNetworkCallback(allNetworks, refreshCallback, handler)
        connectivity.registerNetworkCallback(vpnRequest, vpnCallback, handler)
        connectivity.registerNetworkCallback(wifiRequest, wifiCallback, handler)
        connectivity.registerNetworkCallback(cellRequest, cellCallback, handler)
        connectivity.registerDefaultNetworkCallback(defaultCallback, handler)
    }

    private fun vpnRefreshCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleVpnRefresh()
        override fun onLost(network: Network) = scheduleVpnRefresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            scheduleVpnRefresh()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            scheduleVpnRefresh()
    }

    private fun scheduleVpnRefresh() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(300)
            val fingerprint = networkFingerprint()
            if (fingerprint == lastVpnFingerprint) return@launch
            lastVpnFingerprint = fingerprint
            val app = application as RuNetCheckerApp
            if (!app.settings.autoCheckEnabled()) return@launch
            app.repository.interruptActiveCheck()
            checkJob?.cancel()
            runNow.trySend(Unit)
        }
    }

    @Suppress("DEPRECATION")
    private fun networkFingerprint(): String {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val defaultNetwork = connectivity.activeNetwork
        val defaultCaps = defaultNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val defaultLinks = defaultNetwork?.let { connectivity.getLinkProperties(it) }
        val defaultPart = listOf(
            defaultNetwork?.toString().orEmpty(),
            transportsOf(defaultCaps),
            defaultLinks?.interfaceName.orEmpty(),
        ).joinToString("|")
        val networks = connectivity.allNetworks.map { network ->
            val caps = connectivity.getNetworkCapabilities(network)
            val links = connectivity.getLinkProperties(network)
            val extra = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                "${links?.linkAddresses}|${links?.routes?.size}"
            } else {
                ""
            }
            listOf(
                network.toString(),
                transportsOf(caps),
                links?.interfaceName.orEmpty(),
                extra,
            ).joinToString("|")
        }.sorted()
        return "default:$defaultPart;nets:${networks.joinToString(";")}"
    }

    private fun transportsOf(caps: NetworkCapabilities?): String {
        if (caps == null) return "none"
        return buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cell")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("eth")
        }.joinToString(",")
    }

    private fun startForegroundInternal(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                StatusNotifier.ONGOING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(StatusNotifier.ONGOING_ID, notification)
        }
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        val connectivity = getSystemService(ConnectivityManager::class.java)
        callbacks.forEach { callback ->
            try {
                connectivity.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
        callbacks.clear()
        checkJob?.cancel()
        loopJob?.cancel()
        scope.cancel()
        val app = application as RuNetCheckerApp
        if (!app.settings.autoCheckEnabled()) {
            app.repository.cancelOngoing()
        }
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: MonitorService? = null

        fun republishForeground(notification: Notification): Boolean {
            val service = instance ?: return false
            service.startForegroundInternal(notification)
            return true
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
