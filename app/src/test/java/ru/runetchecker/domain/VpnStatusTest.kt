package ru.runetchecker.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStatusTest {

    @Test
    fun isForeign_onlyWhenActiveAndNotRu() {
        assertFalse(VpnStatus(active = false, countryCode = "DE").isForeign)
        assertFalse(VpnStatus(active = true, countryCode = null).isForeign)
        assertFalse(VpnStatus(active = true, countryCode = "RU").isForeign)
        assertFalse(VpnStatus(active = true, countryCode = "ru").isForeign)
        assertTrue(VpnStatus(active = true, countryCode = "DE").isForeign)
    }
}
