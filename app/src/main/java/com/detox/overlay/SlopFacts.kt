package com.detox.overlay

import kotlin.random.Random

/**
 * "Did you know?" facts shown on the block screen. Each one is a documented, widely reported
 * incident or a published finding, worded cautiously (no invented statistics).
 * Pure logic, unit tested.
 */
object SlopFacts {
    val all: List<String> = listOf(
        "In January 2024, an AI-cloned voice of President Biden robocalled New Hampshire voters and told them to skip the primary. The FCC then ruled that AI-generated voices in robocalls are illegal.",
        "In 2024, an employee at the engineering firm Arup was fooled by a deepfake video call of senior colleagues and sent about US$25 million to scammers.",
        "In 2023, a fake AI image of an explosion near the Pentagon spread on social media and briefly dented US stock markets.",
        "In January 2024, fake explicit AI images of Taylor Swift flooded social media. One post was reportedly viewed about 47 million times before it was removed.",
        "Days before Slovakia's 2023 election, a faked audio clip of a leading candidate talking about rigging the vote went viral.",
        "A 2019 analysis by Deeptrace (now Sensity) found that about 96% of deepfake videos online were non-consensual porn, almost all targeting women.",
        "Experts warned in 2023 that AI-generated mushroom-foraging books sold online could get readers poisoned by wrong advice.",
        "In a 2026 research benchmark of the newest AI video generators, people spotted the fakes only about half the time, barely better than a coin flip.",
        "AI slop farms churn out huge volumes of cheap fake videos to harvest views and ad money, burying the work of real creators.",
    )

    /** A random fact, never the same one twice in a row (pass the previous index as [avoid]). */
    fun pickIndex(rng: Random = Random.Default, avoid: Int = -1): Int {
        if (all.size <= 1) return 0
        var i = rng.nextInt(all.size)
        while (i == avoid) i = rng.nextInt(all.size)
        return i
    }
}
