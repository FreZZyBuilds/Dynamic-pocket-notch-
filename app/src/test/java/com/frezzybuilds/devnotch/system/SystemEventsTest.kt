package com.frezzybuilds.devnotch.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemEventsTest {

    @Test
    fun `low battery warns once per level and resets while charging`() {
        assertEquals(20, LowBattery.crossed(20, charging = false, alreadyWarned = emptySet()))
        assertNull("schon gewarnt", LowBattery.crossed(18, charging = false, alreadyWarned = setOf(20)))
        assertEquals(10, LowBattery.crossed(9, charging = false, alreadyWarned = setOf(20)))
        assertEquals("direkt auf 9 %: die tiefere Stufe", 10, LowBattery.crossed(9, charging = false, alreadyWarned = emptySet()))
        assertNull("beim Laden nie", LowBattery.crossed(5, charging = true, alreadyWarned = emptySet()))
        assertNull(LowBattery.crossed(55, charging = false, alreadyWarned = emptySet()))
    }
}
