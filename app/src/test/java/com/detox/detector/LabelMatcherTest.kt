package com.detox.detector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelMatcherTest {
    private val labels = listOf("AI content", "AI info")

    @Test fun exactLabelMatches() = assertTrue(LabelMatcher.matches("AI content", labels))

    @Test fun caseAndWhitespaceIgnored() = assertTrue(LabelMatcher.matches("  ai CONTENT ", labels))

    @Test fun shortDecoratedLabelMatches() = assertTrue(LabelMatcher.matches("AI content · Learn more", labels))

    @Test fun otherLabelStringMatches() = assertTrue(LabelMatcher.matches("AI info", labels))

    @Test fun longCaptionMentioningLabelDoesNotMatch() =
        assertFalse(LabelMatcher.matches("my cat is not AI content, I filmed this at home with my phone yesterday", labels))

    @Test fun unrelatedTextDoesNotMatch() = assertFalse(LabelMatcher.matches("Follow", labels))

    @Test fun emptyOrBlankLabelsNeverMatch() {
        assertFalse(LabelMatcher.matches("anything", emptyList()))
        assertFalse(LabelMatcher.matches("anything", listOf("")))
    }
}
