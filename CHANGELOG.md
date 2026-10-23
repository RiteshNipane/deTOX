# Changelog

## v1.0 (not tagged yet): Phase 5 code
- In-app accessibility disclosure with explicit consent before the settings screen opens
- `PRIVACY.md`, Play/GitHub distribution notes with demo-video script
- Release build: R8 + resource shrinking, signing from env vars or `keystore.properties`, tag-triggered GitHub release workflow
- Battery profiling script and guide (`scripts/battery_profile.sh`, `docs/battery-profiling.md`), crash/ANR checklist
- Architecture diagram (`docs/architecture.md`)
- Block screen redesigned: "The Slop has been Blocked!", gradient background, rotating "Did you know?" facts about AI-content harms, pill-style Peek button
- deSLOP: AI label must be in the lower half of the screen (ignores page titles); new caption-hint signal (#aivideo, #madewithcantina ...) via `aiHintStrings`
- Loud heads-up notification when the daily limit is reached (own channel)
- Dark theme by default; counter face changes with today's count (30/60/90)
- Counter overlay: top-centre by default, 1 sp larger; adaptive launcher icon from the deTOX logo
- Service logs `slow inspect: N ms` when a tree walk exceeds 50 ms (main-thread/ANR watch)
- Privacy policy wording corrected: reel hashes persist in the local database until data is cleared
- R8 keep rules for kotlinx.serialization models and the accessibility service
- Still required before tagging: real view IDs, the 60-reel test, battery numbers, a trained model (or ship without ML)

## v0.4 (in progress): Phase 4 code
- `ml-training/`: prepare (grouped split), train (MobileNetV3, reel-weighted, JPEG/blur aug), evaluate (precision-targeted threshold, confusion matrix), export (ai-edge-torch -> int8 via AI Edge Quantizer)
- App: `FrameGrabber` (takeScreenshot), `SlopClassifier` (TensorFlow Lite runtime, LiteRT-compatible API), `MlVerdictAggregator` (tested), sampler loop in the service
- Developer mode: save labelled model-input frames for dataset building
- Fix: tracker no longer re-polls every 450 ms forever after a reel is counted (`ReelCounter.isPending`)
- Not yet run: training/export scripts and the on-device model (needs your data + a build)

## v0.3 (in progress): Phase 3 code
- `SlopBlockController` (tested) + `SlopBlockOverlay`: opaque cover that lets swipes through, "Peek anyway" button
- Blocked-count stat feeds the dashboard; deSLOP toggle in settings
- Test plan: docs/deslop-test-plan.md

## v0.2 (in progress): Phase 2 code
- Daily limit with nudges at 50/80/100% (`LimitTracker`, tested; `LimitNotifier`)
- Dashboard: today, 7-day bars, per-app split, limit stepper
- `ProfileRepository`: cached remote config with validation and version check, bundled fallback (set `remote_config_url` in strings.xml)
- Not yet: time spent, YouTube profile verification on device

## v0.1 (in progress): Phase 0/1 scaffold
- Gradle + version catalog, Compose, Room, DataStore
- `AppProfile` JSON config (bundled `profiles.json`) for Instagram Reels and YouTube Shorts
- `ReelTrackerService` (AccessibilityService) with throttled inspection
- `ReelCounter`: 400 ms debounce + recent-hash dedupe, unit tested
- Draggable counter overlay, foreground keep-alive service
- Onboarding screen: accessibility + battery exemption + notification permission
