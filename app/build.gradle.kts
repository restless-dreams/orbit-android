import com.android.build.api.variant.ResValue

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * The key store builds are signed with: the owner's upload key, from
 * ~/.gradle/gradle.properties or the environment, never from this
 * repository (see RELEASE.md):
 *
 *   orbit.signing.storeFile=/path/to/orbit-upload.jks   (ORBIT_SIGNING_STORE_FILE)
 *   orbit.signing.storePassword=…                       (ORBIT_SIGNING_STORE_PASSWORD)
 *   orbit.signing.keyAlias=orbit                        (ORBIT_SIGNING_KEY_ALIAS)
 *   orbit.signing.keyPassword=…                         (ORBIT_SIGNING_KEY_PASSWORD)
 *
 * Without one, release builds are signed with the debug key, which is
 * fine for trying a build and refused by every store.
 */
fun signing(name: String): String? =
    providers.gradleProperty("orbit.signing.$name").orNull
        ?: providers.environmentVariable("ORBIT_SIGNING_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()).orNull
val uploadKey = signing("storeFile")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.hyuchia.orbit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hyuchia.orbit"
        minSdk = 29
        targetSdk = 36
        // One more with each release, whatever its name: stores and Android tie every update to it.
        versionCode = 1
        versionName = "0.1.0"
    }

    // The languages the app speaks (see TRANSLATING.md). Android lists them in the app's language
    // settings from the locale config made here, and libraries' other languages are left out, so a
    // phone in another language shows the whole app in English rather than a mix.
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "es")
    }

    // Two builds of the same app. libre has no Google code at all, for F-Droid and anywhere else:
    // presentations reach TVs with Android's own screen casting. play, for Google Play, casts to
    // TVs with Google Cast where the phone has Google Play services.
    flavorDimensions += "distribution"

    productFlavors {
        create("libre") {
            dimension = "distribution"
            isDefault = true
            // Where About's "Support Orbit" goes: the donation page. -Porbit.supportUrl= leaves the
            // item out, for stores that may refuse apps with donation links, such as AppGallery.
            buildConfigField("String", "SUPPORT_URL", "\"${providers.gradleProperty("orbit.supportUrl").getOrElse("https://orbit.hyuchia.com/support")}\"")
        }

        create("play") {
            dimension = "distribution"
            // Google Play doesn't allow leading people to pay outside its billing, donations included,
            // so the play build never has "Support Orbit", whatever orbit.supportUrl says.
            buildConfigField("String", "SUPPORT_URL", "\"\"")
            // The Cast receiver TVs present maps with: the site web/build.ts makes, registered with Google
            // Cast (see CONTRIBUTING.md, "Casting to TVs"). Pass it as -Porbit.castReceiverId=… or in ~/.gradle/gradle.properties;
            // without one, the app presents on second screens, and casts the phone's screen.
            buildConfigField("String", "CAST_RECEIVER_ID", "\"${providers.gradleProperty("orbit.castReceiverId").getOrElse("")}\"")
            proguardFile("proguard-play.pro")
        }
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = uploadKey
                storePassword = signing("storePassword")
                keyAlias = signing("keyAlias")
                keyPassword = signing("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = if (uploadKey != null) {
                signingConfigs.getByName("upload")
            } else {
                logger.warn("orbit: no upload key (orbit.signing.storeFile); release builds are signed with the debug key, which stores refuse.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // For the app's id as a string, below.
        resValues = true
    }
}

// The app's id as a string resource, for what can't take the manifest's placeholder: the shortcuts' intents.
androidComponents {
    onVariants { variant ->
        variant.resValues.put(variant.makeResValueKey("string", "application_id"), variant.applicationId.map { ResValue(it) })
    }
}

// The open-source notices: every library the app ships, and Orbit and the font, which aren't libraries the plugin finds.
aboutLibraries {
    collect {
        configPath = file("config")
    }
}

// Both builds run the same code, so the profiles :baselineprofile records for each go into one, in src/main.
baselineProfile {
    mergeIntoMain = true
}

kotlin {
    compilerOptions {
        // Material 3 Expressive and the adaptive layouts are still marked experimental.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.emoji.picker)
    implementation(libs.androidx.glance.appwidget)
    // Glance runs on WorkManager. The version it asks for brings a Room whose rules don't keep
    // WorkManager's database in a minified build, which then can't start.
    implementation(libs.androidx.work.runtime)
    // Keeping maps in a folder follows the app into and out of the foreground.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)
    // Pictures from the web, in the details and full screen.
    implementation(libs.coil.network.okhttp)
    // Pictures full screen, zoomed and flicked away.
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.telephoto.flick)
    // Video files played in the app, from maps and the web.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.ui.compose.material3)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.aboutlibraries.core)
    implementation(libs.markdown.renderer.coil)
    // QR codes for the address of a presentation in browsers.
    implementation(libs.zxing.core)
    // Installs the baseline profile on first launch when the store didn't, as for downloaded APKs.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.compose.ui.tooling)

    // Google Cast, in the play build only: its button and dialogs are built on AppCompat's themes,
    // and find TVs with the media router.
    "playImplementation"(libs.androidx.appcompat)
    "playImplementation"(libs.androidx.mediarouter)
    "playImplementation"(libs.play.services.cast.framework)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
