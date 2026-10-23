package com.detox.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitTrackerTest {
    private val day = "2026-10-21"

    @Test fun noLimitMeansNoNudge() {
        assertNull(LimitTracker().check(day, 500, 0))
    }

    @Test fun firesAtEachLevelOnce() {
        val t = LimitTracker()
        assertNull(t.check(day, 4, 10))
        assertEquals(50, t.check(day, 5, 10))
        assertNull(t.check(day, 6, 10))
        assertEquals(80, t.check(day, 8, 10))
        assertEquals(100, t.check(day, 10, 10))
        assertNull(t.check(day, 11, 10))
    }

    @Test fun jumpReportsHighestOnly() {
        val t = LimitTracker()
        assertEquals(100, t.check(day, 10, 10))
        assertNull(t.check(day, 10, 10))
    }

    @Test fun resetsOnNewDay() {
        val t = LimitTracker()
        assertEquals(50, t.check(day, 5, 10))
        assertEquals(50, t.check("2026-10-22", 5, 10))
    }
}
