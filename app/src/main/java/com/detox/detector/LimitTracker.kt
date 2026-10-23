package com.detox.detector

/**
 * Decides when to nudge about the daily limit. Pure logic, unit tested.
 * Each level (50/80/100%) fires at most once per day; if a jump crosses several
 * levels at once, only the highest is reported.
 */
class LimitTracker(private val levels: List<Int> = listOf(50, 80, 100)) {
    private var day: String? = null
    private val fired = mutableSetOf<Int>()

    /** Returns the percent level to nudge about, or null if nothing new was crossed. */
    fun check(dayKey: String, count: Int, limit: Int): Int? {
        if (limit <= 0) return null
        if (dayKey != day) { day = dayKey; fired.clear() }
        val pct = count * 100 / limit
        val newly = levels.filter { it <= pct && it !in fired }
        val level = newly.maxOrNull() ?: return null
        fired.addAll(levels.filter { it <= level })
        return level
    }
}
