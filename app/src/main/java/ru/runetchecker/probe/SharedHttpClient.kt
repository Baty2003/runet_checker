package ru.runetchecker.probe

import okhttp3.OkHttpClient
import ru.runetchecker.domain.PROBE_TIMEOUT_MAX_SECONDS
import ru.runetchecker.domain.PROBE_TIMEOUT_MIN_SECONDS
import java.util.concurrent.TimeUnit

object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun withTimeout(seconds: Int): OkHttpClient {
        val timeout = seconds.coerceIn(PROBE_TIMEOUT_MIN_SECONDS, PROBE_TIMEOUT_MAX_SECONDS).toLong()
        return instance.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }
}
