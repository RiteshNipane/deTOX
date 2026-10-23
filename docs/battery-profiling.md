# Battery and stability profiling (Phase 5)

Target from the masterplan: the counter adds **< 3% battery per hour**.

## Built-in mitigations (already in the code)
- Accessibility events are filtered to the two target packages in `accessibility_service_config.xml`.
- Events are collapsed: each burst schedules a single inspection ~120 ms after the last event.
- The tree walk is bounded (depth 12, 120 text nodes) and reads only the on-screen reel item.
- The tracker stops polling once a reel is counted; it only re-checks while a new reel is in its debounce window.
- Screenshots + ML run only while in Reels **and** deSLOP is on, at one frame per ~1.5 s, on background dispatchers.
- Overlays are removed as soon as you leave Reels.

## Measure it
Use `scripts/battery_profile.sh`. Run three 10-minute sessions with the same scrolling pattern and brightness:

| Session | Setup |
|---|---|
| A. Baseline | deTOX accessibility service **off** |
| B. Counter | service on, deSLOP off |
| C. Counter + ML | service on, deSLOP on, model installed |

Record the phone's battery drop and deTOX's own mAh from the dump. The cost of deTOX is B - A (and C - B for ML).
Short sessions are noisy: repeat each twice and compare. Use the Android Studio **Energy** profiler or
`adb shell dumpsys batterystats` on a physical device; emulators say nothing about power.

If over budget, in order of impact:
1. Raise `SAMPLE_INTERVAL_MS` (ML cost scales with it) or require the screen to be unchanged for a moment.
2. Lower `numThreads` in `SlopClassifier`, or try the GPU/NNAPI delegate if it benchmarks better.
3. Increase the event delay (120 ms) and the notification timeout in the accessibility config.
4. Use a smaller model (`--arch small` is already the smallest MobileNetV3).

## Inference latency (exit criterion: < 300 ms)
With deSLOP on, Logcat tag `deTOX` prints `ml p(ai)=... in N ms`. Collect ~100 lines and check the 95th percentile:
```
adb logcat -d -s deTOX | grep -o 'in [0-9]* ms' | grep -o '[0-9]*' | sort -n | awk '{a[NR]=$1} END {print "p50", a[int(NR*.5)], "p95", a[int(NR*.95)]}'
```

## Crash / ANR testing
- Rotate the phone, switch apps rapidly, and background/foreground Instagram mid-scroll.
- Turn the accessibility service off and on while in Reels (overlays must disappear, no crash).
- Revoke the notification permission and hit the daily limit (must not crash).
- Kill the app from recents, then reopen Reels; the day's count must be preserved.
- Run `adb shell am monkey -p com.instagram.android 500` with the service on, then check `adb logcat -b crash`.
- Check Android vitals / `adb shell dumpsys activity processes` for ANRs after a long (1 h) session.
