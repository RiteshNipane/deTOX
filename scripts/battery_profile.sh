#!/usr/bin/env bash
# Measures deTOX's share of battery use over a scrolling session. Needs adb + a USB-connected phone.
#
#   scripts/battery_profile.sh [minutes]      (default 10)
#
# While it runs you scroll Reels/Shorts normally. The script resets Android's battery stats,
# waits, then prints deTOX's estimated power use and wakeups/CPU time. Run it twice (deSLOP off, then on)
# and once with deTOX disabled for a baseline; compare the numbers.
set -euo pipefail
PKG=com.detox
MIN=${1:-10}

command -v adb >/dev/null || { echo "adb not found"; exit 1; }
adb get-state >/dev/null

echo "Unplugging simulation + resetting stats..."
adb shell dumpsys battery unplug >/dev/null
adb shell dumpsys batterystats --reset >/dev/null
adb shell dumpsys batterystats --enable full-wake-history >/dev/null || true

START_LEVEL=$(adb shell dumpsys battery | awk '/level:/ {print $2}' | tr -d '\r')
echo "Battery at start: ${START_LEVEL}%. Scroll Reels/Shorts for ${MIN} minutes (starting now)."
sleep $((MIN * 60))

END_LEVEL=$(adb shell dumpsys battery | awk '/level:/ {print $2}' | tr -d '\r')
UID_NUM=$(adb shell dumpsys package "$PKG" | awk -F'[= ]' '/userId=/ {print $4; exit}' | tr -d '\r')

OUT="battery_$(date +%Y%m%d_%H%M%S).txt"
adb shell dumpsys batterystats --charged "$PKG" > "$OUT"
adb shell dumpsys battery reset >/dev/null

echo
echo "=== Summary (${MIN} min) ==="
echo "Device battery: ${START_LEVEL}% -> ${END_LEVEL}%  (whole phone incl. screen, Instagram/YouTube)"
echo "--- deTOX lines from batterystats (uid ${UID_NUM}) ---"
grep -E "Estimated power use|Uid u0a|${PKG}|Wake lock|CPU:" "$OUT" | head -30 || true
echo
echo "Full dump saved to $OUT"
echo "Target: deTOX < 3% of battery per hour => over ${MIN} min, deTOX 'Uid' mAh should be under roughly"
echo "  (3% of your battery capacity) * ${MIN}/60. Capacity: adb shell dumpsys batterystats | grep 'Capacity'"
