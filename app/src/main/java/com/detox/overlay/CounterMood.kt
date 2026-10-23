package com.detox.overlay

/**
 * The face shown next to the counter. It gets more tired the more you scroll today:
 * 0-29 smiling, 30-59 neutral, 60-89 sad, 90+ tired. Pure logic, unit tested.
 * Escapes are used instead of literal emoji so source-file encoding can never mangle them.
 */
object CounterMood {
    const val SMILING = "😊"   // 😊
    const val NEUTRAL = "😐"   // 😐
    const val SAD = "😞"       // 😞
    const val TIRED = "\uD83E\uDEE9"     // U+1FAE9 face with bags under eyes (Unicode 16)

    fun emojiFor(count: Int): String = when {
        count >= 90 -> TIRED
        count >= 60 -> SAD
        count >= 30 -> NEUTRAL
        else -> SMILING
    }
}
