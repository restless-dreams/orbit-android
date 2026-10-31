# Orbit for Android on F-Droid

F-Droid builds the libre build of each tagged release itself, and publishes the APK from the GitHub release, signed with Orbit's key, when its own build matches it byte for byte (RELEASE.md, steps 5, 7 and 10). Its recipe lives in [fdroiddata](https://gitlab.com/fdroid/fdroiddata), as `metadata/com.hyuchia.orbit.yml`. These files are what goes there the first time:

- `com.hyuchia.orbit.yml`: the recipe, in F-Droid's own format, so `fdroid rewritemeta` changes nothing in it. Keep the space after `Binaries:`: rewritemeta writes one there before a value it folds onto the next line, and its CI job fails without it (`.editorconfig` keeps editors from trimming it).
- `merge-request.md`: the merge request's description, following fdroiddata's "App inclusion" template word for word.

After F-Droid takes the recipe, it follows the tags by itself (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), and these files are only a record of what was submitted.

## Submitting

Once the release is on GitHub, with its tag and `orbit-x.y.z-fdroid.apk` on the release:

1. **The recipe:** set `commit` to the full hash of the tag's commit (`git rev-parse vx.y.z`), and check `versionName`, `versionCode`, `CurrentVersion` and `CurrentVersionCode` against `app/build.gradle.kts`.
2. **Its checks,** as fdroiddata's CI runs them, in a fork of fdroiddata with the recipe as `metadata/com.hyuchia.orbit.yml`:
   - `fdroid lint com.hyuchia.orbit` is clean, and `fdroid rewritemeta com.hyuchia.orbit` changes nothing;
   - `fdroid checkupdates --allow-dirty com.hyuchia.orbit` finds the tag and changes nothing;
   - `fdroid build --test --on-server com.hyuchia.orbit:<versionCode>`, in `registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie`, ends with "compared built binary to supplied reference binary successfully".

   The fork's own pipeline runs the same jobs when the branch is pushed.
3. **The merge request,** from a public fork of fdroiddata (on a branch that isn't protected) to fdroiddata's `master`, titled "New app: Orbit", with `merge-request.md` as its description. Then answer the reviewers there. The first review can take weeks.

## What was checked before

- **0.2.0, on 2026-10-18:** F-Droid's build of GitHub's 0c296b7 matched `orbit-0.2.0-fdroid.apk`.
- **0.3.0, on 2026-10-20:** fdroiddata's CI jobs, run locally, passed. F-Droid's build of GitHub's aff450a matched `orbit-0.3.0-fdroid.apk`, and the scanner found nothing.

The fork, hyuchia/fdroiddata, has the 0.3.0 recipe on branch `com.hyuchia.orbit`. It was never submitted, so its first merge request is 0.4.0's.
