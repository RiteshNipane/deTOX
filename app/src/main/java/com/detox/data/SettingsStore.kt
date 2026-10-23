package com.detox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val overlayEnabled: Boolean = true,
    val overlayX: Int = 0, // offset from horizontal centre (top-centre default)
    val overlayY: Int = -1, // negative = default spot below the status bar
    val instagramEnabled: Boolean = true,
    val youtubeEnabled: Boolean = true,
    val dailyLimit: Int = 0, // 0 = no limit (Phase 2)
    val deSlopEnabled: Boolean = false, // Phase 3
    val collectLabel: String = "off", // Phase 4 dataset collection: "off" | "real" | "ai"
)

class SettingsStore(private val context: Context) {
    private object K {
        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val overlayX = intPreferencesKey("overlay_center_x")
        val overlayY = intPreferencesKey("overlay_center_y")
        val instagram = booleanPreferencesKey("app_instagram")
        val youtube = booleanPreferencesKey("app_youtube_shorts")
        val dailyLimit = intPreferencesKey("daily_limit")
        val deSlop = booleanPreferencesKey("deslop_enabled")
        val collect = stringPreferencesKey("collect_label")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            overlayEnabled = p[K.overlayEnabled] ?: d.overlayEnabled,
            overlayX = p[K.overlayX] ?: d.overlayX,
            overlayY = p[K.overlayY] ?: d.overlayY,
            instagramEnabled = p[K.instagram] ?: d.instagramEnabled,
            youtubeEnabled = p[K.youtube] ?: d.youtubeEnabled,
            dailyLimit = p[K.dailyLimit] ?: d.dailyLimit,
            deSlopEnabled = p[K.deSlop] ?: d.deSlopEnabled,
            collectLabel = p[K.collect] ?: d.collectLabel,
        )
    }

    suspend fun setOverlayEnabled(v: Boolean) = context.dataStore.edit { it[K.overlayEnabled] = v }
    suspend fun setOverlayPosition(x: Int, y: Int) = context.dataStore.edit { it[K.overlayX] = x; it[K.overlayY] = y }
    suspend fun setAppEnabled(appId: String, v: Boolean) = context.dataStore.edit {
        when (appId) {
            "instagram" -> it[K.instagram] = v
            "youtube_shorts" -> it[K.youtube] = v
        }
    }
    suspend fun setDailyLimit(v: Int) = context.dataStore.edit { it[K.dailyLimit] = v }
    suspend fun setDeSlopEnabled(v: Boolean) = context.dataStore.edit { it[K.deSlop] = v }
    suspend fun setCollectLabel(v: String) = context.dataStore.edit { it[K.collect] = v }}

fun Settings.isAppEnabled(appId: String): Boolean = when (appId) {
    "instagram" -> instagramEnabled
    "youtube_shorts" -> youtubeEnabled
    else -> false
}
