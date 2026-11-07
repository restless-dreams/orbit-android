plugins {
    alias(libs.plugins.android.test)
}

/**
 * Journeys through Orbit as people take them, run on a phone or an emulator
 * against whichever build of Orbit is installed there: scripts/smoke-test.sh
 * runs them on the minified release builds, and scripts/device-tests.sh runs
 * the process death journey on the debug build. They work Orbit through its
 * screen alone (UiAutomator) and the shell, never its code, so R8's renaming
 * doesn't touch them, and they run in a process of their own, so they can
 * stop Orbit's as Android does and start it again. They also act as another
 * app, opening and sharing files with Orbit through a provider of their own.
 */
android {
    namespace = "com.hyuchia.orbit.journeys"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Built against the libre build, and run against either: both are com.hyuchia.orbit, with the same screens.
        missingDimensionStrategy("distribution", "libre")
    }

    targetProjectPath = ":app"

    // The journeys run in their own process, not Orbit's: they stop Orbit's and start it again.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
