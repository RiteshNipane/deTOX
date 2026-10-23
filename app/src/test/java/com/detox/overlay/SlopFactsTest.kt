package com.detox.overlay

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlopFactsTest {
    @Test fun factsAreNonEmptyAndFitTheCard() {
        assertTrue(SlopFacts.all.size >= 5)
        SlopFacts.all.forEach { assertTrue(it.isNotBlank() && it.length <= 260) }
    }

    @Test fun neverRepeatsThePreviousFact() {
        val rng = Random(1)
        var prev = -1
        repeat(200) {
            val next = SlopFacts.pickIndex(rng, prev)
            assertNotEquals(prev, next)
            assertTrue(next in SlopFacts.all.indices)
            prev = next
        }
    }

    @Test fun factsAreUnique() = assertEquals(SlopFacts.all.size, SlopFacts.all.toSet().size)
}
