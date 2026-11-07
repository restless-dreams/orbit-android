package com.hyuchia.orbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the release builds ship, as the lock has it (`app/gradle.lockfile`,
 * from the module's folder, where Gradle runs its tests): the libre build
 * carries no Google Play services nor any other closed code (AGENTS.md), and
 * the play build only the ones LICENSE-EXCEPTION.md names.
 */
class LibreBuildTest {
    /** Each release build's locked libraries, `group:name`, by its classpath. */
    private val shipped: Map<String, Set<String>> = lockedLibraries(File("gradle.lockfile").readLines())

    private fun shippedBy(build: String, groups: List<String>): List<String> =
        shipped[build].orEmpty().filter { module -> groups.any { module.startsWith(it) } }.sorted()

    @Test
    fun `both release builds are locked`() {
        assertTrue(shipped[LIBRE].orEmpty().isNotEmpty())
        assertTrue(shipped[PLAY].orEmpty().isNotEmpty())
    }

    @Test
    fun `the libre build ships no Google Play services, Firebase or Play Store libraries`() {
        assertEquals(emptyList<String>(), shippedBy(LIBRE, listOf(PLAY_SERVICES, "com.google.firebase:", "com.google.android.play:")))
    }

    @Test
    fun `the play build ships only the Google Play services LICENSE-EXCEPTION_md names`() {
        val named = Regex("`(com\\.google\\.android\\.gms:[a-z-]+)`").findAll(File("../LICENSE-EXCEPTION.md").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(emptyList<String>(), shippedBy(PLAY, listOf(PLAY_SERVICES)).filterNot { it in named })
    }

    @Test
    fun `a library locked at another version for each build is in both builds`() {
        // Gradle locks such a library on a line for each version, as appcompat is: one build's mustn't hide the other's.
        val locked = lockedLibraries(
            listOf(
                "com.google.android.gms:play-services-basement:18.4.0=$LIBRE",
                "com.google.android.gms:play-services-basement:18.5.0=$PLAY",
                "androidx.core:core:1.17.0=$LIBRE,$PLAY",
            ),
        )

        assertEquals(setOf("com.google.android.gms:play-services-basement", "androidx.core:core"), locked[LIBRE])
        assertEquals(setOf("com.google.android.gms:play-services-basement", "androidx.core:core"), locked[PLAY])
    }

    private companion object {
        /** Google Play services, which are closed: LICENSE-EXCEPTION.md lets the play build link them. */
        const val PLAY_SERVICES = "com.google.android.gms:"

        const val LIBRE = "libreReleaseRuntimeClasspath"
        const val PLAY = "playReleaseRuntimeClasspath"
    }
}

/**
 * The libraries a lockfile's `lines` lock for each classpath, `group:name`
 * by classpath. A library locked at one version for one build and another
 * for the other has a line for each, and is in both builds' sets.
 */
internal fun lockedLibraries(lines: List<String>): Map<String, Set<String>> = lines
    .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("empty=") }
    .flatMap { line ->
        val (module, builds) = line.split('=', limit = 2)
        builds.split(',').map(String::trim).filter(String::isNotEmpty).map { build -> build to module.substringBeforeLast(':') }
    }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, modules) -> modules.toSet() }
