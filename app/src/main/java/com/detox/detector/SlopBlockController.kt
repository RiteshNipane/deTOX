package com.detox.detector

sealed interface SlopAction {
    /** Show (or keep showing) the block. [newlyBlocked] is true only the first time for this reel. */
    data class Block(val newlyBlocked: Boolean) : SlopAction
    data object Hide : SlopAction
    data object None : SlopAction
}

/**
 * Decides when the deSLOP overlay is up. Pure logic, unit tested.
 *
 * The overlay itself does not intercept touches, so the user's swipe reaches the app.
 * "Dismiss on next scroll" is therefore detected here: once the visible reel's hash
 * changes (and has no label), we hide.
 */
class SlopBlockController(private val maxPeeked: Int = 20) {
    private var blockedHash: Int? = null
    private val peeked = LinkedHashSet<Int>()

    fun onSnapshot(inReels: Boolean, hash: Int?, hasLabel: Boolean, enabled: Boolean): SlopAction {
        if (!enabled || !inReels) {
            blockedHash = null
            return SlopAction.Hide
        }
        // Mid-transition snapshots carry no identity; keep whatever state we have.
        if (hash == null) return SlopAction.None

        if (hasLabel && hash !in peeked) {
            val newly = blockedHash != hash
            blockedHash = hash
            return SlopAction.Block(newly)
        }
        blockedHash = null
        return SlopAction.Hide
    }

    /** User tapped "Peek anyway" for the reel currently blocked. */
    fun peek() {
        val h = blockedHash ?: return
        peeked.add(h)
        while (peeked.size > maxPeeked) peeked.remove(peeked.first())
        blockedHash = null
    }
}
