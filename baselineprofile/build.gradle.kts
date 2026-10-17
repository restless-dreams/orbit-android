plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.hyuchia.orbit.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Recording a profile without root takes Android 13; measuring startup works from the app's own minimum.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // The app's two builds, so each can be profiled and measured.
    flavorDimensions += "distribution"

    productFlavors {
        create("libre") {
            dimension = "distribution"
        }

        create("play") {
            dimension = "distribution"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Profiles are recorded on the connected device: with several, ANDROID_SERIAL picks one (see CONTRIBUTING.md).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
}
