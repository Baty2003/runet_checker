package ru.runetchecker.probe

import ru.runetchecker.domain.ProbeGroup
import ru.runetchecker.domain.ProbeTarget

object DefaultProbeTargets {
    val targets: List<ProbeTarget> = listOf(
        ProbeTarget("google.com", ProbeGroup.GLOBAL),
        ProbeTarget("cloudflare.com", ProbeGroup.GLOBAL),
        ProbeTarget("wikipedia.org", ProbeGroup.GLOBAL),
        ProbeTarget("microsoft.com", ProbeGroup.GLOBAL),
        ProbeTarget("github.com", ProbeGroup.GLOBAL),
        ProbeTarget("apple.com", ProbeGroup.GLOBAL),
        ProbeTarget("max.ru", ProbeGroup.WHITELIST),
        ProbeTarget("yandex.ru", ProbeGroup.WHITELIST),
        ProbeTarget("vk.ru", ProbeGroup.WHITELIST),
        ProbeTarget("mail.ru", ProbeGroup.WHITELIST),
        ProbeTarget("ok.ru", ProbeGroup.WHITELIST),
        ProbeTarget("gosuslugi.ru", ProbeGroup.WHITELIST),
        ProbeTarget("avito.ru", ProbeGroup.WHITELIST),
    )
}
