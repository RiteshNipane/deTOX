# deTOX

An Android digital-wellbeing tool that counts the short-form videos you watch in real time and can block AI-labelled or AI-looking content until you scroll past it.

## Demo

[Watch the demo video](https://github.com/RiteshNipane/deTOX/releases/download/v1.0.0/WhatsApp.Video.2026-10-23.at.4.34.44.PM.mp4)

## Download

[Download the APK (v1.0.0)](https://github.com/RiteshNipane/deTOX/releases/download/v1.0.0/app-debug.apk)

To install, allow installs from unknown sources, open the APK, then enable the deTOX accessibility service and overlay permission when prompted. This is a debug build and detection is still being tuned, so behaviour may vary by device and app version.
## Status

All phases are written (counter, stats and limits, label-based blocking, ML classifier, release setup). Two caveats:

- The view IDs in `app/src/main/assets/profiles.json` are placeholders and must be verified on a real device (see "Finding the real view IDs" below).
- Behaviour can differ across app versions and devices, because detection depends on the target apps' internal view hierarchy.

## Features

- **Live reel counter**: a small overlay at the top of the screen shows how many Reels or Shorts you have watched in the current session.
- **Stats and limits**: daily statistics stored locally, with configurable limits and notifications when you reach them.
- **deSLOP (label-based blocking)**: when a reel carries an "AI info" label, deTOX covers the screen with an opaque overlay until you scroll to the next item.
- **deSLOP (ML classifier)**: an on-device int8 TFLite model flags likely AI-generated content that has no label. If `slop_classifier.tflite` is not present, the app falls back to label detection only.
- **Config-driven detection**: per-app view IDs live in `profiles.json`, so detection fixes can ship as config changes instead of code changes.
- **Private by design**: all processing happens on the device. See [PRIVACY.md](PRIVACY.md).

## How it works

1. An accessibility service watches the foreground app and matches its view hierarchy against the app profiles in `profiles.json` to decide whether you are inside Reels or Shorts.
2. The reel detector and counter register each new reel as you scroll, and the counter overlay updates live. Limits are checked against the running totals, and counts are saved to a local Room database.
3. When blocking is enabled, the controller checks each reel for the AI label. If a frame classifier is bundled, sampled frames are scored on-device and the results are combined by a verdict aggregator.
4. On a positive verdict, the block overlay covers the screen until you scroll past the item.
5. A keep-alive service keeps the tracker running in the background.

## Tech stack

- Android, Accessibility Service, overlay windows
- Room for local storage, a settings store for preferences
- TensorFlow Lite (int8) for the on-device classifier
- Python for classifier training and export (`ml-training/`)
- GitHub Actions for tests and signed release builds

## Requirements

- A physical device running Android 11 or later
- Accessibility service permission (requested during onboarding)
- Overlay permission for the counter and block screens

## Getting started

1. Open the `detox/` folder in Android Studio. It generates the Gradle wrapper on first sync, or run `gradle wrapper --gradle-version 8.9` if you have Gradle installed.
2. Run the app on a physical device.
3. Enable the deTOX accessibility service when prompted.

## Finding the real view IDs

1. Open Instagram, go to Reels. In Android Studio, choose *Tools > Layout Inspector* and attach to `com.instagram.android`.
2. Note the pager or container view ID, the per-reel item ID, and where the "AI info" label appears. Repeat for YouTube Shorts.
3. Update `profiles.json`.
4. Exit criterion: you can reliably tell "in Reels" from "not in Reels".

## Project structure

```
app/src/main/java/com/detox/
├── service/   ReelTrackerService, KeepAliveService, LimitNotifier
├── detector/  AppProfile, ReelDetector, ReelCounter, LimitTracker, SlopBlockController
├── overlay/   CounterOverlay, SlopBlockOverlay
├── ml/        FrameGrabber, SlopClassifier, FrameStore, MlVerdictAggregator
├── data/      Room entities and DAO, SettingsStore
└── ui/        MainActivity (onboarding and settings), Dashboard
ml-training/   Python: prepare data, train, evaluate, export int8 TFLite
docs/          Architecture, test plan, battery profiling, distribution notes
```

## Documentation

- [Architecture](docs/architecture.md)
- [deSLOP test plan](docs/deslop-test-plan.md)
- [Battery profiling](docs/battery-profiling.md)
- [Classifier training](ml-training/README.md)
- [Distribution notes](docs/play-store-declaration.md)
- [Privacy policy](PRIVACY.md)

## Releasing

1. Create a keystore once and back it up. Losing it means you cannot update installed copies.
   ```
   keytool -genkey -v -keystore detox.jks -keyalg RSA -keysize 2048 -validity 10000 -alias detox
   ```
2. Local signed build: create a git-ignored `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, then run `./gradlew assembleRelease`. The APK lands in `app/build/outputs/apk/release/`.
3. CI release: add the repo secrets `DETOX_KEYSTORE_B64` (from `base64 -w0 detox.jks`), `DETOX_KEYSTORE_PASSWORD`, `DETOX_KEY_ALIAS`, and `DETOX_KEY_PASSWORD`, then run `git tag v1.0.0 && git push --tags`. The workflow runs the unit tests and attaches the APK to a GitHub release.
4. Commit the Gradle wrapper (`gradlew`, `gradle/wrapper/`) once Android Studio has generated it. CI needs it.
5. Fill in the contact line in `PRIVACY.md` before publishing.

## License

Add a license (for example MIT) before making the repository public.
