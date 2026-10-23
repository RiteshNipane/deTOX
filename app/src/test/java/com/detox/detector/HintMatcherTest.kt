package com.detox.detector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HintMatcherTest {
    private val hints = listOf("#ai", "#aivideo", "#madewithcantina", "made with sora", "ai-generated")

    @Test fun hashtagInCaptionMatches() =
        assertTrue(HintMatcher.matches("Skinny or Baggy pants \uD83E\uDD14 #madewithcantina ...", hints))

    @Test fun hashtagMatchesCaseInsensitively() = assertTrue(HintMatcher.matches("fun #AIVideo clip", hints))

    @Test fun shortHashtagNeedsWholeToken() {
        assertTrue(HintMatcher.matches("wow #ai", hints))
        assertFalse(HintMatcher.matches("flying home #airport #airline", hints))
    }

    @Test fun phraseMatchesAsSubstring() {
        assertTrue(HintMatcher.matches("Clip Made With Sora 2", hints))
        assertTrue(HintMatcher.matches("this is ai-generated", hints))
    }

    @Test fun plainTextWithoutHintsDoesNotMatch() = assertFalse(HintMatcher.matches("Part-1 my dog at the beach", hints))

    @Test fun veryLongTextIgnored() = assertFalse(HintMatcher.matches("#ai " + "x".repeat(700), hints))

    @Test fun emptyHintsNeverMatch() = assertFalse(HintMatcher.matches("#ai", emptyList()))
}
