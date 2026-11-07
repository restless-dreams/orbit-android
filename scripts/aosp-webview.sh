#!/usr/bin/env bash
# Makes the WebView that Android 10's emulator takes as an update of its own, for checks on the oldest Android and
# WebView Orbit supports: Chromium 101.0.4951.61, the closest above Orbit's minimum (Chromium 99,
# SystemWebView.MINIMUM_CHROMIUM) of the x86_64 prebuilts Android's open-source project shipped that Android 10
# installs. The project never shipped 99; its 100.0.4891.2 targets Android 13's preview, which Android 10 refuses.
#
#   scripts/aosp-webview.sh <apk to write>
#
# The emulator images without Google's apps (system-images;android-29;default;x86_64) draw web pages with
# com.android.webview, signed with the project's public test key, and Android installs an update of it only when
# it's signed with the same key: so the prebuilt, downloaded from the project's own repository and checked against
# its digest, is signed again with that key, also downloaded and checked. Then:
#
#   SMOKE_WEBVIEW=<that apk> scripts/smoke-test.sh <apk> <output folder> [journeys apk]
#
# It needs curl, base64 and the Android SDK's build tools, and downloads about 130 MB.
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <apk to write>" >&2
    exit 2
fi

out=$1
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
build_tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Where each file is in the project's repositories, at a commit or tag that names it for good, and its SHA-256.
aosp=https://android.googlesource.com/platform
webview="$aosp/external/chromium-webview/+/d87ab7af6a94f073acfea01933c03683db20da69/prebuilt/x86_64/webview.apk"
webview_sha256=6ac01ca3d191677da2172c6585ea8e277369d5305ea65f2f5ff88d1e65645b76
key="$aosp/build/+/refs/tags/android-15.0.0_r1/target/product/security/testkey"
key_sha256=495675d32e89a149d5abe191f4e9c0e218b9068714e9b53a7c91e164a0741a23
certificate_sha256=a4384ba815b9499a5ce349b4e33c1755278873fe2eac150a068823f526e6dbde

# Downloads a file from the repositories' web view, which serves it as Base64, and checks it.
fetch() {
    local address=$1 file=$2 sha256=$3
    curl --fail --silent --show-error --retry 3 --max-time 600 "$address?format=TEXT" | base64 -d > "$file"
    if ! echo "$sha256  $file" | sha256sum --check --quiet; then
        echo "$address isn't the file it should be: its SHA-256 is $(sha256sum "$file" | cut -d' ' -f1)" >&2
        exit 1
    fi
}

echo "Downloading Chromium 101.0.4951.61's WebView and the test key"
fetch "$webview" "$work/webview.apk" "$webview_sha256"
fetch "$key.pk8" "$work/testkey.pk8" "$key_sha256"
fetch "$key.x509.pem" "$work/testkey.x509.pem" "$certificate_sha256"

"$build_tools/apksigner" sign --key "$work/testkey.pk8" --cert "$work/testkey.x509.pem" --out "$work/signed.apk" "$work/webview.apk" 2> /dev/null
mkdir -p "$(dirname "$out")"
mv "$work/signed.apk" "$out"
echo "$out: com.android.webview 101.0.4951.61, signed with the test key"
