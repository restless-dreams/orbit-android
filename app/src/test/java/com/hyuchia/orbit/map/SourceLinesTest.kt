package com.hyuchia.orbit.map

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the source editor puts the caret for what Orbit noticed on a line of the text. */
class SourceLinesTest {
    @Test
    fun `a note about a line puts the caret at its start, however the text's lines end`() {
        // Written on Windows: each line ends in a carriage return and a line feed, two characters.
        val windows = "# Trip\r\n- Okay\r\n- Bad <!-- orbit colour: red -->\r\n"
        assertEquals(windows.indexOf("- Bad"), lineStart(windows, 3))
        assertEquals(windows.indexOf("- Okay"), lineStart(windows, 2))

        val unix = "# Trip\n- Okay\n- Bad <!-- orbit colour: red -->\n"
        assertEquals(unix.indexOf("- Bad"), lineStart(unix, 3))

        // A carriage return alone ends a line too, as Orbit reads it, and line endings can be mixed.
        val mixed = "# Trip\r- Okay\n- Bad\r\n- Last"
        assertEquals(mixed.indexOf("- Okay"), lineStart(mixed, 2))
        assertEquals(mixed.indexOf("- Bad"), lineStart(mixed, 3))
        assertEquals(mixed.indexOf("- Last"), lineStart(mixed, 4))

        // The first line starts the text; one past the last, as text changed since Orbit looked, is its end.
        assertEquals(0, lineStart(windows, 1))
        assertEquals(windows.length, lineStart(windows, 4))
        assertEquals(windows.length, lineStart(windows, 40))
        assertEquals(0, lineStart("", 3))
    }
}
