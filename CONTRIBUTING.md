# Contributing

Thanks for helping with Orbit for Android. This is how to build it, test it and change its parts. How the code is organized is in [docs/architecture.md](docs/architecture.md); translations are in [TRANSLATING.md](TRANSLATING.md), and releases in [RELEASE.md](RELEASE.md).

## Building

You need:
- JDK 21: `mise install` reads `mise.toml`;
- the Android SDK with platform 37 and build tools 37: `sdk.dir` in `local.properties`, or `ANDROID_HOME`.

There are two builds of the same app:
- **libre**, the default, has no Google code at all, for F-Droid, AppGallery and APKs: presentations reach TVs with Android's own screen casting, or a browser on the TV.
- **play**, for Google Play, casts presentations to TVs with Google Cast where the phone has Google Play services, and screen casts where it doesn't.

About has "Support Orbit", a link to the donation page, in the libre build only: Google Play doesn't allow leading people to pay outside its billing, donations included. For another store that refuses donation links, `-Porbit.supportUrl=` leaves it out, and `-Porbit.supportUrl=https://…` points it somewhere else.

The APK for the website and GitHub looks for new versions too, since no store updates it: once a day, it asks GitHub for the latest release, and Settings can turn it off. Only that build looks, made with `-Porbit.checkForUpdates=true`. Every other build leaves updates to its store, F-Droid's and AppGallery's included, and the play build never looks.

```bash
./gradlew assembleLibreDebug     # app/build/outputs/apk/libre/debug/app-libre-debug.apk
./gradlew assemblePlayDebug      # app/build/outputs/apk/play/debug/app-play-debug.apk
./gradlew assembleLibreRelease assemblePlayRelease   # shrunk with R8, about 10 MB, signed as in RELEASE.md
./gradlew bundlePlayRelease      # the play build as an app bundle, for Google Play
```

## Before a pull request

Run the unit tests and lint for both builds, the code style check, and the pages' tests and lint if `web/` changed. CI runs the same checks on every pull request (`.github/workflows/ci.yml`).

```bash
./gradlew testLibreDebugUnitTest testPlayDebugUnitTest lintLibreDebug lintPlayDebug ktlintCheck
```

`./gradlew ktlintFormat` applies the Kotlin code style: blank lines around blocks (`if`s, loops, composables with content…) and around members with a doc comment. The rules are in `code-style/`.

Then try what changed on a phone and on a tablet, each way up, and in the minified release build: R8 can break a library that works in a debug build. Changes to the screens' layout need both: the app has its own layouts for tablets and for phones on their side.

## The map's pages

The maps are drawn by pages inside the app's WebViews, written in TypeScript in `web/` and built into `app/src/main/assets/map/` with Orbit inside. `app/src/main/assets/map/orbit.json` records which Orbit they were built with. The built pages are committed, so the app builds without Node or Bun. To rebuild them after changing `web/` or Orbit:

1. Build Orbit first: clone [Orbit](https://github.com/restless-dreams/orbit) beside this repository, then run `bun install` and `bun run build` there. To rebuild the same pages, check out the commit in `orbit.json` first.
2. Then:

   ```bash
   cd web
   ORBIT_PATH=../../orbit bun run build   # default ORBIT_PATH: ../../orbit
   ORBIT_PATH=../../orbit bun test        # the goals' math, rings by themselves, maps saved and read back, and the pages' words
   ORBIT_PATH=../../orbit bun run lint    # the library's code style in web/ and art/; add -- --fix to apply it
   ```

Build them with the Bun version in `web/package.json` (`packageManager`). CI rebuilds the pages from `web/` and the Orbit commit in `orbit.json`, and fails if they differ from the committed ones, so anyone can check that the app's pages come from this source.

## The logo

The logo is drawn by `art/logo.ts`. After changing it, `bun art/logo.ts` writes the launcher icon, the themed icon and the splash screen into `app/src/main/res/drawable/`, and the SVGs in `art/`. The store's icon and feature graphic in `fastlane/metadata/android/en-US/images/` are drawn from `art/logo.svg`.

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

- **The receiver:** `bun run build` in `web/` writes it to `web/dist/cast-receiver/` (`index.html` and `receiver.js`), from the same Orbit as the app's pages. TVs load it from its address every time, so put a new one there when the pages change.
- **Testing:** until the receiver is published in the console, it only starts on the Chromecasts and Google TVs added there as test devices.
- **A receiver of your own:** put that folder on any HTTPS host, register it as a Custom Receiver (a one-time $5), and build with `-Porbit.castReceiverId=ABCD1234`, or put `orbit.castReceiverId=ABCD1234` in `~/.gradle/gradle.properties`.

Without an ID (`-Porbit.castReceiverId=`), in the libre build, and on phones without Google Play services, the app presents on second screens and in browsers, and casts the phone's screen. A TV can't reach the pictures kept on the phone, so they travel inside the map, shrunk to 1280 pixels.
