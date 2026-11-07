#!/usr/bin/env bash
# Runs a minified release build the way a phone does, since R8 can break a library that works in debug builds
# (AGENTS.md, "Test the release build"), and only a launched build shows it:
#
#   - a fresh install;
#   - a cold start to Home;
#   - the welcome map opened, until its page has drawn its planets, and closed again with Back, Orbit still in front;
#   - a second cold start;
#   - with the journeys' APK, the journeys in journeys/: an idea added on a map, edited, found with its search and
#     there again after a restart; a panel open as the phone turns; a file opened with Orbit and text and a file
#     shared to it; an idea caught from Quick Settings; and a map's screen, and a private map's, as Android brings
#     them back after stopping Orbit's process;
#   - the crash log, which has to stay empty, and no "Orbit isn't responding".
#
# On a WebView too old to draw maps (older than Chromium 99), it checks Orbit says so instead: on Home, and in the
# welcome map's place, which it still opens and closes. The journeys need maps drawn, and are left out there.
#
# Under load, the emulator's own apps (its launcher, System UI) can stop responding as it starts: their dialog is
# closed, and the log says which app it was.
#
#   scripts/smoke-test.sh <apk> <folder for the screenshots and logs> [journeys apk]
#
# It runs on the device adb has (ANDROID_SERIAL picks one of several), or, with SMOKE_AVD set, on an emulator of
# its own that it starts and stops, whatever else adb has (scripts/with-emulator.sh). With SMOKE_WEBVIEW set to a
# WebView's APK, it installs that first as an update of the device's own WebView, as on Android 10 with a newer one
# than it came with (CONTRIBUTING.md, "Device tests"). An APK without a signature, as release builds are without the
# upload key, is installed signed with a throwaway key. Build the journeys with ./gradlew :journeys:assembleDebug.
set -euo pipefail

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
    echo "Usage: $0 <apk> <output folder> [journeys apk]" >&2
    exit 2
fi

# An emulator of its own, which other emulators on the same machine never stand in for.
if [ -n "${SMOKE_AVD:-}" ]; then
    mkdir -p "$2"
    SMOKE_LOG=${SMOKE_LOG:-$2/emulator.log} exec "$(dirname "$0")/with-emulator.sh" "$0" "$@"
fi

apk=$(realpath "$1")
out=$2
mkdir -p "$out"
out=$(realpath "$out")
journeys=${3:+$(realpath "$3")}
package=com.hyuchia.orbit
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
adb=$sdk/platform-tools/adb
build_tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
# The oldest Chromium Orbit's pages draw maps in (SystemWebView.MINIMUM_CHROMIUM).
minimum_chromium=99

# Says what went wrong, with the crash log when Orbit crashed, and keeps the screen and the log.
fail() {
    echo "Smoke test failed: $*" >&2
    "$adb" exec-out screencap -p > "$out/failure.png" 2> /dev/null || true
    "$adb" logcat -d > "$out/logcat.txt" 2> /dev/null || true
    "$adb" logcat -b crash -d > "$out/crash.txt" 2> /dev/null || true
    if [ -s "$out/crash.txt" ]; then
        echo "The crash log:" >&2
        cat "$out/crash.txt" >&2
    fi
    exit 1
}

echo "Waiting for the device to start"
timeout 300 "$adb" wait-for-device
for _ in $(seq 1 150); do
    if [ "$("$adb" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = 1 ]; then
        break
    fi
    sleep 2
done
[ "$("$adb" shell getprop sys.boot_completed | tr -d '\r')" = 1 ] || fail "the device didn't finish starting"

# A newer WebView than the device came with, installed as an update of its own: Android 10 with Chromium 101, say.
# Android takes it up once it has prepared it.
if [ -n "${SMOKE_WEBVIEW:-}" ]; then
    "$adb" install -r -d "$SMOKE_WEBVIEW" > /dev/null || fail "the WebView in $SMOKE_WEBVIEW didn't install as an update of the device's own"
    version=$("$build_tools/aapt2" dump badging "$SMOKE_WEBVIEW" 2> /dev/null | grep -oE "versionName='[^']*'" | head -1 | cut -d"'" -f2)
    for _ in $(seq 1 60); do
        if "$adb" shell dumpsys webviewupdate | grep -q "Current WebView package.*, $version)"; then
            break
        fi
        sleep 1
    done
fi

webview=$("$adb" shell dumpsys webviewupdate | grep -m1 -oE 'Current WebView package.*' | tr -d '\r')
echo "Android $("$adb" shell getprop ro.build.version.release | tr -d '\r'), $webview"
chromium=$(echo "$webview" | grep -oE ', [0-9]+\.' | tr -dc '0-9')
if [ -n "$chromium" ] && [ "$chromium" -lt "$minimum_chromium" ]; then
    old_webview=1
    echo "Chromium $chromium can't draw maps: Orbit has to say so instead"
else
    old_webview=
fi
"$adb" shell input keyevent KEYCODE_WAKEUP
"$adb" shell wm dismiss-keyguard > /dev/null 2>&1 || true
# Animations only slow the steps down.
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$adb" shell settings put global "$scale" 0
done

# A release without the upload key is unsigned, and Android installs nothing unsigned.
if ! "$build_tools/apksigner" verify "$apk" > /dev/null 2>&1; then
    keys=$(mktemp -d)
    keytool -genkeypair -keystore "$keys/smoke.jks" -storepass smoke-test -keypass smoke-test -alias smoke \
        -keyalg RSA -keysize 2048 -validity 1 -dname CN=Smoke > /dev/null 2>&1
    "$build_tools/apksigner" sign --ks "$keys/smoke.jks" --ks-pass pass:smoke-test --out "$keys/signed.apk" "$apk"
    apk=$keys/signed.apk
fi

"$adb" uninstall "$package" > /dev/null 2>&1 || true
"$adb" install "$apk" > /dev/null || fail "the APK didn't install"
"$adb" logcat -b all -c

# Prints the centre and the label of the first node in the last screen dump whose text or description (only its
# text, with a third argument "text") matches the pattern, or fails when there's none.
find_node() {
    python3 - "$out/screen.xml" "$1" "${2:-text|content-desc}" <<'PYTHON'
import html
import re
import sys

xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.finditer(r"<node [^>]*>", xml):
    attributes = node.group(0)
    label = " ".join(html.unescape(value) for value in re.findall(f' (?:{sys.argv[3]})="([^"]*)"', attributes) if value)
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attributes)
    if bounds and re.search(sys.argv[2], label):
        left, top, right, bottom = map(int, bounds.groups())
        if right > left and bottom > top:
            print((left + right) // 2, (top + bottom) // 2, label)
            sys.exit(0)
sys.exit(1)
PYTHON
}

# Under load, the emulator's own apps (its launcher, System UI) can stop responding as it starts, and their
# dialog covers Orbit: closes it, and says which app it was. Orbit not responding fails the run.
close_hung_apps() {
    local close hung closed=0
    while [ $closed -lt 5 ] && close=$(find_node '^Close app$'); do
        hung=$(find_node "isn't responding" text | cut -d' ' -f3- || true)
        case "$hung" in
            *Orbit*) fail "$hung" ;;
        esac
        echo "Closed another app that stopped responding: ${hung:-a system dialog}" >&2
        read -r x y _ <<< "$close"
        "$adb" shell input tap "$x" "$y"
        closed=$((closed + 1))
        sleep 2
        dump
    done
}

dump() {
    "$adb" shell uiautomator dump /sdcard/screen.xml > /dev/null 2>&1 || true
    "$adb" shell cat /sdcard/screen.xml > "$out/screen.xml" 2> /dev/null || true
}

# Waits until a node whose text or description (only its text, with a third argument "text") matches the
# pattern is on screen, closing other apps' dialogs on the way, and prints its centre.
screen() {
    local pattern=$1 seconds=$2 fields=${3:-text|content-desc} found
    local deadline=$((SECONDS + seconds))
    while [ $SECONDS -lt $deadline ]; do
        dump
        close_hung_apps
        if found=$(find_node "$pattern" "$fields"); then
            echo "$found" | cut -d' ' -f1,2
            return 0
        fi
        sleep 1
    done
    return 1
}

# Whether the last screen dump worked: one that failed leaves its file empty, which no node matches.
dumped() {
    grep -q '<hierarchy' "$out/screen.xml" 2> /dev/null
}

# Waits until no node whose text or description (only its text, with a third argument "text") matches the pattern
# is on screen, closing other apps' dialogs on the way.
gone() {
    local pattern=$1 seconds=$2 fields=${3:-text|content-desc}
    local deadline=$((SECONDS + seconds))
    while [ $SECONDS -lt $deadline ]; do
        dump
        close_hung_apps
        if dumped && ! find_node "$pattern" "$fields" > /dev/null; then
            return 0
        fi
        sleep 1
    done
    return 1
}

# Whether Orbit is the app in front. Back that leaves it puts the launcher there, as Back on a tablet with the list
# beside a map once did.
in_front() {
    "$adb" shell dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity' | grep -q "$package/"
}

start() {
    local result
    result=$("$adb" shell am start -W -n "$package/.MainActivity" 2>&1 | tr -d '\r' || true)
    echo "$result" | grep -E 'TotalTime|WaitTime' || true
    echo "$result" | grep -q 'Status: ok' || fail "Orbit didn't start: $result"
}

# Home is up once its search is; the welcome map's card is the first map a new install has. The emulator speaks
# English unless it's told otherwise.
home='^Search your maps'

echo "First start"
start
screen "$home" 90 > /dev/null || fail "Home didn't show"
screen '^👋 Welcome to Orbit$' 30 text > "$out/welcome.txt" || fail "Home didn't show the welcome map"
"$adb" exec-out screencap -p > "$out/1-home.png"

if [ -n "$old_webview" ]; then
    # Home says what to update, and that the maps are all still there.
    screen '^Update .* to see your maps$' 30 text > /dev/null || fail "Home didn't say the WebView is too old to draw maps"
    "$adb" exec-out screencap -p > "$out/1-old-webview-on-home.png"
fi

echo "Opening the welcome map"
read -r x y < "$out/welcome.txt"
"$adb" shell input tap "$x" "$y"

if [ -n "$old_webview" ]; then
    # In the map's place, once its screen is open, the same notice, and no page.
    screen 'Back to your maps' 30 > /dev/null || fail "the welcome map's screen didn't open"
    screen '^Update .* to see your maps$' 30 text > /dev/null || fail "the welcome map didn't say the WebView is too old to draw maps"
    gone '^Add an idea to ' 5 text || fail "a page drew the welcome map on a WebView too old for it"
    "$adb" exec-out screencap -p > "$out/2-old-webview-in-the-map.png"
else
    # The map's page draws its planets for TalkBack too, so once it has loaded, its + reads "Add an idea to …" as
    # text (the toolbar's button says it in its description).
    screen '^Add an idea to ' 90 text > /dev/null || fail "the welcome map's page didn't draw"
    sleep 2
    "$adb" exec-out screencap -p > "$out/2-welcome-map.png"
fi

echo "Closing it with Back"
"$adb" shell input keyevent KEYCODE_BACK
# Home showing isn't enough: on a tablet, its list is beside the map all along. The map's page goes, and Orbit stays.
gone '^Add an idea to |Back to your maps' 30 || fail "Back didn't close the welcome map"
in_front || fail "Back left Orbit instead of closing the welcome map"
screen "$home" 30 > /dev/null || fail "Back didn't return to Home"
"$adb" exec-out screencap -p > "$out/3-map-closed.png"

echo "Second start"
"$adb" shell am force-stop "$package"
start
screen "$home" 60 > /dev/null || fail "Home didn't show after the second start"
screen '^👋 Welcome to Orbit$' 30 text > /dev/null || fail "Home didn't list the welcome map after the second start"
"$adb" exec-out screencap -p > "$out/4-second-start.png"
sleep 3

# The journeys, in a process of their own (journeys/), each from a fresh install of this build.
if [ -n "$journeys" ] && [ -z "$old_webview" ]; then
    echo "The journeys"
    "$adb" uninstall "$package.journeys" > /dev/null 2>&1 || true
    "$adb" install -t "$journeys" > /dev/null || fail "the journeys didn't install"
    captures=/sdcard/Android/media/$package.journeys/failures
    "$adb" shell rm -rf "$captures"
    "$adb" shell am instrument -w "$package.journeys/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee "$out/journeys.txt"
    if ! grep -q '^OK (' "$out/journeys.txt"; then
        "$adb" pull "$captures" "$out/journeys-failures" > /dev/null 2>&1 || true
        fail "a journey failed: the screen and its layout as it failed are in $out/journeys-failures"
    fi
    "$adb" uninstall "$package.journeys" > /dev/null 2>&1 || true
elif [ -n "$journeys" ]; then
    echo "The journeys need maps drawn, which this WebView can't: they're left out"
fi

"$adb" logcat -d > "$out/logcat.txt"
"$adb" logcat -b crash -d > "$out/crash.txt"
# Only Orbit's crashes count: the emulator's own apps can crash too, under load or when one of them hangs.
if grep -q -E "Process: $package( |,|$)|>>> $package <<<" "$out/crash.txt" || grep -q "ANR in $package" "$out/logcat.txt"; then
    grep -A 20 "ANR in $package" "$out/logcat.txt" >&2 || true
    fail "Orbit crashed or stopped responding"
fi
others=$( (grep -oE 'Process: [A-Za-z0-9_.:]+' "$out/crash.txt" || true) | cut -d' ' -f2 | sort -u | tr '\n' ' ')
if [ -n "$others" ]; then
    echo "Other apps crashed meanwhile, which doesn't count: $others"
fi
"$adb" shell pidof "$package" > /dev/null || fail "Orbit isn't running after its second start"
echo "Smoke test passed: no crash, and the screenshots are in $out"
