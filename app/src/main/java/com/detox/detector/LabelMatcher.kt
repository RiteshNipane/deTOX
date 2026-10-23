package com.detox.detector

/**
 * Decides whether a piece of on-screen text IS an AI label (e.g. Instagram's "AI content"
 * under the username), as opposed to a caption or comment that merely mentions it.
 * Labels are short: the text may only exceed the label itself by [slack] characters.
 * Pure logic, unit tested.
 */
object LabelMatcher {
    fun matches(text: String, labels: List<String>, slack: Int = 25): Boolean {
        val t = text.trim()
        return labels.any { l ->
            l.isNotBlank() && t.length <= l.length + slack && t.contains(l, ignoreCase = true)
        }
    }
}
