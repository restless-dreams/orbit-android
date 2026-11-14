# Contributing

Thanks for helping with Orbit for Android. This is how to build it, test it and change its parts. How the code is organized is in [docs/architecture.md](docs/architecture.md); translations are in [TRANSLATING.md](TRANSLATING.md), and releases in [RELEASE.md](RELEASE.md).

## Building

You need:
- JDK 21: `mise install` reads `mise.toml`, which also has the Bun the map's pages are built with;
- the Android SDK with platform 37 and build tools 37: `sdk.dir` in `local.properties`, or `ANDROID_HOME`.

The Gradle wrapper checks the Gradle it downloads against `distributionSha256Sum`. To move to another Gradle, give the wrapper task its checksum from [gradle.org](https://gradle.org/release-checksums/) (`--gradle-distribution-sha256-sum`).

There are two builds of the same app:
- **libre**, the default, has no Google Play services or other closed code, for F-Droid, AppGallery and APKs: presentations reach TVs with Android's own screen casting, or a browser on the TV.
- **play**, for Google Play, casts presentations to TVs with Google Cast where the phone has Google Play services, and screen casts where it doesn't.

About has "Support Orbit", a link to the donation page, in the libre build only: Google Play doesn't allow leading people to pay outside its billing, donations included. For another store that refuses donation links, `-Porbit.supportUrl=` leaves it out, and `-Porbit.supportUrl=https://…` points it somewhere else.

The APK for the website and GitHub looks for new versions too, since no store updates it: once a day, it asks GitHub for the latest release, and Settings can turn it off. Only that build looks, made with `-Porbit.checkForUpdates=true`. Every other build leaves updates to its store, F-Droid's and AppGallery's included, and the play build never looks.

Big videos are made smaller (Media3's Transformer) only with an encoder in the phone's hardware, quick and kind to its battery. An emulator has none, so debug builds take its software one too, and `-Porbit.softwareVideoEncoding=true` makes a release that does, to try a release build there.

```bash
./gradlew assembleLibreDebug     # app/build/outputs/apk/libre/debug/app-libre-debug.apk
./gradlew assemblePlayDebug      # app/build/outputs/apk/play/debug/app-play-debug.apk
./gradlew assembleLibreRelease assemblePlayRelease   # shrunk with R8, about 10 MB, signed as in RELEASE.md
./gradlew bundlePlayRelease      # the play build as an app bundle, for Google Play
./gradlew :app:dependencies --write-locks   # after changing a library: app/gradle.lockfile, what the release builds ship
```

The libraries the release builds ship are locked in `app/gradle.lockfile`, so a pull request that adds or changes one shows it there: try its release build on a phone, since R8 can break a library that works in a debug build.

## Before a pull request

Run the unit tests your change touches (`--tests '<class>'`), the code style check, and the pages' tests, type check and lint if `web/` changed. CI runs every check on every pull request (`.github/workflows/ci.yml`), the whole set below included, so there's no need to run all of it first. It also builds both minified release builds, which catches R8 errors and release-only lint errors, and runs the device checks below on emulators: both release builds through the smoke test and the journeys, the debug build through the device tests, and Android 10 with the oldest WebViews. They catch a library R8 breaks, and the everyday journeys they take, but not everything a change can touch, so try what you changed in the release build too, as below.

```bash
./gradlew testLibreDebugUnitTest verifyRoborazziLibreDebug testPlayDebugUnitTest lintLibreDebug lintPlayDebug ktlintCheck :code-style:test
```

`verifyRoborazziLibreDebug` compares Home and Settings, drawn on a phone and a tablet each way up, in dark, in Spanish and in Brazilian Portuguese (`ScreensTest`), with their pictures in `app/src/test/screenshots/`. When a change means they should look different, `./gradlew recordRoborazziLibreDebug` draws the pictures again: look at them before you commit them. A comparison that fails leaves the screen as it's drawn now beside its picture in `app/build/outputs/roborazzi/`.

`./gradlew ktlintFormat` applies the Kotlin code style: blank lines around blocks (`if`s, loops, composables with content…) and around members with a doc comment. `ktlintCheck` also asks for the app's own components where Material's crash a tablet's panes (AGENTS.md): `ui/ListRow`, `ui/OrbitSlider` and `ui/OrbitSnackbarHost`. The rules are in `code-style/`, and `:code-style:test` checks what they report and what `ktlintFormat` makes of it.

Then try a change people can see on a phone and on a tablet, each way up, and one that touches the map's page, a library, R8's rules, startup or a way into Orbit in the minified release build, once: R8 can break a library that works in a debug build. Changes to the screens' layout need both: the app has its own layouts for tablets and for phones on their side.

Pull requests aren't merged on GitHub directly: an accepted change ships in the next release, with its author credited (`Co-authored-by`), and the pull request is closed with a link to that release.

## Device tests

Unit tests run Android's framework on the JVM (Robolectric) and the pages in a pretend browser (happy-dom). These run Orbit on an emulator or a phone, its maps drawn by the device's own WebView, as people use it:

- **The device tests** (`app/src/androidTest/`), in Orbit's own process, on its debug build: a map loading and drawing on the device's WebView, an idea typed on the map and saved to the map's file, a page whose main frame doesn't load ("The map didn't load"), a page whose process Android stops ("The map stopped"), each loaded again with nothing lost, a job the spare page never finishes, called off on the page, which stops its work and goes on with the next one, and the map under the search's sheet, which keeps its size as the keyboard comes up. They read and touch the map's page through its own document, and fail the WebView's own way: a navigation where nothing answers, `chrome://kill`, a picture whose loading never ends.
- **The journeys** (`journeys/`), an APK of their own, in a process of their own, that drives whichever Orbit is installed through its screen alone (UiAutomator, which reads the map's page as TalkBack does), so R8's renaming doesn't touch them: an idea added on a map, edited, found with its search and there after a restart; a map closed and opened again at once, with its last idea, taking the next; a panel that stays open as the phone turns; the welcome map presented idea by idea; the welcome map and its page in Spanish and Brazilian Portuguese, picked in the phone's App languages; a file opened with Orbit and text, styled text and a file shared to it, each a new map, as another app hands them (the journeys' own app, through its file provider); another version of a map that's here, asked about again after Android stopped Orbit; an idea caught from the Quick Settings tile; the map widget added from the launcher's list, which asks which map it shows and keeps the one picked, changed from its settings, and added from a map's menu, which shows that map; a map's launcher shortcut, among those a long press on Orbit's icon shows, renamed with the map on Home, and opening it; and a map's screen, and a private map's behind the screen lock, as Android brings them back after it stopped Orbit's process, and an idea added right before it stopped. Each starts from a fresh install of the build. They run on phones and tablets, either way up. Most put their words in a field at once, through what it offers accessibility services, since a busy emulator's keyboard app drops keys on its own: on the map's page that's an `input` event alone, without the keys, `beforeinput` or composing a keyboard brings. The map journey in English types its idea on the map key by key through the keyboard app, each key once the keyboard is up and the field reads the one before.
- **The oldest Android and WebView:** Orbit's minimum is Android 10 with Chromium 99. On an Android 10 emulator without Google's apps (`system-images;android-29;default;x86_64`), the smoke test first checks that the WebView it came with, Chromium 74, gets Orbit's notice instead of a map; then `scripts/aosp-webview.sh` makes Chromium 101, the closest above 99 that Android's open-source project shipped for Android 10, which installs as an update of that image's own, and the smoke test and the journeys run on it.

```bash
./gradlew assembleLibreRelease assemblePlayRelease assembleLibreDebug assembleLibreDebugAndroidTest :journeys:assembleDebug
export journeys=journeys/build/outputs/apk/debug/journeys-debug.apk
scripts/smoke-test.sh app/build/outputs/apk/libre/release/*.apk build/smoke/libre $journeys
scripts/smoke-test.sh app/build/outputs/apk/play/release/*.apk build/smoke/play $journeys
scripts/device-tests.sh app/build/outputs/apk/libre/debug/app-libre-debug.apk app/build/outputs/apk/androidTest/libre/debug/app-libre-debug-androidTest.apk $journeys build/device
# Android 10, on an emulator of its own: its own WebView, then Chromium 101.
scripts/aosp-webview.sh build/webview.apk
SMOKE_AVD=orbit-android-10 SMOKE_IMAGE="system-images;android-29;default;x86_64" scripts/with-emulator.sh bash -c '
    scripts/smoke-test.sh app/build/outputs/apk/libre/release/*.apk build/oldest/stock-webview &&
    SMOKE_WEBVIEW=build/webview.apk scripts/smoke-test.sh app/build/outputs/apk/libre/release/*.apk build/oldest/chromium-101 "$journeys"'
```

They take the device adb has (`ANDROID_SERIAL` picks one of several), and replace the Orbit on it, maps included: use an emulator. `scripts/with-emulator.sh` runs a command on an emulator of its own, made from `SMOKE_IMAGE` when it doesn't exist, wiped as it starts and stopped after, on `SMOKE_PORT` (5640), as the maintainers' runner does: give it a virtual device no one else uses. Each script keeps its screenshots and logs in the folder it's given, and the screen and its layout as any check failed.

A check finds things by what Orbit's screens say, in English, as the emulators speak it (the language journeys pick Spanish and Brazilian Portuguese for Orbit). When a change renames what a check looks for, change the check with it; when it adds a way in or out of Orbit, add a journey. Checks wait for what they look for, never for a set time, and never repeat a step until it happens to work: on a busy machine a check only takes longer.

## Changing what the app keeps

The files' layout, `meta.json`, the settings and what crosses to other devices change by the rules in [docs/architecture/files-and-upgrades.md](docs/architecture/files-and-upgrades.md) ("Every version opens what the earlier ones left"), so an upgrade never loses anything. The unit tests include `ReleaseDataTest`, which opens the files every release left on a phone (`app/src/test/resources/releases/`) with your build. When it fails, fix the change, not the files.

## The map's pages

The maps are drawn by pages inside the app's WebViews, written in TypeScript in `web/` and built into `app/src/main/assets/map/` with Orbit inside. `app/src/main/assets/map/orbit.json` records which Orbit they were built with. The built pages are committed, so the app builds without Node or Bun. To rebuild them after changing `web/` or Orbit:

1. Build Orbit first: clone [Orbit](https://github.com/restless-dreams/orbit) beside this repository, then run `bun install` and `bun run build` there, with the Bun its own `package.json` names (`packageManager`), which may not be the one your shell runs: with [mise](https://mise.jdx.dev), `mise exec bun@<that version> -- bun install` and `mise exec bun@<that version> -- bun run build`. Another Bun bundles Orbit differently, and the pages built on it then differ from the committed ones even when nothing changed. To rebuild the same pages, check out the commit in `orbit.json` first. Build it again whenever its checkout changes: the build, the tests and the type check below stop when Orbit's `dist/` is older than its sources, as after a pull or a checkout, rather than run against an earlier Orbit.
2. Then:

   ```bash
   cd web
   ORBIT_PATH=../../orbit bun run build       # default ORBIT_PATH: ../../orbit
   ORBIT_PATH=../../orbit bun run test        # the goals' math, rings by themselves, maps saved and read back, the pages' words, maps they lock, every release's maps, and the presentations' pages: in browsers, on second screens and TVs, and web pages presented and printed
   ORBIT_PATH=../../orbit bun run typecheck   # the pages' types, against Orbit's, with JavaScript's built-ins up to ES2022
   ORBIT_PATH=../../orbit bun run lint        # the library's code style in web/, art/ and scripts/; add -- --fix to apply it
   ```

Build them with the Bun version in `web/package.json` (`packageManager`). About and the licenses screen name the Orbit in `orbit.json`, so a rebuild with a new Orbit is all they need. CI rebuilds the pages from `web/` and the Orbit commit in `orbit.json`, and fails if they differ from the committed ones, so anyone can check that the app's pages come from this source. The build fails when a bundle needs JavaScript newer than ES2022, which WebViews from Chromium 99 on don't all read, and it also writes `app/src/test/resources/page-protocol.json`: one of each message between the app and its pages, from `web/protocol-examples.ts`, which `ProtocolTest` checks the app against. A new kind of command or message needs its example there before the pages type-check.

`scripts/check-pages.sh [commit] [orbit]` makes CI's check on your machine, in a temporary folder that leaves your checkout and your Orbit clone as they are: Orbit at `orbit.json`'s commit, from the clone or address you give it (else `ORBIT_REPOSITORY`, else GitHub), built with its own Bun, then the pages of the commit (HEAD unless you name one) with theirs, compared with the ones it has. It needs git, jq and mise.

## The logo

The logo is drawn by `art/logo.ts`. After changing it, `bun art/logo.ts` writes the launcher icon, the themed icon and the splash screen into `app/src/main/res/drawable/`, and the SVGs in `art/`. The store's icon and feature graphic in `fastlane/metadata/android/en-US/images/` are drawn from `art/logo.svg`.

## The welcome map

A first launch puts in one map, the welcome map: `app/src/main/assets/welcome.md`, with Spanish in `es/welcome.md`, and its presentation path in `welcome.json` beside each (see TRANSLATING.md). Its picture, video and sound are made by `art/welcome.ts`, in the logo's colors: `bun art/welcome.ts` draws them and writes them into `app/src/main/assets/welcome/`. It needs `rsvg-convert` (from librsvg) and `ffmpeg`, with libx264 and libwebp. `web/welcome.test.ts` checks the map as Orbit reads it, in each language.

Debug builds also put in the city library (`app/src/debug/assets/samples/`), a map of 2,400 ideas in the cosmos style, to try big maps on. In a release build, open it as a file: `adb push app/src/debug/assets/samples/city-library.md /sdcard/Download/`, then Open a file in Orbit.

## The emoji names

The icon sheet's search finds emoji by the names and keywords Unicode's CLDR gives them, in English, Spanish and Brazilian Portuguese, with the emoji people use most first, as Unicode ranks them. `bun scripts/emoji-names.ts` writes them into `app/src/main/assets/emoji/` from Unicode's emoji list, its emoji frequency page and CLDR; the versions it reads are at its top. Unicode's data comes under the Unicode License v3, which the licenses screen shows (`app/config/`).

## The baseline profile

Release builds carry a baseline profile, in `app/src/main/generated/baselineProfiles/`: the code that runs as the app starts, scrolls through the maps and opens one, for Android to compile ahead of time instead of while it's used. After big changes to those screens, record it again and commit what it writes. It takes an emulator or a phone on Android 13 or later, and about 20 minutes on an emulator, as it goes through the journeys in `baselineprofile/` with both builds:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile   # ANDROID_SERIAL picks the device when several are connected
```

`StartupBenchmarks`, beside the journeys, times cold starts without the profile and with it, and writes the times to `baselineprofile/build/outputs/connected_android_test_additional_output/`. On an emulator, add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`, and take its numbers as rough:

```bash
./gradlew :baselineprofile:connectedLibreBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

## Casting to TVs

TVs show presentations with the audience's page, as a Google Cast receiver. Orbit's is registered at the [Google Cast SDK Developer Console](https://cast.google.com/publish) as `68BB88D5`, served from https://orbit.hyuchia.com/cast, and `gradle.properties` builds the play app with it (`orbit.castReceiverId`).

- **The receiver:** `bun run build` in `web/` writes it to `web/dist/cast-receiver/`, from the same Orbit as the app's pages: as `index.html` and `receiver.js`, and as one file with its script inside, `orbit-cast-receiver.html`. https://orbit.hyuchia.com/cast serves the two files: the page, a few kilobytes, whose `<script>` names the script's full address, and the script on its own, at an address of its own for each release. The page loads the script as a module from another origin, and the page at /cast runs in a sandbox, so its requests say `Origin: null`: the script's host has to answer with `Access-Control-Allow-Origin: *`, or TVs don't run it. TVs load the page from its address every time, so put a new one there when the pages change, before the play build that needs it ships (RELEASE.md, "The Cast receiver").
- **Testing:** until the receiver is published in the console, it only starts on the Chromecasts and Google TVs added there as test devices.
- **Versions:** the phone says hello with the protocol it speaks, and the receiver answers with its own, the newest map format its Orbit reads, and its Orbit's commit (`web/receiver-protocol.ts`, `present/ScreenVersions.kt`). Every version of the app casts to the same receiver, so the receiver only ever adds to what it understands: it keeps following every older command. A new command raises `AUDIENCE_PROTOCOL` and `RECEIVER_PROTOCOL` together and says its protocol in `AudienceCommand.protocol`, so a TV whose receiver is older doesn't get it. Publish that receiver before the app that sends it ships.
- **A receiver of your own:** put that folder on any HTTPS host, register it as a Custom Receiver (a one-time $5), and build with `-Porbit.castReceiverId=ABCD1234`, or put `orbit.castReceiverId=ABCD1234` in `~/.gradle/gradle.properties`.

Without an ID (`-Porbit.castReceiverId=`), in the libre build, and on phones without Google Play services, the app presents on second screens and in browsers, and casts the phone's screen. A TV can't reach the pictures kept on the phone, so they travel inside the map, shrunk to 1280 pixels, up to 48 MiB of them.
