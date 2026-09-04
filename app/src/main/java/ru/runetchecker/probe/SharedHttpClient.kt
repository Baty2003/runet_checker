package ru.runetchecker.probe

import okhttp3.OkHttpClient
import ru.runetchecker.domain.PROBE_TIMEOUT_SECONDS
import java.util.concurrent.TimeUnit

object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        val timeout = PROBE_TIMEOUT_SECONDS.toLong()
        OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(timeout, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
