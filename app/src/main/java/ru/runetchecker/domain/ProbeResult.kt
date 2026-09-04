package ru.runetchecker.domain

data class ProbeResult(
    val target: ProbeTarget,
    val reachable: Boolean,
)
