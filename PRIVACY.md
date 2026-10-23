# deTOX Privacy Policy

_Last updated: 2026-10-21. Replace the contact line below before publishing._

deTOX is a digital-wellbeing app that counts the short-form videos (Instagram Reels, YouTube Shorts) you watch and can optionally hide AI-generated ones. It is built so that your data stays on your phone.

## What deTOX accesses
Through Android's accessibility service, and only while Instagram or YouTube is in the foreground, deTOX reads the on-screen structure (view IDs, text and content descriptions) to:
- detect that you are in Reels / Shorts and count each reel,
- find labels such as "AI info" or "Altered or synthetic content" (only if you enable deSLOP).

If you enable deSLOP's AI detector, deTOX also takes a screenshot roughly every 1.5 seconds while you are in Reels / Shorts and runs it through a model **on your phone**. The screenshot is discarded right after classification.

deTOX does **not** read your messages, comments, passwords, or any app other than Instagram and YouTube, and it does not perform actions on your behalf.

## What deTOX stores (on your phone only)
- Counts per day and per app, and the number of reels blocked.
- A timestamp and a numeric hash of each counted reel's on-screen text, kept in the app's local database and used to avoid counting the same reel twice. The hash cannot be turned back into text. It is kept until you clear the app's data or uninstall.
- Your settings (limit, toggles, overlay position).
- **Developer mode only:** if you switch on "Save training frames", screenshots are saved to the app's own folder on your phone (Android/data/com.detox/files/frames) so you can build a training set. This is off by default, and the files leave your phone only if you copy them yourself.

## What deTOX sends off your phone
Nothing about you. There are no accounts, no analytics, no advertising, and no crash-reporting service.

The one network request is optional: if a remote-config URL is set in the build, the app downloads a small JSON file describing where Instagram/YouTube put things on screen, so it can keep working after those apps change. This request sends no personal data (your device's IP address is visible to the server hosting the file, as with any web request). Builds without a URL make no network requests.

## Sharing
deTOX shares no data with anyone, because it never leaves your device.

## Your control
- Turn deTOX off any time: Android Settings > Accessibility > deTOX.
- Delete all stored data: Android Settings > Apps > deTOX > Storage > Clear data, or uninstall.

## Children
deTOX is not directed at children under 13 and collects no personal information from anyone.

## Changes
If this policy changes, the new version will be published in the project repository with an updated date.

## Contact
`<your email or GitHub issues URL>`
