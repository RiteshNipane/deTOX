package com.detox.ml

/**
 * Turns per-frame probabilities into a per-reel verdict. Pure logic, unit tested.
 * [minFrames] > 1 trades a few seconds of latency for fewer false positives:
 * the reel is only flagged once it has that many frames and their mean p(ai) >= threshold.
 */
class MlVerdictAggregator(
    private val threshold: Float,
    private val minFrames: Int = 1,
    private val maxTracked: Int = 20,
) {
    private val probs = LinkedHashMap<Int, MutableList<Float>>()

    /** Records a frame for [reelHash]; returns true if the reel should now be treated as AI. */
    fun add(reelHash: Int, probAi: Float): Boolean {
        val list = probs.getOrPut(reelHash) { mutableListOf() }
        list.add(probAi)
        while (probs.size > maxTracked) probs.remove(probs.keys.first())
        return list.size >= minFrames && list.average() >= threshold
    }
}
