#!/usr/bin/env bash
# Checks that a commit's built pages (app/src/main/assets/map/) and protocol examples
# (app/src/test/resources/page-protocol.json) are exactly what its web/ builds, the way CI checks them:
#
#   - Orbit at the commit app/src/main/assets/map/orbit.json names, built with the Bun Orbit's own package.json
#     names (packageManager);
#   - the pages built with it, with the Bun web/package.json names;
#   - what that build wrote, compared with what the commit has (compare-pages.sh): every file, and orbit.json but for
#     its commit, whose short form can be longer in another clone, so its Orbit version and map format too.
#
# Another Bun bundles the same source differently, Orbit's or the pages': pages built with one fail CI's check, and a
# check made with one says pages built right don't match.
#
#   scripts/check-pages.sh [commit] [orbit]
#   scripts/check-pages.sh                      # HEAD, with Orbit from GitHub
#   scripts/check-pages.sh main ../orbit        # main, with Orbit from a clone that has orbit.json's commit
#
# Orbit comes from the second argument, a clone of it or its address, else ORBIT_REPOSITORY, else
# https://github.com/restless-dreams/orbit. Everything happens in a temporary folder, in a worktree of the commit
# and a clone of that Orbit: the checkout it runs in and the Orbit it's given stay as they are, so it checks the
# commit as it is, without what's changed since. It needs git, jq and mise (https://mise.jdx.dev), which runs each
# Bun without installing it for the whole machine.
set -euo pipefail

if [ $# -gt 2 ]; then
    echo "Usage: $0 [commit] [orbit]" >&2
    exit 2
fi

commit=${1:-HEAD}
orbit=${2:-${ORBIT_REPOSITORY:-https://github.com/restless-dreams/orbit.git}}
scripts=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

for tool in git jq; do
    if ! command -v "$tool" > /dev/null; then
        echo "check-pages.sh needs $tool." >&2
        exit 1
    fi
done

if ! command -v mise > /dev/null; then
    echo "check-pages.sh runs Orbit's Bun and the pages' with mise, which isn't here: install it from https://mise.jdx.dev," >&2
    echo "or build by hand as CONTRIBUTING.md says, Orbit with the Bun its package.json names and the pages with web/package.json's." >&2
    exit 1
fi

repository=$(git rev-parse --show-toplevel)

if ! checked=$(git -C "$repository" rev-parse --quiet --verify "$commit^{commit}"); then
    echo "There's no commit $commit here." >&2
    exit 1
fi

short=$(git -C "$repository" rev-parse --short "$checked")

if [ "$commit" = HEAD ] && [ -n "$(git -C "$repository" status --porcelain -- web app/src/main/assets/map app/src/test/resources/page-protocol.json)" ]; then
    echo "Checking $short, the last commit: web/ or the pages have changes since, which it leaves out."
fi

work=$(mktemp -d)

cleanup() {
    git -C "$repository" worktree remove --force "$work/app" > /dev/null 2>&1 || true
    rm -rf "$work"
}

trap cleanup EXIT

# Runs a step without its output, which shows when it fails.
quietly() {
    if ! "$@" > "$work/step.log" 2>&1; then
        cat "$work/step.log" >&2
        return 1
    fi
}

# The version a package.json's packageManager names: bun@1.4.3 is 1.4.3.
bun_of() {
    local version
    version=$(jq -r '.packageManager // "" | ltrimstr("bun@")' "$1")

    if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "$1 names no Bun in packageManager." >&2
        return 1
    fi

    echo "$version"
}

git -C "$repository" worktree add --quiet --detach "$work/app" "$checked"
app=$work/app
pages_bun=$(bun_of "$app/web/package.json")
orbit_commit=$(jq -r .commit "$app/app/src/main/assets/map/orbit.json")

# mise.toml pins the pages' Bun, for anyone building them, so the two have to agree.
if [ "$(sed -n 's/^bun = "\(.*\)"$/\1/p' "$app/mise.toml")" != "$pages_bun" ]; then
    echo "mise.toml and web/package.json pin different Bun versions: give both $pages_bun, or both another." >&2
    exit 1
fi

if [ -d "$orbit" ]; then
    # A clone on this machine: its commits come over without the network, and it stays as it is.
    git clone --quiet --no-checkout "$orbit" "$work/orbit"
else
    # Orbit keeps its pictures in Git LFS, which building it doesn't need: a partial clone brings every commit and no
    # files, and checking one out fetches only its own.
    git clone --quiet --no-checkout --filter=blob:none "$orbit" "$work/orbit"
fi

if ! git -C "$work/orbit" rev-parse --quiet --verify "$orbit_commit^{commit}" > /dev/null; then
    echo "$orbit has no Orbit $orbit_commit, which orbit.json names." >&2
    echo "Give a clone of Orbit that has it, as the second argument or ORBIT_REPOSITORY." >&2
    exit 1
fi

GIT_LFS_SKIP_SMUDGE=1 git -C "$work/orbit" -c advice.detachedHead=false checkout --quiet "$orbit_commit"
orbit_bun=$(bun_of "$work/orbit/package.json")

echo "Building Orbit $orbit_commit with Bun $orbit_bun, from $orbit"
(cd "$work/orbit" && quietly mise exec "bun@$orbit_bun" -- bun install --frozen-lockfile && quietly mise exec "bun@$orbit_bun" -- bun run build)

echo "Building the pages of $short with Bun $pages_bun"
(cd "$app/web" && quietly mise exec "bun@$pages_bun" -- bun install --frozen-lockfile && ORBIT_PATH="$work/orbit" quietly mise exec "bun@$pages_bun" -- bun run build)

# This checkout's comparison, which an older commit may not have.
if ! "$scripts/compare-pages.sh" "$app"; then
    echo "Rebuild them as CONTRIBUTING.md says, Orbit $orbit_commit with Bun $orbit_bun and the pages with Bun $pages_bun, and commit them with orbit.json." >&2
    exit 1
fi

echo "The pages and protocol examples of $short are what its web/ builds, with Orbit $(jq -r .version "$app/app/src/main/assets/map/orbit.json") ($orbit_commit)."
