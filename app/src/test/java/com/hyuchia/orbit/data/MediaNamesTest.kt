package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a map names the files in its media folder, and points at them. */
class MediaNamesTest {
    @Test
    fun `pictures are found by their place in the media folder`() {
        val markdown = "# Trip\n- Beach ![](media/picture-abc.jpg)\n- <!-- orbit image: media/map_2.png -->\n- media/../secret"
        assertEquals(setOf("picture-abc.jpg", "map_2.png"), MediaNames.references(markdown).toSet())
    }

    @Test
    fun `sounds are found there too, so they go to the folder and come back from it`() {
        val markdown = "# Trip\n## Beach <!-- orbit audio: media/memo-mf2k1x.mp4 -->\n## Song\n<!-- orbit\naudio:\n  src: media/sound-mf2k1x.mp3\n  caption: Waves\n-->"
        assertEquals(setOf("memo-mf2k1x.mp4", "sound-mf2k1x.mp3"), MediaNames.references(markdown).toSet())
        assertTrue(MediaNames.references(markdown).all(::isMediaName))
    }

    @Test
    fun `a map's text says which pictures and videos it points to`() {
        assertEquals(
            listOf("picture-1.jpg", "video-2.mp4", "poster-2.jpg"),
            MediaNames.references("![](media/picture-1.jpg) <!-- orbit video: media/video-2.mp4, poster: media/poster-2.jpg --> ![](media/picture-1.jpg) multimedia/x.jpg"),
        )
    }

    @Test
    fun `a web address's media folder, or another folder ending in media, isn't the map's own`() {
        val markdown = "![](https://example.com/media/photo.jpg) see multimedia/x.jpg, my-media/y.jpg and ![](./media/z.jpg)"
        assertEquals(listOf("z.jpg"), MediaNames.references(markdown))
    }

    @Test
    fun `text in a web address is never one of the map's own files`() {
        val markdown = """
            # Trip
            - Search [the results](https://example.com/?q=media/photo.jpg)
            - See https://example.com/#media/beach.jpg, or <https://example.com/a=media/sea.jpg>
            - [The film](https://en.wikipedia.org/wiki/Orbit_(media/film.jpg))
            - Write to mailto:me@example.com?subject=media/note.jpg <!-- orbit link: "https://example.com/(media/link.jpg)" -->
            - www.example.com/?media/www.jpg and //example.com/(media/relative.jpg)
            - Here ![](media/here.jpg)
        """.trimIndent()
        assertEquals(listOf("here.jpg"), MediaNames.references(markdown))
    }

    @Test(timeout = 10_000)
    fun `a long text is read in one pass, its web addresses too`() {
        val word = "a".repeat(2_000_000)
        val addresses = (1..20_000).joinToString(" ") { "https://example.com/(media/search-$it.jpg) ![](media/picture-$it.jpg)" }
        assertEquals(listOf("photo.jpg"), MediaNames.references("$word ![](media/photo.jpg) $word: https://example.com/?q=media/other.jpg"))
        assertEquals(20_000, MediaNames.references(addresses).size)
    }

    @Test
    fun `media written where no address starts isn't one of the map's own files`() {
        // Code, a node's link, a query, a list or a mapping's key: Orbit reads none of them as a file of the map's.
        val markdown = "# Trip\n- `media/code.jpg` [Up](#media/node) [Ask](?media/query.jpg)\n- <!-- orbit tags: [media/tag.jpg], other: {media/key.jpg} -->\n- a=media/equals.jpg"
        assertEquals(emptyList<String>(), MediaNames.references(markdown))
    }

    @Test
    fun `a file named only in code goes with the map to no one, and stays in what it carries`() {
        val markdown = listOf(
            "# Trip",
            "- Inline ` media/inline.jpg ` and ``media/double.jpg`` <!-- orbit image: media/attributes.jpg -->",
            "- Fenced",
            "  ```",
            "  ![](media/fenced.jpg)",
            "  ```",
            "~~~",
            "<!-- orbit image: media/in-tildes.jpg -->",
            "~~~",
            "- Shown ![`its` picture](media/shown.jpg)",
            "  <!-- orbit",
            "  caption: \"Say `hi`\"",
            "  audio: media/memo-1.mp4",
            "  -->",
            "      media/indented.jpg",
            "- Open",
            "  ```",
            "  a fence its note never closes",
            "- After ![](media/after.jpg) and ` an open backtick ![](media/open.jpg)",
            "",
        ).joinToString("\n")

        // What the map shows, and its attributes: code in a comment is no code, and a fence its note never closes is code to the note's end.
        assertEquals(
            listOf("attributes.jpg", "shown.jpg", "memo-1.mp4", "indented.jpg", "after.jpg", "open.jpg"),
            sharedNames(markdown),
        )
        // The map keeps them all, for its folder, its backups and Export all, as every release has.
        assertEquals(
            listOf("inline.jpg", "attributes.jpg", "fenced.jpg", "in-tildes.jpg", "shown.jpg", "memo-1.mp4", "indented.jpg", "after.jpg", "open.jpg"),
            carriedNames(markdown),
        )

        // Only code is written over, each character in its place, and nothing else changes.
        val written = MediaNames.withoutCode(markdown)
        assertEquals(markdown.length, written.length)
        assertTrue(markdown.indices.filter { written[it] != markdown[it] }.all { written[it] == '`' && !markdown[it].isWhitespace() })
        assertTrue(written.contains("<!-- orbit image: media/attributes.jpg -->") && written.contains("  audio: media/memo-1.mp4\n"))
        assertFalse(written.contains("inline") || written.contains("fenced") || written.contains("in-tildes"))
    }

    @Test
    fun `a map indented as a whole is read as Orbit reads it, without the indentation its lines share`() {
        // Each as Orbit's parseOrbit reads it, which takes that indentation away before anything else: a title's code is words.
        val code = listOf(
            "    # Trip\n    ## Idea snake_case `a other_name ![](media/title.jpg) `b`\n",
            "\t# Trip\n\t- Idea snake_case `a other_name ![](media/tab.jpg) `b`\n",
            // A blank line with less of it loses what it has.
            "    # Trip\n  \n    - Idea snake_case `a other_name ![](media/blank.jpg) `b`\n",
            "    # Trip\n    - Idea\n      Note\n      ```\n      ![](media/fenced.jpg)\n      ```\n",
            // A byte-order mark at the start is no part of the text: the first line is a title still.
            "\uFEFF- Idea snake_case `a other_name ![](media/mark.jpg) `b`\n",
            // Nor inside a fence around the whole map, where the map starts.
            "```orbit\n\uFEFF    # Trip\n    ## Idea snake_case `a other_name ![](media/fenced-mark.jpg) `b`\n```\n",
            // A line of a byte-order mark, or of a no-break space, is blank, as JavaScript's trim finds it.
            "    # Trip\n\uFEFF\n    ## Idea snake_case `a other_name ![](media/blank-mark.jpg) `b`\n",
            "    # Trip\n\u00A0\n    ## Idea snake_case `a other_name ![](media/blank-space.jpg) `b`\n",
            // Lines that share no indentation are read as they are.
            "  # Trip\n\t- Idea snake_case `a other_name ![](media/mixed.jpg) `b`\n",
        )

        for (markdown in code) {
            assertEquals(markdown, emptyList<String>(), sharedNames(markdown))
        }

        // What it shows goes, and the map keeps every file it names, for its folder, its backups and Export all.
        assertEquals(listOf("shown.jpg", "own-line.jpg"), sharedNames("    # Trip\n    - Idea ![](media/shown.jpg)\n    - Other\n      ![](media/own-line.jpg)\n"))
        assertEquals(listOf("title.jpg"), carriedNames(code.first()))
        assertEquals(code.first().length, MediaNames.withoutCode(code.first()).length)
    }

    @Test
    fun `a map in a fence of Orbit's own is no code, nor is a YAML map's note outside its own fences`() {
        assertEquals(listOf("wrapped.jpg"), sharedNames("```orbit\n# Trip\n![](media/wrapped.jpg)\n```\n"))
        assertEquals(listOf("wrapped.jpg"), sharedNames("~~~orbit height=480\n# Trip\n- <!-- orbit image: media/wrapped.jpg -->\n~~~"))

        val yaml = "title: Trip\nnodes:\n  - text: Trip\n    note: |\n      ```\n      media/fenced.jpg\n      ```\n    image: media/trip.jpg\n    children:\n      - text: Code\n        note: \"`media/code.jpg`\"\n"
        assertEquals(listOf("trip.jpg"), sharedNames(yaml))
    }

    @Test
    fun `what Orbit's reader shows as a picture goes, whatever backticks and fences are near it`() {
        // Each as Orbit 0.5.0's parseOrbit reads it: the idea's picture, or a picture or link in its note.
        val shown = mapOf(
            // A fence four spaces past where its note starts is text: the picture on a line of its own is the idea's.
            "# Trip\n- Idea\n  Note\n      ```\n      ![](media/over-indented.jpg)\n      ```\n" to "over-indented.jpg",
            "# Trip\nNote\n    ```\n    ![](media/top.jpg)\n    ```\n" to "top.jpg",
            "# Trip\n- Idea\n  - Child\n    Note\n        ```\n        ![](media/nested.jpg)\n        ```\n" to "nested.jpg",
            // A picture or a link that starts before a backtick is no code, backticks in its words included.
            "# Trip\n- Idea\n  Note ![a`b](media/alt.jpg) `c`\n" to "alt.jpg",
            "# Trip\n- Idea\n  Note [a`b](media/linked.jpg) `c`\n" to "linked.jpg",
            // A picture on a line of its own is the idea's, whatever its title holds.
            "# Trip\n- Idea\n  ![a`b](media/titled.jpg \"t`\")\n" to "titled.jpg",
        )

        for ((markdown, name) in shown) {
            assertEquals(markdown, listOf(name), sharedNames(markdown))
        }

        // What it shows as code stays out: a fence up to three spaces past where its note starts, at any depth.
        assertEquals(emptyList<String>(), sharedNames("# Trip\n- Idea\n  Note\n     ```\n     ![](media/code.jpg)\n     ```\n"))
        assertEquals(emptyList<String>(), sharedNames("# Trip\n- Idea\n  - Child\n    Note\n       ```\n       ![](media/code.jpg)\n       ```\n"))
    }

    @Test
    fun `a note's code is read as Orbit's note reader reads it, a title's as a title reads it`() {
        // Each as Orbit 0.5.0's reader reads it. In a note: bold, struck or emphasized words that start first take their
        // backticks with them, and a run of backticks closes at the first run as long, even inside a longer one.
        val shown = mapOf(
            "# Trip\n- Idea\n  snake_case `a other_name ![](media/emphasis.jpg) `b`\n" to "emphasis.jpg",
            "# Trip\n- Idea\n  **bold `a** ![](media/bold.jpg) `b`\n" to "bold.jpg",
            "# Trip\n- Idea\n  ~~struck `a~~ ![](media/struck.jpg) `b`\n" to "struck.jpg",
            "# Trip\n- Idea\n  ` a ``` ![](media/longer-run.jpg) `\n" to "longer-run.jpg",
            "# Trip\n- Idea\n  <https://example.com/`a> ![](media/address.jpg) `b`\n" to "address.jpg",
        )

        for ((markdown, name) in shown) {
            assertEquals(markdown, listOf(name), sharedNames(markdown))
        }

        val code = listOf(
            // A title reads its code first, from a run to the next exactly as long: its picture is words there.
            "# Trip\n- Idea snake_case `a other_name ![](media/title.jpg) `b`\n",
            // A span runs on across its paragraph's lines.
            "# Trip\n- Idea\n  Note `a\n  ![](media/across.jpg) b`\n",
            // A fence never closed is code to its note's end, and one in a quote is code too.
            "# Trip\n- Idea\n  Note\n  ```\n  ![](media/unclosed.jpg)\n- Next\n",
            "# Trip\n- Idea\n  > ```\n  > ![](media/quoted.jpg)\n  > ```\n",
            "# Trip\n- Idea\n  > ```\n  > ![](media/quoted-unclosed.jpg)\n\n- Next\n",
        )

        for (markdown in code) {
            assertEquals(markdown, emptyList<String>(), sharedNames(markdown))
        }

        // What follows a fence its note never closes is read as ever.
        assertEquals(listOf("next.jpg"), sharedNames("# Trip\n- Idea\n  ```\n  ![](media/unclosed.jpg)\n- Next\n  ![](media/next.jpg)\n"))
    }

    @Test
    fun `a note's tables, quotes and lists are read block by block, as Orbit's note reader reads them`() {
        // Each as Orbit 0.5.0's note reader reads it: a table's cells each alone, and a quote as a note of its own,
        // whose lists, headings, quotes and tables are blocks of their own.
        val shown = mapOf(
            "# Trip\n- Idea\n  | a | b |\n  |---|---|\n  | `x | ![](media/row.jpg) |\n  | y` | z |\n" to "row.jpg",
            "# Trip\n- Idea\n  | `a | ![](media/header.jpg) |\n  |---|---|\n  | b` | c |\n" to "header.jpg",
            "# Trip\n- Idea\n  Note `a\n  | a | b |\n  |---|---|\n  | ![](media/under.jpg) b` | c |\n" to "under.jpg",
            "# Trip\n- Idea\n  | a | b |\n  |---|---|\n  | `c | d |\n  ![](media/row-line.jpg) e`\n" to "row-line.jpg",
            "# Trip\n- Idea\n  > `a\n  > - ![](media/quoted-item.jpg) c`\n" to "quoted-item.jpg",
            "# Trip\n- Idea\n  > `a\n  > # ![](media/quoted-heading.jpg)\n  > b`\n" to "quoted-heading.jpg",
            "# Trip\n- Idea\n  > ## `a\n  > ![](media/after-heading.jpg) b`\n" to "after-heading.jpg",
            "# Trip\n- Idea\n  > `a\n  > > ![](media/nested-quote.jpg) b`\n" to "nested-quote.jpg",
            "# Trip\n- Idea\n  > `a\n  > * * *\n  > ![](media/after-stars.jpg) b`\n" to "after-stars.jpg",
            // A list item's text goes on only with the lines indented under it.
            "# Trip\n- Idea\n  > - ![](media/item.jpg) `a\n  > b`\n" to "item.jpg",
            "# Trip\n- Idea\n  > | a | b |\n  > |---|---|\n  > | `x | ![](media/quoted-table.jpg) |\n  > | y` | z |\n" to "quoted-table.jpg",
            // `[![](…)` is a link to the file, whatever follows it, as the note reader reads one: it goes.
            "# Trip\n- Idea\n  See [![](media/linked.jpg)](javascript:alert(1)) here\n" to "linked.jpg",
        )

        for ((markdown, name) in shown) {
            assertEquals(markdown, listOf(name), sharedNames(markdown))
        }

        val words = listOf(
            // Code takes a cell's | with it, and a row's cells past its header's aren't shown.
            "# Trip\n- Idea\n  | a | b |\n  |---|---|\n  | `![](media/across-cells.jpg) | z` |\n",
            "# Trip\n- Idea\n  | a | b |\n  |---|---|\n  | c | d | ![](media/past-header.jpg) |\n",
            // A list item's text goes on across the lines indented under it, and lines after a list under it show as written.
            "# Trip\n- Idea\n  > - `a\n  >   ![](media/item-text.jpg) b`\n",
            "# Trip\n- Idea\n  > - a\n  >   - b\n  >   c ![](media/after-list.jpg)\n",
            // A quote's --- is no break: its paragraph goes on.
            "# Trip\n- Idea\n  > `a\n  > ---\n  > ![](media/after-dashes.jpg) b`\n",
        )

        for (markdown in words) {
            assertEquals(markdown, emptyList<String>(), sharedNames(markdown))
        }
    }

    @Test(timeout = 10_000)
    fun `a deep quote, a deep list and a long table row are read in one pass`() {
        val quotes = "# Trip\n- Idea\n  " + ">".repeat(200_000) + " ![](media/deep-quote.jpg)\n"
        val lists = "# Trip\n- Idea\n" + (0 until 2_000).joinToString("\n") { "  > " + " ".repeat(it) + "- item" } + "\n  >\n  > ![](media/after-lists.jpg)\n"
        val row = "# Trip\n- Idea\n  | a |\n  |---|\n  | " + (2_000 downTo 1).joinToString("") { "`".repeat(it) + "x" } + " ![](media/after-row.jpg) |\n"
        assertEquals(listOf("deep-quote.jpg"), sharedNames(quotes))
        assertEquals(listOf("after-lists.jpg"), sharedNames(lists))
        assertEquals(listOf("after-row.jpg"), sharedNames(row))
    }

    @Test
    fun `a YAML map's note is read a paragraph at a time, as Orbit's note reader reads it`() {
        // A note as YAML's block of text, folded or not, as a quoted text, or as plain text on lines under its key.
        fun note(value: String) = "title: Trip\nnodes:\n  - text: Trip\n    note: $value\n    image: media/trip.jpg\n"

        val shown = mapOf(
            note("|\n      `a\n\n      ![](media/apart.jpg) b`") to "apart.jpg",
            note("|\n      `a\n      - ![](media/item.jpg) b`") to "item.jpg",
            note("|\n      | a | b |\n      |---|---|\n      | `x | ![](media/cell.jpg) |\n      | y` | z |") to "cell.jpg",
            note("\"`a\\n- ![](media/quoted-item.jpg) b`\\n---\"") to "quoted-item.jpg",
            // A backtick in a key after the note's is no part of it.
            note("|\n      Use `code\n    link: \"https://example.com/`b\"") to "trip.jpg",
        )

        for ((yaml, name) in shown) {
            assertEquals(yaml, (listOf(name) + "trip.jpg").distinct(), sharedNames(yaml))
        }

        val code = listOf(
            note("|\n      `a\n      ![](media/across.jpg) b`"),
            note("|\n      - [ ] `a\n        ![](media/checklist.jpg) b`"),
            note(">\n      `a\n      - ![](media/folded.jpg) b`"),
            note("`a\n      ![](media/plain.jpg) b`"),
            note("\"`a\\n\\n`b ![](media/quoted.jpg) `\""),
        )

        for (yaml in code) {
            assertEquals(yaml, listOf("trip.jpg"), sharedNames(yaml))
        }
    }

    @Test(timeout = 10_000)
    fun `a bullet with nothing but spaces after it, or a line of marks, is read in one pass`() {
        val spaces = List(3) { "- " + " ".repeat(40_000) }.joinToString("\n")
        val marks = "_x*y~~z**w__v<https://".repeat(50_000) + " ![](media/after-marks.jpg)"
        assertEquals(listOf("after-spaces.jpg"), sharedNames("# Trip\n$spaces\n- Idea\n  ![](media/after-spaces.jpg)\n"))
        assertEquals(listOf("after-marks.jpg"), sharedNames("# Trip\n- Idea\n  $marks\n"))
    }

    @Test(timeout = 10_000)
    fun `a line of nothing but comments, pictures or backticks is read in one pass`() {
        val comments = "<!---->".repeat(150_000) + " ![](media/after-comments.jpg)"
        val brackets = "![](a(".repeat(100_000) + " ![](media/after-brackets.jpg)"
        val runs = (1..1_000).joinToString("") { "`".repeat(it) + "x" } + " ![](media/after-runs.jpg)"
        assertEquals(listOf("after-comments.jpg"), sharedNames("# Trip\n- $comments\n"))
        assertEquals(listOf("after-brackets.jpg"), sharedNames("# Trip\n- $brackets\n"))
        assertEquals(listOf("after-runs.jpg"), sharedNames("# Trip\n- $runs\n"))
    }

    @Test(timeout = 10_000)
    fun `a long text's code is found in one pass`() {
        val line = (1..2_000).joinToString(" ") { "`code $it` ``a`` <!-- c --> ![](media/picture-$it.jpg)" }
        val text = List(50) { line }.joinToString("\n") + "\n" + "`".repeat(40_000) + " " + (1..2_000).joinToString("") { "`".repeat(it % 200 + 1) + "x" }
        assertEquals(2_000, sharedNames(text).size)
    }

    @Test
    fun `every place Orbit reads one of the map's files from is found`() {
        val markdown = """
            # Trip
            ## Beach
            ![The beach](media/beach.jpg)
            ## Trail
            [![Its poster](media/trail-poster.jpg)](media/trail.mp4)
            ## Summit <!-- orbit image: media/summit.jpg -->
            ## Lake
            <!-- orbit
            image:
              src: "media/lake.jpg"
              motion:
                src: './media/lake-motion.mp4'
                still: 1.5
            video: { src: media/lake-video.mp4, thumbnail: media/lake-poster.jpg }
            audio: {"src":media/lake-memo.mp4}
            -->
            Notes with ![a picture](media/note.jpg) and [a guide]( media/guide.pdf "The guide").
            ### [Its report](media/report.pdf)
        """.trimIndent()
        val names = listOf(
            "beach.jpg", "trail-poster.jpg", "trail.mp4", "summit.jpg", "lake.jpg", "lake-motion.mp4", "lake-video.mp4", "lake-poster.jpg",
            "lake-memo.mp4", "note.jpg", "guide.pdf", "report.pdf",
        )
        assertEquals(names, MediaNames.references(markdown))

        val yaml = "title: Trip\nnodes:\n  - text: Trip\n    image: media/trip.jpg\n    children:\n      - text: Talk\n        audio:\n          src: media/talk.mp4\n" +
            "        note: \"See ![it](media/note.jpg)\"\n      - text: Walk\n        video: \"media/walk.mp4\"\n        link: 'media/walk.pdf'\n"
        assertEquals(listOf("trip.jpg", "talk.mp4", "note.jpg", "walk.mp4", "walk.pdf"), MediaNames.references(yaml))
    }

    @Test
    fun `a renamed picture is renamed where the map points at it, and nowhere else`() {
        val markdown = "![](media/photo.jpg) ![](media/photo.jpg.old) ![](https://example.com/media/photo.jpg) ![](./media/photo.jpg) [Search](https://example.com/?q=media/photo.jpg)"
        assertEquals(
            "![](media/photo-2.jpg) ![](media/photo.jpg.old) ![](https://example.com/media/photo.jpg) ![](./media/photo-2.jpg) [Search](https://example.com/?q=media/photo.jpg)",
            MediaNames.renamed(markdown, "photo.jpg", "photo-2.jpg"),
        )
    }

    @Test
    fun `a live photo's motion is named after its still, and known by that name`() {
        assertEquals("picture-1-motion.mp4", MediaNames.motionName("picture-1", "mp4"))
        assertTrue(MediaNames.isMotion("picture-1-motion.mov"))
        assertEquals("picture-1", MediaNames.stillOf("picture-1-motion.mp4"))
        assertFalse(MediaNames.isMotion("picture-1.mp4"))
        assertNull(MediaNames.stillOf("video-2.mp4"))
    }
}
