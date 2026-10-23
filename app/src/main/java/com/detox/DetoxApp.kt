package com.detox

import android.app.Application
import com.detox.data.AppDatabase
import com.detox.data.SettingsStore
import com.detox.detector.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide singletons. Small enough that DI is not worth it yet. */
class DetoxApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var profiles: ProfileRepository
        private set

    private val _todayCount = MutableStateFlow(0)
    /** Live count shown by the overlay; seeded from Room on service start. */
    val todayCount: StateFlow<Int> = _todayCount

    fun setTodayCount(value: Int) { _todayCount.value = value }
    fun incrementTodayCount() { _todayCount.value = _todayCount.value + 1 }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = SettingsStore(this)
        profiles = ProfileRepository(this)

        // Best-effort remote config refresh; bundled/cached profiles keep working if it fails.
        val url = getString(R.string.remote_config_url)
        if (url.isNotBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { profiles.refreshFrom(url) }
        }
    }
}
