package com.hyuchia.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ideas' dates as the light reading of a map's text finds them, for reminders: read without a page, as Orbit reads them. */
class MapTextDatesTest {
    private fun read(text: String): List<String> = MapText.dates(text).map { "${it.name} ${it.path} ${it.date.text} ${it.status}" }

    @Test
    fun `dates in a markdown map, on a line, in a block, and as programs write them`() {
        val text = """
            ---
            title: Trip
            ---
            # 🧳 Trip <!-- orbit due: 2026-12-01 -->
            ## Flights <!-- orbit due: 2026-11-20 -->
            ## Hotel <!-- orbit due: "2026-11-20 09:30", status: active -->
            ## Museum
            <!-- orbit
            status: todo
            due: 2026-11-21T14:05
            -->
            - [ ] Tickets <!-- orbit due: 2026-2-3 7:00 -->
            - [x] Passport <!-- orbit due: 2026-11-19 -->
            - Someday <!-- orbit due: next week -->
            - February 30th <!-- orbit due: 2026-02-30 -->
              - Deeper <!-- orbit due: 2026-11-22 -->
        """.trimIndent()

        assertEquals(
            listOf(
                "🧳 Trip [] 2026-12-01 null",
                "Flights [0] 2026-11-20 null",
                "Hotel [1] 2026-11-20 09:30 active",
                "Museum [2] 2026-11-21 14:05 todo",
                "Tickets [2, 0] 2026-02-03 07:00 todo",
                "Passport [2, 1] 2026-11-19 done",
                // A `due` Orbit can't read isn't a date, and the ideas around it keep their places.
                "Deeper [2, 3, 0] 2026-11-22 null",
            ),
            read(text),
        )
    }

    @Test
    fun `dates in a YAML map`() {
        val text = """
            title: Trip
            nodes:
              - text: Trip
                children:
                  - text: Flights
                    due: 2026-11-20 09:30
                  - Packing
                  - text: Hotel
                    status: done
                    due: "2026-11-21"
        """.trimIndent()

        assertEquals(listOf("Flights [0] 2026-11-20 09:30 null", "Hotel [2] 2026-11-21 done"), read(text))
    }

    @Test
    fun `a map without dates has none, and code or quotes hold none`() {
        assertEquals(emptyList<String>(), read("# Plan\n## Research\n"))
        assertEquals(emptyList<String>(), read("# Plan\n## Notes\n```\n- Fake <!-- orbit due: 2026-11-20 -->\n```\n> - Quoted <!-- orbit due: 2026-11-20 -->\n"))
    }
}
