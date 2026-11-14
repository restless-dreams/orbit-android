package com.hyuchia.orbit.data

/** The folder beside a map's text that keeps its pictures, videos and sounds; the map points at them as `media/<name>`. */
const val MEDIA_DIRECTORY = "media"

/**
 * How a map names the files in its media folder ([MEDIA_DIRECTORY]) and
 * points at them from its text, in one place: the names Orbit gives, the
 * references to them, and a live photo's motion beside its still.
 */
object MediaNames {
    /** A name of Orbit's own in a media folder, to put in other patterns: letters, digits and `._-`, starting with neither a dot nor a dash, so nothing steps outside the folder. */
    const val PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*"

    /** A whole name of Orbit's own (see [PATTERN]). */
    val NAME = Regex(PATTERN)

    /**
     * A reference to one of the map's own files: an address that starts
     * with `media/` (or `./media/`: the `./` is its first group, the name its
     * second), as Orbit reads one in a picture or a link (`![](media/…)`),
     * an idea's attributes or a YAML map (`image: media/…`, quoted or not,
     * and `{"src":media/…}`). So it comes after nothing, a space, a
     * parenthesis or a quote: not in a longer path or word
     * (`example.com/media/`, `multimedia/`), an address's query or fragment
     * (`?q=media/…`) or code (`` `media/…` ``), where it's no file of the map's.
     */
    private val REFERENCE = Regex("""(?:(?<![^\s("'])|(?<=["']:))(\./)?$MEDIA_DIRECTORY/($PATTERN)""")

    /**
     * A web address in a map's text, from its scheme (`https:`, `mailto:`),
     * `//` or `www.` to where it ends: a space, a quote, an angle or square
     * bracket, a backtick, or a parenthesis it didn't open, as a link's
     * address ends at its `)` and `…/wiki/Orbit_(film)` goes on past one.
     * Whatever it says, nothing in it is one of the map's own files. A
     * scheme is looked for only where a run of its letters starts, and read
     * once, so a long word costs one pass rather than one for each letter.
     */
    private val WEB_ADDRESS = Regex("""(?:(?<![A-Za-z0-9+.-])[A-Za-z][A-Za-z0-9+.-]*+:|//|www\.)(?:[^\s()<>"'`\[\]]|\([^\s()<>"'`\[\]]*\))*""")

    /** A live photo's motion as Orbit names it, beside its still: `picture-1-motion.mp4` for `picture-1.jpg`. Its first group is the still's name without its extension. */
    private val MOTION = Regex("""([A-Za-z0-9._-]+)-motion\.(mp4|mov)""")

    /** The names `markdown` points at in its map's media folder, each once, in the order it first does. */
    fun references(markdown: String): List<String> = places(markdown).map { it.groupValues[2] }.distinct().toList()

    /** `markdown` with each reference to `name` in its map's media pointing at `renamed` instead. */
    fun renamed(markdown: String, name: String, renamed: String): String =
        replaced(markdown) { place -> if (place.groupValues[2] == name) "${place.groupValues[1]}$MEDIA_DIRECTORY/$renamed" else null }

    /**
     * `markdown` with each place it points at one of its map's files (see
     * [references]) written as `replacement` gives it: the place is the whole
     * of `./media/<name>` or `media/<name>`, its name the second group. One it
     * gives null for stays as it is.
     */
    fun replaced(markdown: String, replacement: (MatchResult) -> String?): String {
        val written = StringBuilder()
        var end = 0

        for (place in places(markdown)) {
            val instead = replacement(place) ?: continue
            written.append(markdown, end, place.range.first).append(instead)
            end = place.range.last + 1
        }

        return written.append(markdown, end, markdown.length).toString()
    }

    /**
     * Each place `markdown` points at one of its map's files, but those in a
     * web address: the whole of `./media/<name>` or `media/<name>`, its name
     * the second group. Both come in the text's order, so each address is
     * looked at once, however many there are.
     */
    fun places(markdown: String): Sequence<MatchResult> {
        val addresses = WEB_ADDRESS.findAll(markdown).map { it.range }.iterator()
        var address = IntRange.EMPTY

        return REFERENCE.findAll(markdown).filter { place ->
            while (address.last < place.range.first && addresses.hasNext()) {
                address = addresses.next()
            }

            place.range.first !in address
        }
    }

    /**
     * `markdown` with its code written over, so what's left names only the
     * files the map shows: Orbit shows code as it's written, so a `media/…`
     * in code is only words there, as Orbit's own reading of a link has it.
     * Each character of the code but spaces and line breaks becomes a
     * backtick, so every other character stays where it was: a place found
     * in what's left is that place in `markdown`. What's code is what
     * Orbit's reader takes for code ([MapCode]).
     */
    fun withoutCode(markdown: String): String {
        val code = MapCode.ranges(markdown)

        if (code.isEmpty()) {
            return markdown
        }

        val written = StringBuilder(markdown)

        for (range in code) {
            for (index in range) {
                if (!written[index].isWhitespace()) {
                    written.setCharAt(index, '`')
                }
            }
        }

        return written.toString()
    }

    /** The name of a live photo's motion beside the still named `stem` (without its extension): `stem-motion.mp4`. */
    fun motionName(stem: String, extension: String): String = "$stem-motion.$extension"

    /** Whether `name` is a live photo's motion, as Orbit names it. */
    fun isMotion(name: String): Boolean = MOTION.matches(name)

    /** The name, without its extension, of the still a motion is beside; null when `name` isn't a motion's. */
    fun stillOf(name: String): String? = MOTION.matchEntire(name)?.groupValues?.get(1)
}
