# Distribution notes: Play Store vs GitHub APK

**Recommended for v1.0: GitHub release APK first.** Google Play restricts accessibility-service apps heavily, and a
digital-wellbeing counter is not one of the pre-approved categories, so review is uncertain and slow. Ship on GitHub,
learn from real use, then try Play. Policy changes often, so re-read the current rules before submitting:
https://support.google.com/googleplay/android-developer/answer/10964491

## If you submit to Play
Expect to provide, all of which must be truthful and match the app's real behavior:

1. **Prominent in-app disclosure** before the accessibility screen opens. Done: the "Accessibility permission" dialog in `MainActivity`, with an explicit agree button. Keep its wording in sync with the code.
2. **Permissions declaration form** explaining why the accessibility API is needed and that it is core to the app's functionality.
3. **Demo video** (under ~2 minutes) showing: the disclosure dialog, the user granting the service in settings, the counter overlay appearing in Reels, the dashboard, and (if shipped) a blocked reel with "Peek anyway".
4. **Privacy policy URL**: publish `PRIVACY.md` (GitHub Pages or the repo's rendered file works) and fill in the contact line.
5. **Data safety form**: collects no data, shares no data. If you enable the remote-config URL, say the app makes a network request without personal data.
6. **Foreground service declaration** (`specialUse`): the manifest already carries the subtype text; the form needs the same explanation.

Things reviewers may question, so be ready to explain them: screenshots (`canTakeScreenshot`) are processed on-device and discarded; deTOX reads only two named packages; it performs no gestures or clicks for the user.

## Video script (about 90 seconds)
1. Home screen, open deTOX. Show the setup card. (5 s)
2. Tap Continue, read the disclosure aloud, tap I agree. (15 s)
3. Android accessibility settings: enable deTOX, accept the system dialog. (10 s)
4. Open Instagram Reels, scroll; point out the draggable counter increasing. (20 s)
5. Back in deTOX, show the dashboard and the daily-limit stepper. (10 s)
6. Enable deSLOP; scroll to an AI-labelled reel, show the block and "Peek anyway". (20 s)
7. Disable the service in settings to show the user stays in control. (10 s)

## GitHub release checklist
- [ ] Phases 0-3 verified on a real phone (real view IDs in `profiles.json`)
- [ ] `PRIVACY.md` contact line filled in
- [ ] Keystore created and stored safely (see README "Releasing")
- [ ] Version bumped in `app/build.gradle.kts`, `CHANGELOG.md` updated
- [ ] `git tag v1.0.0 && git push --tags` (the release workflow builds and attaches the signed APK)
- [ ] Demo video and screenshots added to the release notes
