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
    /** Each locked library, `group:name`, by the release builds that ship it. */
    private val shipped: Map<String, Set<String>> = File("gradle.lockfile").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("empty=") }
        .associate { line ->
            val (module, builds) = line.split('=', limit = 2)
            module.substringBeforeLast(':') to builds.split(',').map(String::trim).toSet()
        }

    private fun shippedBy(build: String, groups: List<String>): List<String> =
        shipped.filter { (module, builds) -> build in builds && groups.any { module.startsWith(it) } }.keys.sorted()

    @Test
    fun `both release builds are locked`() {
        assertTrue(shipped.values.any { "libreReleaseRuntimeClasspath" in it })
        assertTrue(shipped.values.any { "playReleaseRuntimeClasspath" in it })
    }

    @Test
    fun `the libre build ships no Google Play services, Firebase or Play Store libraries`() {
        assertEquals(emptyList<String>(), shippedBy("libreReleaseRuntimeClasspath", listOf(PLAY_SERVICES, "com.google.firebase:", "com.google.android.play:")))
    }

    @Test
    fun `the play build ships only the Google Play services LICENSE-EXCEPTION_md names`() {
        val named = Regex("`(com\\.google\\.android\\.gms:[a-z-]+)`").findAll(File("../LICENSE-EXCEPTION.md").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(emptyList<String>(), shippedBy("playReleaseRuntimeClasspath", listOf(PLAY_SERVICES)).filterNot { it in named })
    }

    private companion object {
        /** Google Play services, which are closed: LICENSE-EXCEPTION.md lets the play build link them. */
        const val PLAY_SERVICES = "com.google.android.gms:"
    }
}
