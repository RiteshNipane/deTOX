package com.detox.detector

/**
 * Second AI signal for reels that carry no official label: hashtags / phrases creators use for
 * AI content (#aivideo, #madewithcantina, "made with sora" ...). Hashtag-style hints must match a whole
 * token, so "#ai" does not fire on "#airport". Phrase hints (containing a space) match as substrings.
 * Long texts are ignored: a caption is short, a comment thread is not. Pure logic, unit tested.
 */
object HintMatcher {
    private val splitter = Regex("[^\\p{L}\\p{N}#_]+")
    private const val MAX_TEXT = 600

    fun matches(text: String, hints: List<String>): Boolean {
        if (hints.isEmpty() || text.length > MAX_TEXT) return false
        val lower = text.lowercase()
        val tokens = splitter.split(lower).filter { it.isNotEmpty() }.toSet()
        return hints.any { raw ->
            val h = raw.trim().lowercase()
            when {
                h.isEmpty() -> false
                h.contains(' ') || h.contains('-') -> lower.contains(h)
                else -> h in tokens
            }
        }
    }
}
