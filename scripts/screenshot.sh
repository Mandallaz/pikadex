#!/usr/bin/env bash
# Installs the current debug build on the running pikadex_test emulator, launches it, and pulls a
# screenshot + uiautomator dump to a given output path (default: /tmp/pikadex-screenshot).
#
# Usage: scripts/screenshot.sh [output-basename]
# Produces <output-basename>.png and <output-basename>.xml
set -euo pipefail

SDK=/Users/tom/Library/Android/sdk
ADB="$SDK/platform-tools/adb"
PKG=com.mandallaz.pikadex
OUT="${1:-/tmp/pikadex-screenshot}"

"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 2
"$ADB" shell screencap -p /sdcard/screenshot.png
"$ADB" pull /sdcard/screenshot.png "${OUT}.png"
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
"$ADB" pull /sdcard/ui.xml "${OUT}.xml"

echo "Saved ${OUT}.png and ${OUT}.xml"
