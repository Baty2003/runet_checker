package ru.runetchecker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeResultLogTest {

    @Test
    fun logLines_includeRedirectAndFinalCode() {
        val result = ProbeResult(
            target = ProbeTarget("google.com", ProbeGroup.GLOBAL),
            reachable = true,
            requestUrl = "https://google.com/",
            durationMs = 812,
            hops = listOf(
                HttpHop(301, "https://google.com/"),
                HttpHop(200, "https://www.google.com/"),
            ),
        )

        assertEquals(
            listOf(
                "GLOBAL  google.com",
                "GET https://google.com/   812 ms",
                "HTTP 301 → https://www.google.com/",
                "HTTP 200",
            ),
            result.logLines(),
        )
    }

    @Test
    fun logLines_includeTimeoutError() {
        val result = ProbeResult(
            target = ProbeTarget("wikipedia.org", ProbeGroup.GLOBAL),
            reachable = false,
            requestUrl = "https://wikipedia.org/",
            durationMs = 5004,
            errorMessage = "timeout (5s)",
        )

        assertEquals(
            listOf(
                "GLOBAL  wikipedia.org",
                "GET https://wikipedia.org/   5004 ms",
                "ERROR  timeout (5s)",
            ),
            result.logLines(),
        )
    }
}
