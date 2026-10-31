# AGENTS.md

Guidance for coding agents (Codex, Claude Code, Kimi Code and others) working in this repository.

## What this is

Orbit for Android (`com.hyuchia.orbit`): a native app (Kotlin, Jetpack Compose, Material 3 Expressive) whose maps are drawn by Orbit (`@restless-dreams/orbit`, the web library) in WebViews. It's the library's showcase, so it uses Orbit's own features as they're meant to be used rather than working around them. It's GPL 3.0 or later; the `play` build links Google Play services under `LICENSE-EXCEPTION.md`.

- `app/`: the app. `docs/architecture.md` walks through its code (`data/`, `page/`, `map/`, `present/`, `nearby/`, `media/`, `home/`, `settings/`, `widget/`, `capture/`, `voice/`, `study/`, `theme/`, `ui/`) and the decisions behind it.
- Two builds: `libre` (the default: no Google Play services or other closed code, for F-Droid, AppGallery and APKs) and `play` (Google Cast, for Google Play). Code for one build only lives in `app/src/libre/` or `app/src/play/`, and Google Play services only ever come in through `playImplementation`.
- `web/`: the pages inside the WebViews, in TypeScript, built with Orbit into `app/src/main/assets/map/` (committed, so Gradle alone builds the app). `assets/map/orbit.json` records the Orbit commit they were built from. The app and the map's page talk in JSON (`page/Protocol.kt` and `web/bridge.ts`): change both sides together.
- `baselineprofile/`: records the startup profile in `app/src/main/generated/baselineProfiles/`.
- `art/logo.ts`: draws the logo and writes every copy of it. `art/welcome.ts` draws the welcome map's picture and video, and plays its sound.
- `scripts/emoji-names.ts`: writes the icon search's emoji and their names (`app/src/main/assets/emoji/`) from Unicode's data.
- `fastlane/metadata/android/<locale>/`: the store listings, in English and Spanish.
- `docs/features.md` is every feature, area by area: keep it current when what the app does changes.

## Commands

Run from the repository root. JDK 21 comes from `mise install` (`mise.toml`); the Android SDK needs platform and build tools 37 (`local.properties` `sdk.dir`, or `ANDROID_HOME`).

```bash
./gradlew assembleLibreDebug                                               # the default build
./gradlew testLibreDebugUnitTest verifyRoborazziLibreDebug testPlayDebugUnitTest lintLibreDebug lintPlayDebug ktlintCheck :code-style:test   # before every pull request (CI runs them too)
./gradlew ktlintFormat                                                     # apply the Kotlin code style (see Rules)
./gradlew recordRoborazziLibreDebug                                        # after changing how Home or Settings look: their pictures in app/src/test/screenshots/
./gradlew assembleLibreRelease assemblePlayRelease                         # minified with R8
scripts/smoke-test.sh app/build/outputs/apk/libre/release/*.apk build/smoke   # the release build on the device adb has: started cold, a map opened, the crash log
./gradlew :app:dependencies --write-locks                                  # after changing a library: app/gradle.lockfile, what the release builds ship
(cd web && ORBIT_PATH=../../orbit bun run build)                           # rebuild the pages after changing web/ or Orbit (build Orbit first, with its own Bun)
(cd web && ORBIT_PATH=../../orbit bun run test)                            # the pages' tests
(cd web && ORBIT_PATH=../../orbit bun run typecheck)                       # the pages' types, against Orbit's
(cd web && ORBIT_PATH=../../orbit bun run lint)                            # the code style of web/, art/ and scripts/ (bun run lint -- --fix applies it)
scripts/check-pages.sh HEAD ../orbit                                       # the committed pages are exactly what the source builds, as CI checks them, in a temporary folder
bun art/logo.ts                                                            # after changing the logo
bun art/welcome.ts                                                         # after changing the welcome map's picture, video or sound (needs rsvg-convert and ffmpeg)
bun scripts/emoji-names.ts                                                 # after changing the Unicode versions the emoji names come from
bun scripts/store-listing.ts                                               # the store listings fit the stores, with What's new for this versionCode (CI runs it)
```

CI runs these checks on every pull request (`.github/workflows/ci.yml`): the unit tests, lint and code style of both builds, the screens against their pictures (`ScreensTest`), both release builds, the libre one started on an emulator with a map opened (`scripts/smoke-test.sh`), and the pages against `orbit.json`'s Orbit. A change is ready when CI has passed and its release build has been tried on a device (see Rules).

`ORBIT_PATH` points at a checkout of the Orbit library (github.com/restless-dreams/orbit; `assets/map/orbit.json` names the commit the pages were built from), built there with `bun run build` and the Bun its own `package.json` names (`packageManager`), which may not be the one the shell runs: `mise exec bun@<that version> -- bun install`, then `mise exec bun@<that version> -- bun run build`. In a worktree, give its full path. Build the pages with the Bun in `web/package.json`: CI checks that the committed pages are exactly what the source builds, along with `app/src/test/resources/page-protocol.json`, the protocol's examples (`web/protocol-examples.ts`) that `ProtocolTest` reads. Another Bun bundles the same source differently, Orbit's as well as the pages': pages built on an Orbit built with another Bun differ from the committed ones even when nothing changed. `scripts/check-pages.sh [commit] [orbit]` checks a commit as CI does, in a temporary folder: Orbit at `orbit.json`'s commit, cloned from the clone or address it's given (else `ORBIT_REPOSITORY`, else GitHub) and built with its own Bun, then the pages with theirs, compared with the commit's. It leaves the checkout and the Orbit clone as they are.

## Rules

- **Phones and tablets, both ways up.** Every screen has to work and look right on a phone and a tablet, each in portrait and landscape. Short screens (under 480 dp tall, like a phone on its side) get a side rail, the breadcrumbs in the top bar and a slim home header; landscape screens wide enough open panels beside the map; tablets keep the list of maps beside the open one. Check a change on both, each way up, before calling it done.
- **On the map, not in bars.** Ideas are added with the + in the orbit and typed inside their planet; native menus, sheets and the keyboard go around the map. Don't add input bars or dialogs for what can happen on the map.
- **Test the release build.** R8 (full mode, AGP 9's default) can break a library that works in debug builds: it once broke Glance's WorkManager. Before calling a change done, install the minified release build, cold-start it, open a map and try what changed: `scripts/smoke-test.sh` does the first three, as CI does. When a library arrives or changes, update the lock with the `dependencies` task above: `app/gradle.lockfile`'s diff is what the release builds ship, and `LibreBuildTest` fails if the libre build would ship Google Play services.
- **`ui/ListRow`, not Material's `ListItem`,** for rows in the map's panels: on tablets the map's pane animates in a lookahead pass, and `ListItem`'s supporting text crashes it when a side panel closes. After changing panels, open and close each one on a tablet and check the crash log (`adb logcat -b crash`).
- **`ui/OrbitSlider`, not Material's `Slider`,** for the same reason: `Slider` reads its track's alignment line as it measures, and that throws in the lookahead pass when a side panel opens beside the map on a tablet (0.3.0's Style panel closed the app that way). `OrbitSlider` looks and works like Material 3 Expressive's slider, keys, TalkBack and haptics included.
- **`ui/OrbitSnackbarHost`, not Material's `SnackbarHost` and `Snackbar`,** for the same reason: Material's snackbar lines its action up with its text's baselines as it measures, and that throws in the lookahead pass when a panel opens beside the map on a tablet while a snackbar is up (removing an idea with ideas under it, then opening the outline, closed the app). `OrbitSnackbar` is laid out as Material's is, with its colors, type, Undo and other actions, and Material's host's timing and TalkBack; it can also be swiped away.
  `./gradlew ktlintCheck` checks these three (`AppComponentsRule` in `code-style/`): `ListItem` in `map/` and `home/` outside a dialog or sheet, and Material's slider or snackbar outside `ui/`.
- **Every string in English and Spanish.** Strings go in `app/src/main/res/values/strings_<area>.xml` and the same file in `values-es/`; the welcome map, templates and privacy policy have Spanish copies in `app/src/main/assets/es/`, and the pages get their words from `strings_pages.xml`. Lint lists missing translations (`MissingTranslation`). `TRANSLATING.md` has the glossary and style: neutral Latin American Spanish, with tú.
- **Maps are people's files.** Never write half a file (`writeAtomically` in `data/Files.kt`), never write a map over text that changed since it was read (`MapRepository.write`), and keep what a newer version of the app wrote. A map Orbit can't fully read is only shown, never rewritten.
- **Upgrades lose nothing.** A change to the files' layout, `meta.json`, the settings or what crosses to other devices follows the rules in `docs/architecture.md` ("Every version opens what the earlier ones left"), and `ReleaseDataTest` opens every release's files with each build. Never edit a release's files in `app/src/test/resources/releases/` to make a test pass: they're what people's phones hold.
- **No Google Play services in `libre`,** nor any other closed code. Anything that needs Google Play services has a fallback that works without them: Android's own network service discovery, folder picker and screen casting. Open-source libraries under Google's name are fine there, such as AndroidX, or ZXing for the QR codes that let browsers join.
- **AGP 9 has Kotlin built in:** never apply `org.jetbrains.kotlin.android`.
- **The pages run on WebViews from Chromium 99** (`SystemWebView.MINIMUM_CHROMIUM`): newer CSS needs a plainer fallback there, and older WebViews get the app's notice instead of a map.
- **The store's pitch** leads with "Your universe of ideas, in orbit.", not with markdown: people build maps on the map and rarely see the markdown, which is the library's story.
- **Code style.** In Kotlin, a block has a blank line before and after it, unless it starts or ends the block it's in. Blocks are `if`s, `when`s, loops and `try`s, and statements on several lines whose last line starts with a closing brace: a function, a lambda (so a composable with content), a class or an object. A member with a doc comment (in a class body, or a constructor's properties) has a blank line before its comment and after it, unless it's the first or the last. `./gradlew ktlintCheck` checks this and `./gradlew ktlintFormat` applies it. The rules are in `code-style/`, and ktlint runs those and three of its standard ones, set in `.editorconfig`: no unused imports, imports in order, and no double blank lines. The TypeScript in `web/` and `art/` follows the library's style, which also makes functions arrow functions. `bun run lint` in `web/` checks both, with the rules from the Orbit checkout at `ORBIT_PATH`.
- Releases are in `RELEASE.md`. Signing keys and their passwords stay outside the repository.
