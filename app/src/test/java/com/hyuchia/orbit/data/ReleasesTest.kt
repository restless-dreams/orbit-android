package com.hyuchia.orbit.data

import com.hyuchia.orbit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every release, by its versionCode: the folder in src/test/resources/releases/ with the files it left on a phone. */
private val RELEASES = mapOf(1 to "0.1.0", 2 to "0.1.1", 3 to "0.2.0", 4 to "0.3.0", 5 to "0.4.0", 6 to "0.5.0")

/**
 * The upgrade tests (ReleaseDataTest) open every release's files, which each
 * release adds as it's published (RELEASE.md). A release's files can only be
 * made once it's out, so they're asked for from the next version on: a new
 * version can't start without its last release's files.
 */
class ReleasesTest {
    private val folders: Set<String> = File(requireNotNull(ReleasesTest::class.java.classLoader?.getResource("releases")) { "No release data on the test classpath" }.toURI())
        .listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.toSet()

    @Test
    fun `every release before this version left its files for the upgrade tests`() {
        assertTrue("Add this version, ${BuildConfig.VERSION_NAME}, to RELEASES as versionCode ${BuildConfig.VERSION_CODE}", RELEASES[BuildConfig.VERSION_CODE] == BuildConfig.VERSION_NAME)
        val missing = RELEASES.filterKeys { it < BuildConfig.VERSION_CODE }.values.filter { it !in folders }
        assertEquals("Releases without their files in src/test/resources/releases/ (its README says how to make them)", emptyList<String>(), missing)
    }

    @Test
    fun `every folder of release files is a release up to this version`() {
        // This version's own files come once it's out, before the next version starts.
        val released = RELEASES.filterKeys { it <= BuildConfig.VERSION_CODE }.values.toSet()
        assertEquals("Folders that aren't a release up to this one", emptySet<String>(), folders - released - "before-0.1.0")
    }

    @Test
    fun `versions are ordered part by part`() {
        assertTrue(compareVersions("0.10.0", "0.9.1") > 0)
        assertTrue(compareVersions("0.4.0", "0.4.0") == 0)
        assertTrue(compareVersions("0.3.0", "0.4.0") < 0)
        assertTrue(compareVersions("1.0", "0.99.99") > 0)
    }
}
