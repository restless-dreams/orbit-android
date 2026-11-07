# What each release left on a phone

Each folder holds the files a release of Orbit for Android left in a phone's app storage, made by that release's own code. `ReleaseDataTest` opens every one with the build under test:
- every map, picture, video, version, trashed map and setting has to be there;
- opening them must write nothing;
- they take changes without losing anything;
- with a newer layout than the build knows, they're only read.

`web/releases.test.ts` reads their maps, versions and trashed maps with the Orbit the pages are built with, as the map's page does: none is locked or misread, each is saved as it was written, and their kinds of idea and goals are the app's.

| Folder | Release | Its commit on GitHub |
| --- | --- | --- |
| `before-0.1.0` | The test builds under the app's id before versions started over at 0.1.0 (1.0.0, code 10000) | none: they came before the first |
| `0.1.0` | 0.1.0 (code 1) | none: it came before the first |
| `0.1.1` | 0.1.1 (code 2), the first release on GitHub | dc00360 |
| `0.2.0` | 0.2.0 (code 3) | 0c296b7 |
| `0.3.0` | 0.3.0 (code 4) | aff450a |
| `0.4.0` | 0.4.0 (code 5) | 7be3b34 |

A folder keeps only what's Orbit's in the app's `files/`:
- `maps/`, with `.layout.json`;
- `history/`;
- `trash/`;
- `datastore/settings.preferences_pb`;
- from 0.4.0, this device's id in the folder maps are kept in, `folder-device`, and when the install that made it was installed, `folder-device-installed` (and `folder-deletions.json` and `folder-notes.json`, which last only until the next sync, if they're there).

WebView's and WorkManager's own files are left out.

Layouts before `.layout.json` (each map as a lone file, or history inside each map's folder) only existed under the app's earlier id, which no release used. Their steps are tested from made-up files in `MapRepositoryTest`.

## Adding a release

Make its files with its own code, the same way as the others:

1. **Build the release's debug build** from its tagged commit (`./gradlew assembleLibreDebug`). Debug builds let `run-as` read the app's files.
2. **Zip the maps in `scripts/release-data/`** and copy the zip to an emulator's Downloads. Two of them, Weekend in Lisbon and Why mind maps should move, are maps Orbit started with before 0.4.0, which starts with the welcome map alone: steps 6 and 9 use them. Give the emulator a screen lock too, which private maps need:

   ```bash
   (cd scripts/release-data && zip -r -X ../../orbit-release-data.zip lisbon study old-ideas media-kinds weekend-in-lisbon why-mind-maps-should-move)
   adb push orbit-release-data.zip /sdcard/Download/
   adb shell locksettings set-pin 1234
   ```

3. **Make the data in the app.** On an emulator without Orbit, install the build, open it once in English, and:
   1. Settings, Import maps, `orbit-release-data.zip`: "Added 6 maps."
   2. Still in Settings: Appearance Dark, New maps Paper, Snap to planets off, Move on to the next idea on its own after 8 seconds, and Take maps from nearby devices off.
   3. Open "Lisbon in May", which opens on Food. Tap the + in the orbit, type "Night markets", press Enter, then Escape, then Back until the map's top shows.
   4. Long-press "Tram 28", then Move, Earlier in its orbit: the page writes its `paletteColor`, so it keeps its color. Then Back until the list of maps shows.
   5. Delete "Old ideas" from its card's menu.

   From 0.4.0:

   6. Make "Weekend in Lisbon" private from its card's menu, with the PIN if Orbit asks. Then, from its card's menu again, Cover while locked: ✈️ as its emoji and "Trips" as its name, and Save.
   7. Study "Study notes", a YAML map, from its card's menu (if Home still calls it "text: Photosynthesis", that's the one). Its one card is "Calvin cycle": Show, then Again; when it comes back, Show, then Knew it. Then stop studying.
   8. In Settings: private maps lock again after 5 minutes, and Hide them while locked is on; Studying brings 5 new cards a day. On the list of maps, Got it on the note about where maps are kept.
   9. In Settings, Keep maps in a folder: make a new, empty folder in Documents and use it, with the PIN when Orbit asks to unlock the private map. Wait until Settings says it's in step. Then delete "Why mind maps should move" from its card's menu: the trash then holds a map that was in the folder, which takes its file there with it.

   From the release after 0.4.0:

   10. Open "Lisbon in May", long-press "Night markets", then Add a date: the 20th of next month, Done. Long-press it again, then its date, Add a time: 18:30, Done (Not now if Orbit asks about notifications). Back until the list of maps shows, then in Settings turn Remind me of ideas' dates off.
   11. Push the boarding pass too (`adb push scripts/release-data/boarding-pass.pdf /sdcard/Download/`). Open "Lisbon in May", long-press Food, Add a file, and pick `boarding-pass.pdf` from Downloads: Food holds it, as `media/file-boarding-pass.pdf`, a file of a kind no earlier release carries. Then Back until the list of maps shows.
4. **Copy the files out.** Force-stop the app, then copy its files and keep only Orbit's:

   ```bash
   adb shell am force-stop com.hyuchia.orbit
   adb exec-out run-as com.hyuchia.orbit tar cf - files > release.tar
   mkdir -p <version>/files/datastore && tar xf release.tar
   cp -r files/maps files/history files/trash <version>/files/
   cp files/datastore/settings.preferences_pb <version>/files/datastore/
   # From 0.4.0. folder-deletions.json and folder-notes.json are there only between a deletion or a note and the
   # next sync, so copy them if they are:
   cp files/folder-device files/folder-device-installed <version>/files/
   ```

5. **Add it to `RELEASES`** in `ReleasesTest`, by its versionCode, if it isn't there yet, and **run the tests:** `./gradlew testLibreDebugUnitTest --tests '*ReleaseDataTest*' --tests '*ReleasesTest*'`.

`ReleasesTest` asks for every release's files from the next version on: once the version goes up for the next release, the tests fail until the last release's files are here.

A release that adds something these steps leave, such as a new setting or a new field in `meta.json`, adds a step for it here and a check for it in `ReleaseDataTest`, behind `since("<version>")` so earlier releases' files still pass.
