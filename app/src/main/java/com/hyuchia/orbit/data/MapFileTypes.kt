package com.hyuchia.orbit.data

/**
 * The types and extensions maps come as, in one place for every way in:
 * the manifest's filters for opening and sharing files (`MapFileTypesTest`
 * checks them against these), the file pickers, drops and imports. Other
 * apps' formats carry their own ([OtherFormat]).
 */
object MapFileTypes {
    /** Markdown, as Orbit keeps its maps. */
    val MARKDOWN = setOf("text/markdown", "text/x-markdown")

    /** YAML, the other way Orbit writes maps. */
    val YAML = setOf("application/yaml", "text/yaml", "application/x-yaml")

    /** Zips of maps, as different apps type them. */
    val ZIP = setOf("application/zip", "application/x-zip-compressed", "application/x-zip")

    /**
     * What OPML often says it is. The file picker offers it and drops take
     * it, but the manifest doesn't: Orbit would be offered for every XML file.
     */
    val XML = setOf("text/xml", "application/xml")

    /** What a file that says nothing of what it is comes as: it can be a map, and its name says. */
    const val ANY_FILE = "application/octet-stream"

    /** Every type a map comes as, Orbit's own and other apps': what the manifest's filters take. */
    val MAPS: Set<String> = MARKDOWN + YAML + ZIP + OtherApps.TYPES

    /** The extensions of maps written as text, which are kept as written even when an app says they're plain text. */
    val TEXT_EXTENSIONS = setOf("md", "markdown", "yaml", "yml")

    /** Every extension maps are named with, Orbit's own and other apps'. */
    val EXTENSIONS: Set<String> = TEXT_EXTENSIONS + "zip" + OtherFormat.entries.flatMap { it.extensions }

    /** What the file picker offers to open: every map, text to make one of, OPML that says it's XML, and files that say nothing. */
    val FILE_PICKER: List<String> = (MAPS + "text/plain" + XML + ANY_FILE).toList()

    /** What Restore's picker offers: zips of every map, and files that say nothing, as a zip saved by some apps does. */
    val ZIPS_PICKER: List<String> = (ZIP + ANY_FILE).toList()
}
