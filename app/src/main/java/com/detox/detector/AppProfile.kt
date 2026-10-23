package com.detox.detector

import android.content.Context
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * All app-specific knowledge lives here so a UI change in Instagram/YouTube is a
 * config fix, not an APK release. Bundled copy: assets/profiles.json.
 */
@Serializable
data class AppProfile(
    val id: String,
    val displayName: String,
    val packageName: String,
    val reelsContainerIds: List<String> = emptyList(),
    val reelsContentDescriptions: List<String> = emptyList(),
    val reelItemIds: List<String> = emptyList(),
    val aiLabelStrings: List<String> = emptyList(),
    /** Caption hashtags / phrases that strongly suggest AI content when no official label is shown. */
    val aiHintStrings: List<String> = emptyList(),
)

@Serializable
data class ProfileConfig(val version: Int, val profiles: List<AppProfile>)

/**
 * Load order: cached remote copy (if it parses) -> bundled asset.
 * A remote config is only accepted if it parses, is non-empty and its version is
 * not older than what we already have, so a bad push can't brick detection.
 */
class ProfileRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "profiles_cache.json")

    @Volatile
    private var config: ProfileConfig = loadCachedOrBundled()

    fun all(): List<AppProfile> = config.profiles
    fun forPackage(pkg: String): AppProfile? = config.profiles.firstOrNull { it.packageName == pkg }
    fun version(): Int = config.version

    /** Fetches [url]; returns true if a newer valid config was applied. Network off the main thread. */
    suspend fun refreshFrom(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val raw = URL(url).openConnection().apply {
                connectTimeout = 5_000
                readTimeout = 5_000
            }.getInputStream().bufferedReader().use { it.readText() }
            applyIfValid(raw)
        }.getOrDefault(false)
    }

    /** Validates then swaps in [raw] and persists it. */
    fun applyIfValid(raw: String): Boolean {
        val parsed = runCatching { json.decodeFromString<ProfileConfig>(raw) }.getOrNull() ?: return false
        if (parsed.profiles.isEmpty() || parsed.version < config.version) return false
        config = parsed
        runCatching { cacheFile.writeText(raw) }
        return true
    }

    private fun loadCachedOrBundled(): ProfileConfig {
        runCatching {
            if (cacheFile.exists()) {
                val c = json.decodeFromString<ProfileConfig>(cacheFile.readText())
                if (c.profiles.isNotEmpty()) return c
            }
        }
        return context.assets.open("profiles.json").bufferedReader().use {
            json.decodeFromString(it.readText())
        }
    }
}
