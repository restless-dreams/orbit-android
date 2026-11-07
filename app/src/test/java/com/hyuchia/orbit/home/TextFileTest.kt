package com.hyuchia.orbit.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** A map's text shared alone, from its card on Home. */
class TextFileTest {
    @Test
    fun `a map's text goes as the file it is, a YAML map's as YAML`() {
        assertEquals(TextFile.Markdown, TextFile.of("# Trip to Japan\n\n- Tokio\n"))
        assertEquals(TextFile.Yaml, TextFile.of("title: Trip to Japan\nnodes:\n  - text: Trip to Japan\n"))
        assertEquals(TextFile.Yaml, TextFile.of("nodes:\n  - Trip to Japan\n"))
        // A markdown note that happens to start with "nodes:" is still markdown, as for Orbit.
        assertEquals(TextFile.Markdown, TextFile.of("# Trip to Japan\n\nnodes: three of them\n"))

        // Named and typed as what it is, so the app it goes to opens it as such.
        assertEquals("yaml" to "application/yaml", TextFile.Yaml.extension to TextFile.Yaml.type)
        assertEquals("md" to "text/markdown", TextFile.Markdown.extension to TextFile.Markdown.type)
    }
}
