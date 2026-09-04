package ru.runetchecker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnDetectorParseTest {

    @Test
    fun parseCountryIso_fromIfconfigJson() {
        val json = """{"ip":"1.2.3.4","country_iso":"DE","country":"Germany"}"""
        assertEquals("DE", VpnDetector.parseCountryIso(json))
    }

    @Test
    fun parseCountryIso_ignoresMissingField() {
        val json = """{"ip":"1.2.3.4","country":"Germany"}"""
        assertNull(VpnDetector.parseCountryIso(json))
    }
}
