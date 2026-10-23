# Architecture

```mermaid
flowchart LR
    subgraph Apps["Instagram / YouTube"]
        UI[Reels / Shorts screen]
    end

    UI -- accessibility events --> RTS[ReelTrackerService]
    RTS --> RD[ReelDetector]
    PR[ProfileRepository<br/>profiles.json: bundled + cached remote] --> RD
    RD -- snapshot: inReels, contentHash, hasAiLabel --> RTS

    RTS --> RC[ReelCounter<br/>debounce + dedupe]
    RC --> DB[(Room: ReelEvent, DailyStat)]
    RTS --> LT[LimitTracker] --> LN[LimitNotifier]
    RTS --> CO[CounterOverlay]

    RTS -- every ~1.5 s, deSLOP on --> FG[FrameGrabber<br/>takeScreenshot]
    FG --> SC[SlopClassifier<br/>int8 TFLite, on-device]
    SC --> MV[MlVerdictAggregator]
    MV --> SBC[SlopBlockController]
    RD -- AI label seen --> SBC
    SBC --> SBO[SlopBlockOverlay<br/>cover + Peek anyway]

    DB --> DASH[Dashboard - Compose]
    DS[(DataStore: Settings)] <--> RTS
    DS <--> DASH
    KAS[KeepAliveService] -. keeps process alive .- RTS
```

## Key ideas
- **App-specific knowledge is data.** View IDs, labels and package names live in `profiles.json`; a UI change in Instagram is a config fix.
- **Logic is pure where it can be.** `ReelCounter`, `LimitTracker`, `SlopBlockController`, and `MlVerdictAggregator` have no Android types and are unit tested.
- **Two block signals, one controller.** The label detector and the ML model both feed `SlopBlockController`, which owns the "peek" and "dismiss on next reel" behavior.
- **Everything stays on the device.** No analytics, no accounts; the optional remote config fetches a JSON file only.

## Training side
```mermaid
flowchart LR
    A[Developer mode:<br/>save labelled frames] --> B[prepare_data.py<br/>grouped split]
    P[Public sets: CIFAKE, GenImage] --> B
    B --> C[train.py<br/>MobileNetV3, reel-weighted]
    C --> D[evaluate.py<br/>precision-targeted threshold]
    D --> E[export_tflite.py<br/>int8 + parity check]
    E --> F[app/src/main/assets/<br/>slop_classifier.tflite]
```
