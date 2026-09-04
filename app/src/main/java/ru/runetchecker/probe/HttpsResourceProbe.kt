package ru.runetchecker.probe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.runetchecker.domain.ProbeResult
import ru.runetchecker.domain.ProbeTarget
import ru.runetchecker.domain.ResourceProbe
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class HttpsResourceProbe(
    private val client: OkHttpClient = defaultClient(),
) : ResourceProbe {

    override suspend fun probe(target: ProbeTarget): ProbeResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://${target.host}/")
            .get()
            .build()

        try {
            client.newCall(request).execute().use {
                ProbeResult(target = target, reachable = true)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ProbeResult(target = target, reachable = false)
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 5L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
