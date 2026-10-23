package com.detox.detector

/**
 * Pure counting logic (no Android types) so it can be unit-tested.
 * A reel counts when the content hash changes after the debounce window,
 * and is not a hash we just saw (scroll-back flicker).
 */
class ReelCounter(
    private val debounceMs: Long = 300,
    private val recentSize: Int = 3,
) {
    private var lastHash: Int? = null
    private var pendingHash: Int? = null
    private var pendingSince: Long = 0
    private val recent = ArrayDeque<Int>()

    /** True while a new reel is waiting out the debounce window, i.e. a re-check is worthwhile. */
    val isPending: Boolean get() = pendingHash != null

    /** Call on every snapshot while in reels. Returns true when a NEW reel should be counted. */
    fun onSnapshot(hash: Int?, nowMs: Long): Boolean {
        if (hash == null) return false
        if (hash == lastHash) { pendingHash = null; return false }

        if (hash != pendingHash) {
            pendingHash = hash
            pendingSince = nowMs
            return false
        }
        if (nowMs - pendingSince < debounceMs) return false

        // Stable for the debounce window: it's a real reel change.
        pendingHash = null
        lastHash = hash
        if (hash in recent) return false
        recent.addLast(hash)
        if (recent.size > recentSize) recent.removeFirst()
        return true
    }

    /** Call when the user leaves Reels/Shorts so re-entering the same reel counts again. */
    fun reset() {
        lastHash = null
        pendingHash = null
        recent.clear()
    }
}
