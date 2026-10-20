# What each release left on a phone

Each folder holds the files a release of Orbit for Android left in a phone's app storage, made by that release's own code. `ReleaseDataTest` opens every one with the build under test:
- every map, picture, video, version, trashed map and setting has to be there;
- opening them must write nothing;
- they take changes without losing anything;
- with a newer layout than the build knows, they're only read.

| Folder | Release | Built from |
| --- | --- | --- |
| `before-0.1.0` | The test builds under the app's id before versions started over at 0.1.0 (1.0.0, code 10000) | 01edf60 |
| `0.1.0` | 0.1.0 (code 1) | 495833b |
| `0.1.1` | 0.1.1 (code 2), the first release on GitHub | 16e6bb0 (GitHub dc00360) |
| `0.2.0` | 0.2.0 (code 3) | 4b9d0cf (GitHub 0c296b7) |

The commits are the merge requests' on GitLab, whose trees are the GitHub releases'.

A folder keeps only what's Orbit's in the app's `files/`:
- `maps/`, with `.layout.json`;
- `history/`;
- `trash/`;
- `datastore/settings.preferences_pb`.

WebView's and WorkManager's own files are left out.

Layouts before `.layout.json` (each map as a lone file, or history inside each map's folder) only existed under the app's earlier id, which no release used. Their steps are tested from made-up files in `MapRepositoryTest`.

## Adding a release

Make its files with its own code, the same way as the others:

1. **Build the release's debug build** from its tagged commit (`./gradlew assembleLibreDebug`). Debug builds let `run-as` read the app's files.
2. **Zip the maps in `scripts/release-data/`** and copy the zip to an emulator's Downloads:

   ```bash
   (cd scripts/release-data && zip -r -X ../../orbit-release-data.zip lisbon study old-ideas)
   adb push orbit-release-data.zip /sdcard/Download/
   ```

3. **Make the data in the app.** On an emulator without Orbit, install the build, open it once in English, and:
   1. Settings, Import maps, `orbit-release-data.zip`: "Added 3 maps."
   2. Still in Settings: Appearance Dark, New maps Paper, Snap to planets off, Move on to the next idea on its own after 8 seconds, and Take maps from nearby devices off.
   3. Open "Lisbon in May", which opens on Food. Tap the + in the orbit, type "Night markets", press Enter, then Escape, then Back until the list of maps shows.
   4. Delete "Old ideas" from its card's menu.
4. **Copy the files out.** Force-stop the app, then copy its files and keep only Orbit's:

   ```bash
   adb shell am force-stop com.hyuchia.orbit
   adb exec-out run-as com.hyuchia.orbit tar cf - files > release.tar
   mkdir -p <version>/files/datastore && tar xf release.tar
   cp -r files/maps files/history files/trash <version>/files/
   cp files/datastore/settings.preferences_pb <version>/files/datastore/
   ```

5. **Run the tests:** `./gradlew testLibreDebugUnitTest --tests '*ReleaseDataTest*'`.

A release that adds something these steps leave, such as a new setting or a new field in `meta.json`, adds a step for it here and a check for it in `ReleaseDataTest`.
