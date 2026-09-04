package ru.runetchecker.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.runetchecker.domain.VpnStatus
import ru.runetchecker.probe.SharedHttpClient
import kotlin.coroutines.cancellation.CancellationException

class VpnDetector(
    context: Context,
    private val client: OkHttpClient = SharedHttpClient.instance,
) {
    private val appContext = context.applicationContext

    fun isVpnActive(): Boolean {
        return try {
            detectVpnTransport()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun detect(): VpnStatus {
        if (!isVpnActive()) {
            return VpnStatus(active = false)
        }
        return VpnStatus(active = true, countryCode = lookupCountryIso())
    }

    @Suppress("DEPRECATION")
    private fun detectVpnTransport(): Boolean {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivity.allNetworks.any { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    suspend fun lookupCountryIso(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GEO_URL)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseCountryIso(body)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val GEO_URL = "https://ifconfig.co/json"
        private const val USER_AGENT = "runet-checker/0.2.0"

        private val countryIsoRegex = Regex("\"country_iso\"\\s*:\\s*\"([A-Za-z]{2})\"")

        internal fun parseCountryIso(json: String): String? {
            val code = countryIsoRegex.find(json)?.groupValues?.get(1)?.uppercase() ?: return null
            return if (code.length == 2 && code.all { it in 'A'..'Z' }) code else null
        }
    }
}
