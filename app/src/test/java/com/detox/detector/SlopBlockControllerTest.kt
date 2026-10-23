package com.detox.detector

import org.junit.Assert.assertEquals
import org.junit.Test

class SlopBlockControllerTest {

    @Test fun labelledReelIsBlockedOnce() {
        val c = SlopBlockController()
        assertEquals(SlopAction.Block(true), c.onSnapshot(true, 1, true, true))
        assertEquals(SlopAction.Block(false), c.onSnapshot(true, 1, true, true))
    }

    @Test fun unlabelledReelIsNotBlocked() {
        val c = SlopBlockController()
        assertEquals(SlopAction.Hide, c.onSnapshot(true, 1, false, true))
    }

    @Test fun nextReelDismissesBlock() {
        val c = SlopBlockController()
        c.onSnapshot(true, 1, true, true)
        assertEquals(SlopAction.Hide, c.onSnapshot(true, 2, false, true))
    }

    @Test fun twoLabelledReelsInARowEachCountAsNew() {
        val c = SlopBlockController()
        assertEquals(SlopAction.Block(true), c.onSnapshot(true, 1, true, true))
        assertEquals(SlopAction.Block(true), c.onSnapshot(true, 2, true, true))
    }

    @Test fun peekHidesAndStaysHiddenForThatReel() {
        val c = SlopBlockController()
        c.onSnapshot(true, 1, true, true)
        c.peek()
        assertEquals(SlopAction.Hide, c.onSnapshot(true, 1, true, true))
    }

    @Test fun peekedReelBlocksAgainAfterEviction() {
        val c = SlopBlockController(maxPeeked = 1)
        c.onSnapshot(true, 1, true, true); c.peek()
        c.onSnapshot(true, 2, true, true); c.peek()
        assertEquals(SlopAction.Block(true), c.onSnapshot(true, 1, true, true))
    }

    @Test fun disabledOrLeftReelsHides() {
        val c = SlopBlockController()
        assertEquals(SlopAction.Hide, c.onSnapshot(true, 1, true, false))
        assertEquals(SlopAction.Hide, c.onSnapshot(false, null, false, true))
    }

    @Test fun nullHashKeepsState() {
        val c = SlopBlockController()
        c.onSnapshot(true, 1, true, true)
        assertEquals(SlopAction.None, c.onSnapshot(true, null, false, true))
        assertEquals(SlopAction.Block(false), c.onSnapshot(true, 1, true, true))
    }

    @Test fun reentryAfterLeavingBlocksAsNew() {
        val c = SlopBlockController()
        c.onSnapshot(true, 1, true, true)
        c.onSnapshot(false, null, false, true)
        assertEquals(SlopAction.Block(true), c.onSnapshot(true, 1, true, true))
    }
}
