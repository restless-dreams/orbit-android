#!/usr/bin/env bash
# Runs the device tests on the device adb has (ANDROID_SERIAL picks one of several), with Orbit's debug build, its
# maps drawn by the device's own WebView, as no unit test can:
#
#   - the app's own (app/src/androidTest), in Orbit's process: a map loading and drawing, an idea typed on the map
#     and saved to the map's file, a page that doesn't load, a page whose process Android stops, and a job the
#     page never finishes;
#   - from the journeys (journeys/), in a process of their own: a map's screen as Android brings it back after it
#     stopped Orbit's process;
#   - the crash log, which has to stay empty of Orbit's crashes.
#
#   scripts/device-tests.sh <debug apk> <its androidTest apk> <journeys apk> <folder for the logs and screenshots>
#
# Build them with ./gradlew assembleLibreDebug assembleLibreDebugAndroidTest :journeys:assembleDebug. Debug builds
# put in the city library beside the welcome map, which the tests leave alone. It takes an emulator or a phone on
# Android 10 or later with a WebView that draws maps (Chromium 99 or later), and replaces the Orbit there, its maps
# included. scripts/smoke-test.sh runs the minified release builds.
set -euo pipefail

if [ $# -ne 4 ]; then
    echo "Usage: $0 <debug apk> <androidTest apk> <journeys apk> <output folder>" >&2
    exit 2
fi

app=$(realpath "$1")
tests=$(realpath "$2")
journeys=$(realpath "$3")
out=$4
mkdir -p "$out"
out=$(realpath "$out")
package=com.hyuchia.orbit
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
adb=$sdk/platform-tools/adb
# Where the tests leave a screenshot and the screen's layout of each that fails: Orbit's media folder, for its own
# tests, and the journeys'.
captures="/sdcard/Android/media/$package/failures /sdcard/Android/media/$package.journeys/failures"

fail() {
    echo "Device tests failed: $*" >&2
    mkdir -p "$out/failures"
    for folder in $captures; do
        "$adb" pull "$folder/." "$out/failures" > /dev/null 2>&1 || true
    done
    echo "The screen and its layout as each test failed are in $out/failures" >&2
    "$adb" logcat -d > "$out/logcat.txt" 2> /dev/null || true
    "$adb" logcat -b crash -d > "$out/crash.txt" 2> /dev/null || true
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
echo "Android $("$adb" shell getprop ro.build.version.release | tr -d '\r'), $("$adb" shell dumpsys webviewupdate | grep -m1 -oE 'Current WebView package.*' | tr -d '\r')"
"$adb" shell input keyevent KEYCODE_WAKEUP
"$adb" shell wm dismiss-keyguard > /dev/null 2>&1 || true
# Animations only slow the steps down.
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$adb" shell settings put global "$scale" 0
done

# A fresh install, with the welcome map, and the tests beside it.
for installed in "$package" "$package.test" "$package.journeys"; do
    "$adb" uninstall "$installed" > /dev/null 2>&1 || true
done
"$adb" install "$app" > /dev/null || fail "the debug build didn't install"
"$adb" install -t "$tests" > /dev/null || fail "the device tests didn't install"
"$adb" install -t "$journeys" > /dev/null || fail "the journeys didn't install"
"$adb" shell rm -rf $captures
"$adb" logcat -b all -c

# Runs one instrumentation, saving what it says; passes only when JUnit says OK.
instrument() {
    local name=$1
    shift
    echo "Running $name"
    # Each test gives up after five minutes; the whole run, after half an hour, in case Orbit's process hangs.
    timeout 1800 "$adb" shell am instrument -w "$@" | tr -d '\r' | tee "$out/$name.txt"
    grep -q '^OK (' "$out/$name.txt"
}

instrument app-tests "$package.test/androidx.test.runner.AndroidJUnitRunner" || fail "the app's device tests failed"
instrument process-death -e class "$package.journeys.ProcessDeathJourney" "$package.journeys/androidx.test.runner.AndroidJUnitRunner" ||
    fail "the process death journey failed"

"$adb" logcat -d > "$out/logcat.txt"
"$adb" logcat -b crash -d > "$out/crash.txt"
# Only Orbit's crashes count: the emulator's own apps can crash too, under load. A test that stops Orbit's page's
# process on purpose crashes no app: Android only reports it as the page's.
if grep -q -E "Process: $package( |,|$)|>>> $package <<<" "$out/crash.txt" || grep -q "ANR in $package" "$out/logcat.txt"; then
    grep -A 20 "ANR in $package" "$out/logcat.txt" >&2 || true
    fail "Orbit crashed or stopped responding"
fi
echo "Device tests passed: the logs are in $out"
