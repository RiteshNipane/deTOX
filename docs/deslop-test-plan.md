# deSLOP v1 test plan (Phase 3 exit)

Target: >= 90% of labelled AI reels blocked, near-zero false positives.

## 1. Confirm label text first (Phase 0 spike)
For each app, find a reel that shows an AI label and use Layout Inspector to see:
- which node carries the label text (`text` vs `contentDescription`)
- that it sits inside the per-reel container in `reelItemIds` (the detector only reads the on-screen item)
- the exact string. Put every variant into `aiLabelStrings` in `profiles.json`.

Matching is case-insensitive substring, English UI only. If your phone is not in English, add the localised strings.

## 2. Build the test set
- 30 reels that visibly carry an AI label (Instagram "AI info", Shorts "Altered or synthetic content")
- 30 normal reels, mixed topics, including some captions that mention "AI" in plain text (these are the false-positive risk)

## 3. Run
1. Enable deSLOP, open the app, scroll through all 60 reels once.
2. Note per reel: blocked (Y/N). Logcat tag `deTOX` prints "AI label detected" for each block.
3. Detection rate = blocked labelled / 30. False positives = blocked normal / 30.
4. Dashboard "Blocked today" should equal your blocked tally.

## Known limits of v1
- A frame or two of the reel can show before the label is detected.
- Audio keeps playing under the block.
- Label only appears on reels the creator or platform labelled; unlabelled AI content needs the Phase 4 classifier.
- Substring matching can false-positive on captions containing a label string; tighten `aiLabelStrings` or match on the specific node if that happens.
