package com.hyuchia.orbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every language the app speaks has all of its parts, not only its strings
 * (TRANSLATING.md, "Adding a language"). A language missing its maps, its
 * icon search, the TV's words or its store listing falls back to English
 * there on its own, and nothing else says so.
 */
class LanguagesTest {
    /** The app's sources, from the module's folder, where Gradle runs its tests. */
    private val main = File("src/main")

    /** Each language with strings in res/: the build takes its languages from the same folders (app/build.gradle.kts). */
    private val languages: List<String> = File(main, "res").listFiles().orEmpty()
        .filter { folder -> folder.listFiles().orEmpty().any { it.name.startsWith("strings") && it.extension == "xml" } }
        .mapNotNull { LANGUAGE_FOLDER.matchEntire(it.name)?.groupValues?.get(1) }
        .sorted()

    /** The language's code alone, as the assets, the emoji names and the TV's words name it: `pt` for `pt-rBR`. */
    private fun code(language: String): String = language.removePrefix("b+").substringBefore('-').substringBefore('+')

    /** The files under `folder`, by their path in it. */
    private fun filesIn(folder: File): List<String> =
        folder.walkTopDown().filter { it.isFile }.map { it.relativeTo(folder).path }.sorted().toList()

    @Test
    fun `the languages are read from res, where Spanish is`() {
        assertTrue(languages.toString(), "es" in languages)
    }

    @Test
    fun `each language has the welcome map, the templates and the privacy policy`() {
        val assets = File(main, "assets")
        val translated = listOf("welcome.md", "welcome.json", "privacy.md") + filesIn(File(assets, "templates")).map { "templates/$it" }

        for (language in languages) {
            assertEquals(language, emptyList<String>(), translated.filterNot { File(assets, "${code(language)}/$it").isFile })
        }
    }

    @Test
    fun `each language has the debug builds' big map`() {
        val assets = File("src/debug/assets")
        val samples = filesIn(File(assets, "samples"))

        for (language in languages) {
            assertEquals(language, samples, filesIn(File(assets, "${code(language)}/samples")))
        }
    }

    @Test
    fun `each language has the icon search's emoji names`() {
        for (language in languages + "en") {
            assertTrue(language, File(main, "assets/emoji/${code(language)}.txt").isFile)
        }
    }

    @Test
    fun `each language has the TV's words`() {
        val words = File("../web/words.ts").readText()
        val tvWords = words.substringAfter("const TV_WORDS").substringBefore("};")

        for (language in languages + "en") {
            assertTrue(language, Regex("""(?m)^\s*${Regex.escape(code(language))}:\s*\{""").containsMatchIn(tvWords))
        }
    }

    @Test
    fun `each language has its store listing`() {
        val listings = File("../fastlane/metadata/android").list().orEmpty().toList()

        for (language in languages + "en") {
            assertTrue("$language in $listings", listings.any { it == code(language) || it.startsWith("${code(language)}-") })
        }
    }

    private companion object {
        /** A folder of values in a language, by its qualifier: `values-es`, `values-pt-rBR`, `values-b+sr+Latn`; not `values-night`. */
        val LANGUAGE_FOLDER = Regex("values-([a-z]{2,3}(-r[A-Z]{2})?|b\\+[A-Za-z0-9+]+)")
    }
}
