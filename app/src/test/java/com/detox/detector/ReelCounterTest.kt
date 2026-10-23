package com.detox.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelCounterTest {

    @Test
    fun countsOnlyAfterDebounce() {
        val c = ReelCounter(debounceMs = 400)
        assertFalse(c.onSnapshot(1, 0))
        assertFalse(c.onSnapshot(1, 200))
        assertTrue(c.onSnapshot(1, 450))
    }

    @Test
    fun sameReelNotCountedTwice() {
        val c = ReelCounter()
        c.onSnapshot(1, 0); assertTrue(c.onSnapshot(1, 500))
        assertFalse(c.onSnapshot(1, 1000))
        assertFalse(c.onSnapshot(1, 2000))
    }

    @Test
    fun fastSwipeThroughIsNotCounted() {
        val c = ReelCounter(debounceMs = 400)
        var counted = 0
        listOf(1 to 0L, 2 to 100L, 3 to 200L, 4 to 300L).forEach { (h, t) ->
            if (c.onSnapshot(h, t)) counted++
        }
        assertEquals(0, counted)
    }

    @Test
    fun scrollBackToRecentReelIsNotRecounted() {
        val c = ReelCounter()
        c.onSnapshot(1, 0); assertTrue(c.onSnapshot(1, 500))
        c.onSnapshot(2, 600); assertTrue(c.onSnapshot(2, 1100))
        c.onSnapshot(1, 1200); assertFalse(c.onSnapshot(1, 1700))
    }

    @Test
    fun resetAllowsRecount() {
        val c = ReelCounter()
        c.onSnapshot(1, 0); assertTrue(c.onSnapshot(1, 500))
        c.reset()
        c.onSnapshot(1, 600); assertTrue(c.onSnapshot(1, 1100))
    }

    @Test
    fun pendingOnlyUntilCounted() {
        val c = ReelCounter()
        assertFalse(c.isPending)
        c.onSnapshot(1, 0)
        assertTrue(c.isPending)
        c.onSnapshot(1, 500)
        assertFalse(c.isPending)
        c.onSnapshot(1, 1000) // same reel again: no more polling needed
        assertFalse(c.isPending)
    }

    @Test
    fun nullHashIgnored() {
        val c = ReelCounter()
        assertFalse(c.onSnapshot(null, 0))
        assertFalse(c.onSnapshot(null, 1000))
    }
}
