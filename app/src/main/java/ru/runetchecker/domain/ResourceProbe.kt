package ru.runetchecker.domain

interface ResourceProbe {
    suspend fun probe(target: ProbeTarget): ProbeResult
}
