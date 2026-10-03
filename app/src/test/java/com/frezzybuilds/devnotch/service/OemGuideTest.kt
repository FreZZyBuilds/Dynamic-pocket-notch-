package com.frezzybuilds.devnotch.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OemGuideTest {

    @Test
    fun `detects xiaomi family and samsung, ignores others`() {
        assertEquals(OemGuide.XIAOMI, OemGuide.forDevice("Xiaomi", "Redmi"))
        assertEquals(OemGuide.XIAOMI, OemGuide.forDevice("Xiaomi", "POCO"))
        assertEquals(OemGuide.SAMSUNG, OemGuide.forDevice("samsung", "samsung"))
        assertNull(OemGuide.forDevice("Google", "google"))
        assertNull(OemGuide.forDevice("OnePlus", "OnePlus"))
    }
}
