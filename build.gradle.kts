plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// ktlint checks the Kotlin code style with the app's own rules, in code-style/. Its
// standard rules are off (.editorconfig): they'd rewrite much more than the style asks.
val ktlint: Configuration by configurations.creating

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }

    ktlint(project(":code-style"))
}

val kotlinFiles = listOf("app/src/**/*.kt", "baselineprofile/src/**/*.kt", "code-style/src/**/*.kt", "*.kts", "*/*.kts")

tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks the Kotlin code style."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(listOf("--relative") + kotlinFiles)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Applies the Kotlin code style."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(listOf("--format", "--relative") + kotlinFiles)
}
