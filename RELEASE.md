# Releasing

The app's id is `com.hyuchia.orbit` (`applicationId` in `app/build.gradle.kts`). Stores tie every update to it, so it can't change once a build is published.

Each release is one commit on GitHub's main, tagged `vx.y.z`. A release goes in this order:

1. Orbit first, if the pages need one that isn't public yet.
2. The release's commit on GitHub, with its CI green.
3. The builds from that commit, tried on devices.
4. The tag and the downloads.
5. The receiver, then each store.

Releasing for the first time on a store takes longer than the release itself: F-Droid's first review can take weeks, and Google Play may ask a new developer account for a 14-day closed test first. So start those early (steps 10 and 11).

## Before the release

0. **Orbit, first.** The app's pages are built with the Orbit commit in `app/src/main/assets/map/orbit.json`, and anyone rebuilding them clones it from github.com/restless-dreams/orbit: GitHub's CI, F-Droid's reviewers and CONTRIBUTING.md's readers. So when the pages are built with an Orbit that GitHub doesn't have yet, release Orbit first:

   1. **Its release,** as Orbit's RELEASE.md says: the version, the maps it writes frozen as fixtures, and its commit on GitHub, tagged `vx.y.z`.
   2. **npm,** from the tag (`npm login` once; `prepack` builds the package):

      ```bash
      git switch --detach vx.y.z && bun install --frozen-lockfile && npm publish && git switch -
      ```

   3. **The pages,** if `orbit.json` doesn't name that commit yet: rebuild them from a clone of Orbit's tag (CONTRIBUTING.md, "The map's pages"), and commit them. Only `orbit.json` changes when the files are the same. `scripts/check-pages.sh` then builds them as GitHub's CI will, Orbit from GitHub, and says whether they're the same.

   Orbit went from 0.2.0 to 0.4.0 on GitHub and npm: 0.3.0 was never published.
1. **The version:** `versionName` and `versionCode` in `app/build.gradle.kts`. The name follows semantic versioning, from 0.1.0 while the app is still changing a lot. The code goes up by one with each release (0.1.0 is 1): stores refuse an update whose code isn't higher, and the stores' "What's new" files are named after it (`fastlane/metadata/android/<locale>/changelogs/<code>.txt`, at most 500 characters, in each language). CI checks they're there and every listing fits the stores (`bun scripts/store-listing.ts`).
2. **The signing key:** stores take builds signed with your upload key, kept outside this repository. Make one once:

   ```bash
   keytool -genkeypair -v -keystore ~/keys/orbit-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias orbit
   ```

   Then add it to `~/.gradle/gradle.properties`, or to the environment as `ORBIT_SIGNING_STORE_FILE`, `ORBIT_SIGNING_STORE_PASSWORD`, `ORBIT_SIGNING_KEY_ALIAS` and `ORBIT_SIGNING_KEY_PASSWORD`:

   ```properties
   orbit.signing.storeFile=/home/you/keys/orbit-upload.jks
   orbit.signing.storePassword=…
   orbit.signing.keyAlias=orbit
   orbit.signing.keyPassword=…
   ```

   Without a key, release builds are left unsigned, never signed with the debug key, which anyone could sign with: they install nowhere until they're signed, as F-Droid builds them. Made with `-Porbit.requireUploadKey=true`, they stop instead. Keep the key and its passwords safe: every update has to be signed with it. Its certificate's SHA-256 is `b4fd505f255c406cd205a6c403898e2069968339ad40269d5c22be79d561a107`, which F-Droid's recipe allows (`fdroid/com.hyuchia.orbit.yml`).
3. **The privacy policy** is `app/src/main/assets/privacy.md`, which the app shows under Settings, About. Stores ask for it at a public address too: it's at [orbit.hyuchia.com/privacy](https://orbit.hyuchia.com/privacy), which has to say the same by the time the release is out. The stores' own forms (Google Play's Data safety, for one) have to match it.

## The release

4. **The commit on GitHub, and its CI.** The release's code goes to GitHub's main as one commit. GitHub's CI (`.github/workflows/ci.yml`) runs on it. **Wait until its jobs, `app`, `release`, `smoke` and `pages`, have passed on that exact commit** (the Actions tab, or `gh run watch`): this is the run on the release's own commit, with Orbit from GitHub. Never tag or publish on red: fix it, and make the release's commit again.
5. **The builds,** from a fresh `git clone` of GitHub at that commit, never a working copy or a worktree. Each APK records the commit it was built from (`META-INF/version-control-info.textproto`), and a worktree's build records none, so it can't match F-Droid's build of the tag, which has to match the F-Droid APK byte for byte. `-Porbit.requireUploadKey=true` stops a build that would be signed with the debug key:

   ```bash
   git clone https://github.com/restless-dreams/orbit-android.git orbit-x.y.z && cd orbit-x.y.z && git checkout <the commit>
   ./gradlew bundlePlayRelease assemblePlayRelease -Porbit.requireUploadKey=true             # Google Play, and its APK for trying it
   ./gradlew assembleLibreRelease -Porbit.requireUploadKey=true -Porbit.checkForUpdates=true  # the website and GitHub
   ./gradlew assembleLibreRelease -Porbit.requireUploadKey=true                               # F-Droid
   ./gradlew assembleLibreRelease -Porbit.requireUploadKey=true -Porbit.supportUrl=           # AppGallery
   ```

   - The website's APK looks for new versions on GitHub. F-Droid's updates the app itself. AppGallery's leaves out the donation link.
   - The libre builds are all written to the same file (`app/build/outputs/apk/libre/release/app-libre-release.apk`), so copy each one out before the next, as `orbit-x.y.z.apk`, `orbit-x.y.z-fdroid.apk` and `orbit-x.y.z-appgallery.apk`. Copy out the play build's too: `app/build/outputs/bundle/playRelease/app-play-release.aab` as `orbit-x.y.z-play.aab`, and `app/build/outputs/apk/play/release/app-play-release.apk` as `orbit-x.y.z-play.apk`.
   - Keep each build's `app/build/outputs/mapping/*/mapping.txt`, which turns crash reports back into readable stack traces.

   Then **check that each file is signed with Orbit's key**: each has to print the certificate in step 2 (the bundle's with colons, in capitals).

   ```bash
   for apk in orbit-x.y.z*.apk; do "$ANDROID_HOME"/build-tools/37.0.0/apksigner verify --print-certs "$apk" | grep 'certificate SHA-256'; done
   keytool -printcert -jarfile orbit-x.y.z-play.aab | grep 'SHA256:'
   ```
6. **Before shipping:** install those exact files on a phone, a tablet and a phone without Google services, over the last release with maps in it, and try what changed: `orbit-x.y.z-play.apk` stands for the bundle, which is built from the same commit.
7. **The tag and the downloads:**
   - Tag the commit `vx.y.z` and push the tag: `git tag vx.y.z <the commit> && git push github vx.y.z`.
   - The release's commit credits the author of each outside pull request it carries (`Co-authored-by`). Close those pull requests with a link to the release (CONTRIBUTING.md, "Before a pull request").
   - Publish a GitHub release for the tag. It has the website's APK as `orbit-x.y.z.apk` and F-Droid's as `orbit-x.y.z-fdroid.apk`. Its notes carry the first one's SHA-256 and the signing certificate's.
   - Copies already installed find it there, as the latest release, within a day.
   - Then put the same file's link, version and SHA-256s on [orbit.hyuchia.com/android](https://orbit.hyuchia.com/android), so people can compare them.

## After the release

8. **The Cast receiver:** TVs load the audience's page from https://orbit.hyuchia.com/cast. Put the one built with the release's pages there before the play build reaches anyone (step 11):
   - Building the pages from the release's commit writes it to `web/dist/cast-receiver/` (CONTRIBUTING.md, "Casting to TVs"). It goes up as two files: `receiver.js`, its script, and `index.html`, the page, a few kilobytes, which loads the script.
   - The script first, unchanged, at an address of its own that no other release's uses, on an HTTPS host that answers with `Access-Control-Allow-Origin: *`. The page loads it as a module from another origin, and the page at /cast runs in a sandbox, so its requests say `Origin: null`: without that header, TVs don't run the script. Check the address answers with the same bytes, as `text/javascript`, and with the header when asked with `Origin: null`.
   - Then the page: `index.html` with the script's full address in place of `receiver.js` in its last `<script>`, since a relative address can't be resolved from /cast. With the script put back inside it, it's `orbit-cast-receiver.html`, the receiver as one file, byte for byte.
   - Keep the earlier release's page and script where they are: its page back at /cast is the way back.
   - Keep the receiver published in the Google Cast SDK Developer Console: an unpublished receiver only starts on test devices.
   - Every version of the app casts to it, so it keeps working with all of them. When the release raises `AUDIENCE_PROTOCOL`, the new receiver has to be up before the app ships.
9. **The store pages:** their texts and pictures are in `fastlane/metadata/android/`: `en-US`, `es-419`, `es-ES` and `pt-BR`. Brazilian Portuguese has its own feature graphic but no screenshots of its own yet, so the stores show the English ones for it.
   - F-Droid reads them from the tagged commit.
   - Google Play and AppGallery take them by hand, from the same files. For Google Play, `fastlane supply` can upload them instead, with a service account of the Play Console.
10. **F-Droid:**
    - **The first time:** submit the recipe in `fdroid/`, as its README says. F-Droid's review is a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata), and can take weeks.
    - **After that:** F-Droid follows the tags by itself (`UpdateCheckMode: Tags`). It builds the tagged commit, and publishes `orbit-x.y.z-fdroid.apk` from the GitHub release, signed with your key, when its own build matches it, or else it skips the version. So that file has to be on the release by then, and the tagged commit needs `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`. Publishing the GitHub release runs `.github/workflows/release.yml`, which builds the tag as F-Droid does and compares it with that file the same way: when it fails, F-Droid's build won't match either, so build the release's files again from a fresh clone (step 5).
11. **Google Play:**
    - **The first time,** in the Play Console:
      1. Create the app: Orbit, English (United States), an app, free.
      2. App integrity: use your own app signing key, uploaded with Play's encryption tool, rather than letting Play make one. That way Play's copies are signed as the other stores' are, and each installs over the others.
      3. Fill in what Play asks about the app:
         - Privacy policy: https://orbit.hyuchia.com/privacy.
         - Ads: none.
         - Data safety: Orbit itself collects and shares nothing. The microphone turns speech into ideas through the phone's speech service and records voice memos, which stay on the phone. The fingerprint, face or PIN unlock private maps through Android's own prompt. The local network server sends maps to the person's own devices and shows presentations in browsers in the room. Declare what Google's Cast library sends, as the privacy policy describes it: anonymous information about casting, with the phone's and Orbit's versions.
         - Content rating: the questionnaire.
         - Target audience: choosing ages under 13 brings Play's Families rules.
      4. The store listing, in English, both Spanishes and Brazilian Portuguese (step 9).
      5. Release on the internal testing track first, then closed testing, then production. A personal developer account created after November 2023 has to run a closed test with at least 12 testers for 14 days before it can apply for production.
    - **Each release:** upload `orbit-x.y.z-play.aab` with its `mapping.txt`, and its What's new from `changelogs/<versionCode>.txt` in each language. Roll it out only after the receiver (step 8).
12. **AppGallery:**
    - **The first time,** in AppGallery Connect: create the app for `com.hyuchia.orbit`, and fill in its listing, privacy policy and content rating. If it offers App Signing, give it Orbit's own key rather than letting it make one.
    - **Each release:** upload `orbit-x.y.z-appgallery.apk`, which has no donation link, with its What's new.
13. **Its files, for the tests:** make the files this release leaves on a phone, with its own debug build, and add them to `app/src/test/resources/releases/<version>/` as its README says. Every later build then has to open them without losing anything (`ReleaseDataTest`), and `ReleasesTest` fails once the version goes up for the next release until they're there. A release that adds something the steps leave (a setting, a field in `meta.json`) adds a check for it there too.
14. **The libraries, for the next version:** bring them up to date now, right after a release, and never in the days before one: each changes the APK, and needs the release builds tried again (AGENTS.md, "Test the release build").
    - `./gradlew lintLibreDebug` lists them (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`). Take stable versions. A pre-release only where the app needs what it brings, as Material 3 Expressive's `material3` pin in `gradle/libs.versions.toml`: drop that pin once the Compose BOM brings 1.5.0.
    - A new Google Cast library (`play-services-cast-framework`): list the play build's Google Play services libraries again (`./gradlew :app:dependencies --configuration playReleaseRuntimeClasspath`) and check `LICENSE-EXCEPTION.md` names each closed one.
    - A new Gradle: F-Droid's build server runs Gradle through its own `gradlew-fdroid`, which knows each version's checksum, so check that it knows the new one before the wrapper moves to it.
    - The target SDK: Google Play asks updates to target a recent Android, a year behind the newest at most. Move `targetSdk` with the libraries, and try what the new Android changes for apps.
    - Then see what resolves (`./gradlew :app:dependencies --configuration libreReleaseRuntimeClasspath`): a library the libre build didn't have before needs a look (AGENTS.md, "No Google Play services in libre"), and the licenses screen lists it by itself.