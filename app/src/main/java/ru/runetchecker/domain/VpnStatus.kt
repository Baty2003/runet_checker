package ru.runetchecker.domain

data class VpnStatus(
    val active: Boolean,
    val countryCode: String? = null,
) {
    val isForeign: Boolean
        get() = active && countryCode != null && !countryCode.equals("RU", ignoreCase = true)
}
