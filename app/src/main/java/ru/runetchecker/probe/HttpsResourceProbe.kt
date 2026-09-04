package ru.runetchecker.probe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.runetchecker.domain.HttpHop
import ru.runetchecker.domain.ProbeResult
import ru.runetchecker.domain.ProbeTarget
import ru.runetchecker.domain.ResourceProbe
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

class HttpsResourceProbe(
    private val client: OkHttpClient = defaultClient(),
) : ResourceProbe {

    override suspend fun probe(target: ProbeTarget): ProbeResult = withContext(Dispatchers.IO) {
        val requestUrl = "https://${target.host}/"
        val startedAt = System.nanoTime()
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                ProbeResult(
                    target = target,
                    reachable = true,
                    method = "GET",
                    requestUrl = requestUrl,
                    durationMs = elapsedMs(startedAt),
                    hops = responseHops(response),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ProbeResult(
                target = target,
                reachable = false,
                method = "GET",
                requestUrl = requestUrl,
                durationMs = elapsedMs(startedAt),
                errorMessage = describeError(error),
            )
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

        private fun elapsedMs(startedAt: Long): Long =
            (System.nanoTime() - startedAt) / 1_000_000

        private fun responseHops(response: Response): List<HttpHop> {
            val chain = mutableListOf<Response>()
            var current: Response? = response
            while (current != null) {
                chain.add(current)
                current = current.priorResponse
            }
            return chain.asReversed().map { hop ->
                HttpHop(code = hop.code, url = hop.request.url.toString())
            }
        }

        internal fun describeError(error: Exception): String = when (error) {
            is SocketTimeoutException, is InterruptedIOException -> "timeout (${TIMEOUT_SECONDS}s)"
            is UnknownHostException -> "DNS: ${error.message ?: "unknown host"}"
            is SSLException -> "TLS: ${error.message ?: error.javaClass.simpleName}"
            is ConnectException -> "connection: ${error.message ?: "failed"}"
            else -> error.message ?: error.javaClass.simpleName
        }
    }
}
