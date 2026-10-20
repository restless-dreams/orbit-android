# Releasing


The app's id is `com.hyuchia.orbit` (`applicationId` in `app/build.gradle.kts`). Stores tie every update to it, so it can't change once a build is published.

1. **The version:** `versionName` and `versionCode` in `app/build.gradle.kts`. The name follows semantic versioning, from 0.1.0 while the app is still changing a lot. The code goes up by one with each release (0.1.0 is 1): stores refuse an update whose code isn't higher, and the stores' "What's new" files are named after it (`fastlane/metadata/android/<locale>/changelogs/<code>.txt`).
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

   Without a key, release builds are signed with the debug key, which stores refuse. Keep the key and its passwords safe: every update has to be signed with it. With Google Play App Signing, give Play your own app signing key rather than letting it make one, so the AppGallery and F-Droid builds can be signed the same way and install over each other.
3. **The builds,** from a clean clone of the release's tagged commit on GitHub rather than a working copy: each APK records the commit it was built from (`META-INF/version-control-info.textproto`), and F-Droid's build of the tag has to match the F-Droid APK byte for byte. `./gradlew bundlePlayRelease` for Google Play; `./gradlew assembleLibreRelease -Porbit.checkForUpdates=true` for the website and GitHub, which looks for new versions there; `./gradlew assembleLibreRelease` for F-Droid, which updates the app itself; and `./gradlew assembleLibreRelease -Porbit.supportUrl=` for AppGallery, without the donation link. The libre builds are all written to the same file, so copy each one out before the next. Keep each release's `app/build/outputs/mapping/*/mapping.txt`, which turns crash reports back into readable stack traces.
4. **Downloads:** tag the release `v<version>` and publish a GitHub release with the website's APK as `orbit-<version>.apk` and F-Droid's as `orbit-<version>-fdroid.apk`, and the first one's SHA-256 and the signing certificate's in the notes. Copies already installed find it there, as the latest release, within a day. Then put the same file's link, version and SHA-256s on [orbit.hyuchia.com/android](https://orbit.hyuchia.com/android), so people can compare them.
5. **F-Droid** follows the tags by itself (its recipe is `metadata/com.hyuchia.orbit.yml` in [fdroiddata](https://gitlab.com/fdroid/fdroiddata)): it builds the tagged commit, and publishes `orbit-<version>-fdroid.apk` from the GitHub release, signed with your key, when its own build matches it, or skips the version. So that file has to be on the release by then, and the tagged commit needs `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
6. **The store pages:** their texts and pictures are in `fastlane/metadata/android/`, where F-Droid and Fastlane read them.
7. **The Cast receiver:** TVs load the audience's page from https://orbit.hyuchia.com/cast, so put the one built with the release's Orbit there (CONTRIBUTING.md, "Casting to TVs"), and keep it published in the Google Cast SDK Developer Console: an unpublished receiver only starts on test devices. Every version of the app casts to it, so it keeps working with all of them. When the release raises `AUDIENCE_PROTOCOL`, publish the new receiver before the app ships.
8. **The privacy policy** is `app/src/main/assets/privacy.md`, which the app shows under Settings, About. Stores ask for it at a public address too: it's at [orbit.hyuchia.com/privacy](https://orbit.hyuchia.com/privacy), which has to be updated with it.
9. **Before shipping:** run the tests and lint, then install the exact release builds on a phone, a tablet and a phone without Google services, and try what changed.
10. **Its files, for the tests:** make the files this release leaves on a phone, with its own debug build, and add them to `app/src/test/resources/releases/<version>/` as its README says. Every later build then has to open them without losing anything (`ReleaseDataTest`). A release that adds something the steps leave (a setting, a field in `meta.json`) adds a check for it there too.
