# AGENTS.md

Guidance for coding agents (Codex, Claude Code, Kimi Code and others) working in this repository.

## What this is

Orbit for Android (`com.hyuchia.orbit`): a native app (Kotlin, Jetpack Compose, Material 3 Expressive) whose maps are drawn by Orbit (`@restless-dreams/orbit`, the web library) in WebViews. It's the library's showcase, so it uses Orbit's own features as they're meant to be used rather than working around them. It's GPL 3.0 or later; the `play` build links Google Play services under `LICENSE-EXCEPTION.md`.

- `app/`: the app. `docs/architecture.md` walks through its code (`data/`, `page/`, `map/`, `present/`, `nearby/`, `media/`, `home/`, `settings/`, `widget/`, `capture/`, `voice/`, `theme/`, `ui/`) and the decisions behind it.
- Two builds: `libre` (the default: no Google code at all, for F-Droid, AppGallery and APKs) and `play` (Google Cast, for Google Play). Code for one build only lives in `app/src/libre/` or `app/src/play/`, and Google Play services only ever come in through `playImplementation`.
- `web/`: the pages inside the WebViews, in TypeScript, built with Orbit into `app/src/main/assets/map/` (committed, so Gradle alone builds the app). `assets/map/orbit.json` records the Orbit commit they were built from. The app and the map's page talk in JSON (`page/Protocol.kt` and `web/bridge.ts`): change both sides together.
- `baselineprofile/`: records the startup profile in `app/src/main/generated/baselineProfiles/`.
- `art/logo.ts`: draws the logo and writes every copy of it.
- `fastlane/metadata/android/<locale>/`: the store listings, in English and Spanish.
- `docs/features.md` is every feature, area by area: keep it current when what the app does changes.

## Commands

Run from the repository root. JDK 21 comes from `mise install` (`mise.toml`); the Android SDK needs platform and build tools 37 (`local.properties` `sdk.dir`, or `ANDROID_HOME`).

```bash
./gradlew assembleLibreDebug                                               # the default build
./gradlew testLibreDebugUnitTest testPlayDebugUnitTest lintLibreDebug lintPlayDebug ktlintCheck   # before every pull request (CI runs them too)
./gradlew ktlintFormat                                                     # apply the Kotlin code style (see Rules)
./gradlew assembleLibreRelease assemblePlayRelease                         # minified with R8
./gradlew :app:dependencies --configuration libreReleaseRuntimeClasspath   # which library versions resolve
(cd web && ORBIT_PATH=../../orbit bun run build)                           # rebuild the pages after changing web/ or Orbit (build Orbit first)
(cd web && ORBIT_PATH=../../orbit bun test)                                # the pages' tests
(cd web && ORBIT_PATH=../../orbit bun run lint)                            # the code style of web/ and art/ (bun run lint -- --fix applies it)
bun art/logo.ts                                                            # after changing the logo
```

`ORBIT_PATH` points at a checkout of the Orbit library (github.com/restless-dreams/orbit; `assets/map/orbit.json` names the commit the pages were built from), built there with `bun run build`; in a worktree, give its full path. Build the pages with the Bun in `web/package.json`: CI checks that the committed pages are exactly what the source builds.

## Rules

- **Phones and tablets, both ways up.** Every screen has to work and look right on a phone and a tablet, each in portrait and landscape. Short screens (under 480 dp tall, like a phone on its side) get a side rail, the breadcrumbs in the top bar and a slim home header; landscape screens wide enough open panels beside the map; tablets keep the list of maps beside the open one. Check a change on both, each way up, before calling it done.
- **On the map, not in bars.** Ideas are added with the + in the orbit and typed inside their planet; native menus, sheets and the keyboard go around the map. Don't add input bars or dialogs for what can happen on the map.
- **Test the release build.** R8 (full mode, AGP 9's default) can break a library that works in debug builds: it once broke Glance's WorkManager. Before calling a change done, install the minified release build, cold-start it, open a map and try what changed. When a new library arrives, check what resolves with the `dependencies` task above.
- **`ui/ListRow`, not Material's `ListItem`,** for rows in the map's panels: on tablets the map's pane animates in a lookahead pass, and `ListItem`'s supporting text crashes it when a side panel closes. After changing panels, open and close each one on a tablet and check the crash log (`adb logcat -b crash`).
- **Every string in English and Spanish.** Strings go in `app/src/main/res/values/strings_<area>.xml` and the same file in `values-es/`; the starter maps, templates and privacy policy have Spanish copies in `app/src/main/assets/es/`, and the pages get their words from `strings_pages.xml`. Lint lists missing translations (`MissingTranslation`). `TRANSLATING.md` has the glossary and style: neutral Latin American Spanish, with tú.
- **Maps are people's files.** Never write half a file (`writeAtomically` in `data/Files.kt`), never write a map over text that changed since it was read (`MapRepository.write`), and keep what a newer version of the app wrote. A map Orbit can't fully read is only shown, never rewritten.
- **No Google code in `libre`.** Anything that needs Google Play services has a fallback that works without them: Android's own network service discovery, folder picker and screen casting.
- **AGP 9 has Kotlin built in:** never apply `org.jetbrains.kotlin.android`.
- **The pages run on WebViews from Chromium 99** (`SystemWebView.MINIMUM_CHROMIUM`): newer CSS needs a plainer fallback there, and older WebViews get the app's notice instead of a map.
- **The store's pitch** leads with "Your universe of ideas, in orbit.", not with markdown: people build maps on the map and rarely see the markdown, which is the library's story.
- **Code style.** In Kotlin, a block has a blank line before and after it, unless it starts or ends the block it's in. Blocks are `if`s, `when`s, loops and `try`s, and statements on several lines whose last line starts with a closing brace: a function, a lambda (so a composable with content), a class or an object. A member with a doc comment (in a class body, or a constructor's properties) has a blank line before its comment and after it, unless it's the first or the last. `./gradlew ktlintCheck` checks this and `./gradlew ktlintFormat` applies it. The rules are in `code-style/`, and ktlint runs only those: its standard rules are off in `.editorconfig`. The TypeScript in `web/` and `art/` follows the library's style, which also makes functions arrow functions. `bun run lint` in `web/` checks both, with the rules from the Orbit checkout at `ORBIT_PATH`.
- Releases are in `RELEASE.md`. Signing keys and their passwords stay outside the repository.
