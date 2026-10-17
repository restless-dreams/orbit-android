// The app's own ktlint rules: blank lines around blocks and around documented members.
// The root build runs ktlint with them (`./gradlew ktlintCheck`, `./gradlew ktlintFormat`).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly(libs.ktlint.cli.ruleset.core)
    compileOnly(libs.ktlint.rule.engine.core)

    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.slf4j.nop)
}
