package ru.runetchecker.domain

data class HttpHop(
    val code: Int,
    val url: String,
)

data class ProbeResult(
    val target: ProbeTarget,
    val reachable: Boolean,
    val method: String = "GET",
    val requestUrl: String = "https://${target.host}/",
    val durationMs: Long = 0,
    val hops: List<HttpHop> = emptyList(),
    val errorMessage: String? = null,
) {
    fun logLines(): List<String> {
        val lines = mutableListOf(
            "${target.group.name}  ${target.host}",
            "$method $requestUrl   ${durationMs} ms",
        )
        if (hops.size > 1) {
            hops.zipWithNext().forEach { (from, to) ->
                lines += "HTTP ${from.code} → ${to.url}"
            }
            hops.lastOrNull()?.let { lines += "HTTP ${it.code}" }
        } else {
            hops.firstOrNull()?.let { lines += "HTTP ${it.code}" }
        }
        errorMessage?.let { lines += "ERROR  $it" }
        return lines
    }
}
