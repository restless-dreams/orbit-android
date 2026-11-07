#!/usr/bin/env bash
# Runs a command on an emulator of its own, which it starts and stops, whatever else adb has: other emulators on the
# same machine never stand in for it. One boot serves the smoke test of each release build and the device tests.
#
#   scripts/with-emulator.sh <command> [its arguments…]
#
#   - SMOKE_AVD is the virtual device, made from SMOKE_IMAGE when it doesn't exist
#     (system-images;android-35;google_apis;x86_64), and wiped as it starts: one no one else uses;
#   - SMOKE_PORT is its port (5640);
#   - SMOKE_LOG is where the emulator's own output goes (emulator.log).
#
# The command runs with ANDROID_SERIAL set to the emulator, and its exit status is this script's.
set -euo pipefail

if [ $# -eq 0 ] || [ -z "${SMOKE_AVD:-}" ]; then
    echo "Usage: SMOKE_AVD=<virtual device> $0 <command> [arguments…]" >&2
    exit 2
fi

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
adb=$sdk/platform-tools/adb
avd=$SMOKE_AVD
port=${SMOKE_PORT:-5640}
log=${SMOKE_LOG:-emulator.log}

if "$adb" devices | grep -q "^emulator-$port"; then
    echo "Another emulator already uses port $port: give SMOKE_PORT another even port." >&2
    exit 1
fi

if ! "$sdk/emulator/emulator" -list-avds | grep -qx "$avd"; then
    image=${SMOKE_IMAGE:-system-images;android-35;google_apis;x86_64}
    echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" --silent create avd --name "$avd" --package "$image" --device pixel_7 > /dev/null
fi

# A clean phone every time: its data is wiped as it starts, and nothing is saved when it stops. Its Wi-Fi and Bluetooth
# don't go through netsim, the network simulator every emulator on the machine shares: one netsim that has run for a
# long time made new emulators crash as they started, and nothing run here needs Bluetooth. Should the emulator still
# stop as it starts, before Android is up, that start is tried once more. Only the start is: what runs on the emulator
# is never run twice.
mkdir -p "$(dirname "$log")"
export ANDROID_SERIAL=emulator-$port
emulator=
trap '"$adb" emu kill > /dev/null 2>&1 || true; [ -z "$emulator" ] || wait "$emulator" 2> /dev/null || true' EXIT

# Starts the emulator and waits until Android has started on it. Fails when the emulator stops first, or after 8 minutes.
boot() {
    echo "--- starting $avd on port $port" >> "$log"
    "$sdk/emulator/emulator" -avd "$avd" -port "$port" -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
        -gpu swiftshader_indirect -feature -WiFiPacketStream,-BluetoothEmulation >> "$log" 2>&1 &
    emulator=$!
    echo "Waiting for $avd to start on port $port"

    for _ in $(seq 1 240); do
        if ! kill -0 "$emulator" 2> /dev/null; then
            wait "$emulator" 2> /dev/null || true
            emulator=
            return 1
        fi

        if [ "$("$adb" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = 1 ]; then
            return 0
        fi

        sleep 2
    done

    return 1
}

if ! boot; then
    echo "The emulator stopped, or didn't finish starting: starting it again"
    "$adb" emu kill > /dev/null 2>&1 || true
    [ -z "$emulator" ] || wait "$emulator" 2> /dev/null || true

    if ! boot; then
        echo "The emulator didn't start: see $log" >&2
        exit 1
    fi
fi

# The command's own scripts start no emulator of their own.
SMOKE_AVD= "$@"
