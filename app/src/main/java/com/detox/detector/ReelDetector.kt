package com.detox.detector

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Result of inspecting one window snapshot. */
data class ReelSnapshot(
    val inReels: Boolean,
    /** Stable-ish hash of the visible reel's text/descriptions; null if nothing identifiable. */
    val contentHash: Int?,
    /** True if one of the profile's AI-label strings is visible (used by deSLOP in Phase 3). */
    val hasAiLabel: Boolean,
)

class ReelDetector {

    fun inspect(root: AccessibilityNodeInfo, profile: AppProfile): ReelSnapshot {
        val inReels = isInReels(root, profile)
        if (!inReels) return ReelSnapshot(false, null, false)

        val item = findCurrentItem(root, profile) ?: root
        val texts = ArrayList<String>(16)
        collectText(item, texts, depth = 0, maxNodes = 120)
        val hash = if (texts.isEmpty()) null else texts.joinToString("|").hashCode()
        return ReelSnapshot(true, hash, hasAiSignal(root, profile))
    }

    /**
     * True if the visible screen shows an AI label ("AI content" under the username) or an AI hint in
     * the caption. Only the LOWER part of the screen counts: that is where Instagram puts the username
     * row and caption, and it keeps page titles such as a search header reading "ai content" (top bar)
     * from flagging every reel in the feed.
     */
    private fun hasAiSignal(root: AccessibilityNodeInfo, p: AppProfile): Boolean {
        val screen = Rect().also { root.getBoundsInScreen(it) }
        if (screen.height() <= 0) return false
        val minY = screen.top + (screen.height() * LOWER_REGION_START).toInt()
        return scanAi(root, p, minY, screen.bottom, Rect(), intArrayOf(0), 0)
    }

    private fun scanAi(
        n: AccessibilityNodeInfo?, p: AppProfile, minY: Int, maxY: Int, r: Rect, visited: IntArray, depth: Int,
    ): Boolean {
        if (n == null || depth > 30 || visited[0]++ > MAX_SCAN_NODES) return false
        if (!n.isVisibleToUser) return false
        n.getBoundsInScreen(r)
        val centreY = (r.top + r.bottom) / 2
        if (centreY in minY..maxY) {
            for (s in arrayOf(n.text?.toString(), n.contentDescription?.toString())) {
                if (s != null && (LabelMatcher.matches(s, p.aiLabelStrings) || HintMatcher.matches(s, p.aiHintStrings))) return true
            }
        }
        for (i in 0 until n.childCount) {
            if (scanAi(n.getChild(i), p, minY, maxY, r, visited, depth + 1)) return true
        }
        return false
    }

    private companion object {
        const val LOWER_REGION_START = 0.5f
        const val MAX_SCAN_NODES = 600
    }

    private fun isInReels(root: AccessibilityNodeInfo, p: AppProfile): Boolean {
        for (id in p.reelsContainerIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.any { it.isVisibleToUser }) return true
        }
        // Fallback: content-description text matching when view IDs change.
        for (desc in p.reelsContentDescriptions) {
            // The active bottom-nav tab reports isSelected = true.
            if (root.findAccessibilityNodeInfosByText(desc).any { it.isSelected }) return true
        }
        return false
    }

    /** The reel item occupying the most screen area is the one on screen. */
    private fun findCurrentItem(root: AccessibilityNodeInfo, p: AppProfile): AccessibilityNodeInfo? {
        val candidates = p.reelItemIds.flatMap { root.findAccessibilityNodeInfosByViewId(it) }
            .filter { it.isVisibleToUser }
        val r = Rect()
        return candidates.maxByOrNull { n -> n.getBoundsInScreen(r); r.width().toLong() * r.height() }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int, maxNodes: Int) {
        if (node == null || depth > 12 || out.size >= maxNodes) return
        // Reels pagers keep neighbouring reels in the tree. Reading only what is actually on screen
        // keeps the hash stable (fast counting) and stops the next reel's label triggering early.
        if (!node.isVisibleToUser) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, depth + 1, maxNodes)
    }
}
