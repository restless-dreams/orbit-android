#!/usr/bin/env bash
# Compares the pages web/ has just built in a checkout (app/src/main/assets/map/, and the protocol's examples in
# app/src/test/resources/page-protocol.json) with the ones its last commit has, as check-pages.sh and CI do once
# they've built them:
#
#   - every file has to be the same;
#   - and orbit.json too, all but the commit it names, whose short form can be longer in another clone: its Orbit
#     version, and the newest map format that Orbit reads (format, which the app's folder records say the app
#     reads), are what the Orbit at that commit builds.
#
#   scripts/compare-pages.sh [checkout]        # the checkout the current folder is in, unless another is given
#
# It prints what differs and exits with 1, or exits with 0 when the pages are the commit's. It needs git and jq.
set -euo pipefail

if [ $# -gt 1 ]; then
    echo "Usage: $0 [checkout]" >&2
    exit 2
fi

checkout=${1:-$(git rev-parse --show-toplevel)}
orbit_json=app/src/main/assets/map/orbit.json
changes=$(git -C "$checkout" status --porcelain -- app/src/main/assets/map app/src/test/resources/page-protocol.json ":(exclude)$orbit_json")

if [ -n "$changes" ]; then
    echo "The committed pages or protocol examples differ from what web/ builds:" >&2
    echo "$changes" >&2
    exit 1
fi

# Every field but the commit, keys in order, so a field the build writes and the commit doesn't have differs too.
committed=$(git -C "$checkout" show "HEAD:$orbit_json" | jq --compact-output --sort-keys 'del(.commit)')
built=$(jq --compact-output --sort-keys 'del(.commit)' "$checkout/$orbit_json")

if [ "$committed" != "$built" ]; then
    echo "orbit.json says $committed, but the Orbit commit it names builds $built." >&2
    exit 1
fi
