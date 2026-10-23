package com.detox.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlVerdictAggregatorTest {

    @Test fun singleFrameAtOrAboveThresholdFlags() {
        val a = MlVerdictAggregator(threshold = 0.9f)
        assertFalse(a.add(1, 0.89f))
        assertTrue(a.add(2, 0.95f))
    }

    @Test fun minFramesRequiresEnoughEvidence() {
        val a = MlVerdictAggregator(threshold = 0.9f, minFrames = 2)
        assertFalse(a.add(1, 0.99f))
        assertTrue(a.add(1, 0.95f))
    }

    @Test fun meanMustClearThreshold() {
        val a = MlVerdictAggregator(threshold = 0.9f, minFrames = 2)
        a.add(1, 0.99f)
        assertFalse(a.add(1, 0.5f)) // mean 0.745
    }

    @Test fun reelsAreTrackedIndependently() {
        val a = MlVerdictAggregator(threshold = 0.9f, minFrames = 2)
        a.add(1, 0.99f)
        assertFalse(a.add(2, 0.99f))
    }

    @Test fun oldestReelsAreEvicted() {
        val a = MlVerdictAggregator(threshold = 0.9f, minFrames = 2, maxTracked = 1)
        a.add(1, 0.99f)
        a.add(2, 0.1f) // evicts reel 1
        assertFalse(a.add(1, 0.99f)) // starts over: only one frame
    }
}
