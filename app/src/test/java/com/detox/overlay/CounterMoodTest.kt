package com.detox.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterMoodTest {
    @Test fun startsSmiling() {
        assertEquals(CounterMood.SMILING, CounterMood.emojiFor(0))
        assertEquals(CounterMood.SMILING, CounterMood.emojiFor(29))
    }

    @Test fun neutralFrom30() {
        assertEquals(CounterMood.NEUTRAL, CounterMood.emojiFor(30))
        assertEquals(CounterMood.NEUTRAL, CounterMood.emojiFor(59))
    }

    @Test fun sadFrom60() {
        assertEquals(CounterMood.SAD, CounterMood.emojiFor(60))
        assertEquals(CounterMood.SAD, CounterMood.emojiFor(89))
    }

    @Test fun tiredFrom90() {
        assertEquals(CounterMood.TIRED, CounterMood.emojiFor(90))
        assertEquals(CounterMood.TIRED, CounterMood.emojiFor(500))
    }
}
