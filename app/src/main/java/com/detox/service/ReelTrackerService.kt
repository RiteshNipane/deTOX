package com.detox.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.detox.DetoxApp
import com.detox.data.ReelEvent
import com.detox.data.Settings
import com.detox.data.isAppEnabled
import com.detox.detector.LimitTracker
import com.detox.detector.ReelCounter
import com.detox.detector.ReelDetector
import com.detox.detector.ReelSnapshot
import com.detox.detector.SlopAction
import com.detox.detector.SlopBlockController
import com.detox.ml.FrameGrabber
import com.detox.ml.FrameStore
import com.detox.ml.MlVerdictAggregator
import com.detox.ml.SlopClassifier
import com.detox.overlay.CounterOverlay
import com.detox.overlay.SlopBlockOverlay
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives accessibility events from Instagram / YouTube only (package filter lives in
 * accessibility_service_config.xml). Work is throttled: events just (re)schedule a short
 * delayed inspection, so a burst of scroll events costs one tree walk.
 *
 * Phase 4 adds a slow sampler loop: while inside Reels and deSLOP-ML (or dataset collection)
 * is on, it grabs one frame every ~1.5 s, classifies it, and feeds the verdict into the same
 * block controller the label detector uses.
 */
class ReelTrackerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = ReelDetector()
    private val counter = ReelCounter(debounceMs = DEBOUNCE_MS)
    private val slopController = SlopBlockController()
    private val limitTracker = LimitTracker()
    private lateinit var slopOverlay: SlopBlockOverlay
    private lateinit var notifier: LimitNotifier
    private lateinit var overlay: CounterOverlay
    private lateinit var app: DetoxApp

    private lateinit var classifier: SlopClassifier
    private lateinit var frameGrabber: FrameGrabber
    private lateinit var frameStore: FrameStore
    private lateinit var aggregator: MlVerdictAggregator

    @Volatile private var settings = Settings()
    private var inspectJob: Job? = null
    private var currentPkg: String? = null
    private var wasInReels = false

    /** Hash of the reel currently on screen (main thread only). */
    private var currentHash: Int? = null
    /** Reels the ML model flagged as AI (main thread only, bounded). */
    private val mlFlagged = LinkedHashSet<Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = application as DetoxApp
        notifier = LimitNotifier(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        slopOverlay = SlopBlockOverlay(
            context = this,
            windowManager = wm,
            onPeek = { slopController.peek(); slopOverlay.hide() },
        )
        overlay = CounterOverlay(
            context = this,
            windowManager = wm,
            onMoved = { x, y -> scope.launch { app.settings.setOverlayPosition(x, y) } },
        )

        classifier = SlopClassifier(this)
        frameGrabber = FrameGrabber(classifier.inputSize)
        frameStore = FrameStore(this)
        aggregator = MlVerdictAggregator(threshold = classifier.threshold, minFrames = ML_MIN_FRAMES)

        scope.launch { app.settings.settings.collectLatest { settings = it } }
        scope.launch {
            // Seed the live counter from storage so a service restart doesn't reset the day.
            app.setTodayCount(app.db.reelDao().totalForDay(today()))
            app.todayCount.collectLatest { if (overlay.isShown) overlay.update(it) }
        }
        scope.launch { samplerLoop() }
        startForegroundService(Intent(this, KeepAliveService::class.java))
    }

    /** Keyboards and the system UI overlay other apps without the user having left them. */
    private val ignoredPackages: Set<String> by lazy {
        val ime = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        (ime + packageName + "com.android.systemui").toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val profile = app.profiles.forPackage(pkg)

        if (profile == null) {
            // Another app came to the front: hide everything. Only the event TYPE and package name
            // are looked at; nothing from other apps is read.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg !in ignoredPackages) {
                leaveTrackedApps()
            }
            return
        }
        if (!settings.isAppEnabled(profile.id)) {
            if (wasInReels || overlay.isShown) leaveTrackedApps()
            return
        }
        currentPkg = pkg

        // Throttle, don't debounce: while a video plays, events can arrive faster than the delay, and
        // restarting the wait on each one would starve the inspection for seconds.
        if (inspectJob?.isActive == true) return
        inspectJob = scope.launch {
            delay(EVENT_DELAY_MS)
            inspect()
        }
    }

    /** User is no longer in Instagram/YouTube: hide overlays and forget the current reel. */
    private fun leaveTrackedApps() {
        inspectJob?.cancel()
        currentPkg = null
        currentHash = null
        if (wasInReels) { counter.reset(); wasInReels = false }
        slopController.onSnapshot(false, null, false, false)
        slopOverlay.hide()
        overlay.hide()
    }

    /** The counter shows in any screen of a tracked app (not just Reels), and nowhere else. */
    private fun syncCounterOverlay() {
        if (settings.overlayEnabled) {
            overlay.show(settings.overlayX, settings.overlayY, app.todayCount.value)
        } else overlay.hide()
    }

    private suspend fun inspect() {
        val pkg = currentPkg ?: return
        val profile = app.profiles.forPackage(pkg) ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val t0 = SystemClock.elapsedRealtime()
        val snap = detector.inspect(root, profile)
        // Profiling aid (Phase 5): the tree walk runs on the main thread, so keep it far below ~100 ms.
        val took = SystemClock.elapsedRealtime() - t0
        if (took > SLOW_INSPECT_MS) Log.w(TAG, "slow inspect: $took ms")

        if (!snap.inReels) {
            if (wasInReels) { counter.reset(); wasInReels = false }
            currentHash = null
            slopController.onSnapshot(false, null, false, false)
            slopOverlay.hide()
            syncCounterOverlay() // still inside Instagram/YouTube (e.g. the feed): keep the counter
            return
        }
        wasInReels = true
        if (snap.contentHash != null) currentHash = snap.contentHash
        handleSlop(snap, profile.id)
        syncCounterOverlay()

        if (counter.onSnapshot(snap.contentHash, System.currentTimeMillis())) {
            val now = System.currentTimeMillis()
            app.incrementTodayCount()
            app.db.reelDao().recordReel(
                ReelEvent(appId = profile.id, timestampMs = now, dayKey = today(), contentHash = snap.contentHash!!)
            )
            val total = app.todayCount.value
            limitTracker.check(today(), total, settings.dailyLimit)?.let {
                notifier.nudge(it, total, settings.dailyLimit)
            }
        } else if (counter.isPending) {
            // Only while a new reel is waiting out the debounce; otherwise we'd poll forever.
            inspectJob = scope.launch { delay(RECHECK_MS); inspect() }
        }
    }

    private fun handleSlop(snap: ReelSnapshot, appId: String) {
        val mlFlag = snap.contentHash?.let { it in mlFlagged } == true
        val flagged = snap.hasAiLabel || mlFlag
        if (flagged && !settings.deSlopEnabled) Log.d(TAG, "AI signal seen in $appId but deSLOP is off")
        when (val a = slopController.onSnapshot(true, snap.contentHash, flagged, settings.deSlopEnabled)) {
            is SlopAction.Block -> {
                slopOverlay.show()
                if (a.newlyBlocked) {
                    Log.d(TAG, "blocking reel in $appId (label=${snap.hasAiLabel}, ml=$mlFlag)")
                    scope.launch { app.db.reelDao().recordBlocked(today(), appId) }
                }
            }
            SlopAction.Hide -> slopOverlay.hide()
            SlopAction.None -> Unit
        }
    }

    // ---- Phase 4: frame sampling ------------------------------------------------------

    private suspend fun samplerLoop() {
        while (true) {
            if (!wasInReels) { delay(IDLE_POLL_MS); continue }
            delay(SAMPLE_INTERVAL_MS)

            val wantMl = settings.deSlopEnabled && classifier.isAvailable
            val collect = settings.collectLabel
            if (!wasInReels || (!wantMl && collect == "off")) continue
            if (slopOverlay.isShown) continue // a frame would just show our own cover
            sampleFrame(wantMl, collect)
        }
    }

    private suspend fun sampleFrame(wantMl: Boolean, collect: String) {
        val hash = currentHash ?: return
        val frame = frameGrabber.grab(this) ?: return
        try {
            // The user may have swiped while the screenshot was in flight; drop mismatched frames.
            if (currentHash != hash) return

            if (collect != "off") withContext(Dispatchers.IO) { frameStore.save(frame, collect, hash) }

            if (wantMl) {
                val r = withContext(Dispatchers.Default) { classifier.predict(frame) } ?: return
                Log.d(TAG, "ml p(ai)=${"%.3f".format(r.probAi)} in ${r.millis} ms")
                if (currentHash == hash && aggregator.add(hash, r.probAi)) {
                    mlFlagged.add(hash)
                    while (mlFlagged.size > MAX_FLAGGED) mlFlagged.remove(mlFlagged.first())
                    inspectJob?.cancel()
                    inspectJob = scope.launch { inspect() }
                }
            }
        } finally {
            frame.recycle()
        }
    }

    private fun today(): String = LocalDate.now().toString()

    private companion object {
        const val TAG = "deTOX"
        const val SAMPLE_INTERVAL_MS = 1_500L
        const val IDLE_POLL_MS = 3_000L
        /** Frames needed before ML may flag a reel. 1 = fastest; raise to cut false positives. */
        const val ML_MIN_FRAMES = 1
        const val MAX_FLAGGED = 50
        const val SLOW_INSPECT_MS = 50L
        // Counter latency after a scroll is about EVENT_DELAY_MS + DEBOUNCE_MS + RECHECK_MS (~0.65 s).
        const val EVENT_DELAY_MS = 50L
        const val DEBOUNCE_MS = 300L
        const val RECHECK_MS = 300L
    }

    override fun onInterrupt() { slopOverlay.hide(); overlay.hide() }

    override fun onUnbind(intent: Intent?): Boolean {
        slopOverlay.hide()
        overlay.hide()
        classifier.close()
        stopService(Intent(this, KeepAliveService::class.java))
        scope.cancel()
        return super.onUnbind(intent)
    }
}
