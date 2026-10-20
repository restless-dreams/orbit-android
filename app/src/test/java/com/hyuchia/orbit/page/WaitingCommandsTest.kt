package com.hyuchia.orbit.page

import com.hyuchia.orbit.page.WaitingCommands.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitingCommandsTest {
    @Test
    fun `commands wait in the order they came, and go once`() {
        val waiting = WaitingCommands()
        waiting.add("words", Kind.Words)
        waiting.add("load trip", Kind.NewMap)
        waiting.add("theme", Kind.Map)
        waiting.add("snapshot 1", Kind.Job, job = 1)

        assertEquals(listOf("words", "load trip", "theme", "snapshot 1"), waiting.take())
        assertEquals(emptyList<String>(), waiting.take())
    }

    @Test
    fun `another map makes what waited about the one before moot, but not the words or jobs`() {
        val waiting = WaitingCommands()
        waiting.add("words", Kind.Words)
        waiting.add("load trip", Kind.NewMap)
        waiting.add("source of trip", Kind.Map)
        waiting.add("copy 1", Kind.Job, job = 1)
        waiting.add("unload", Kind.NewMap)
        waiting.add("load plans", Kind.NewMap)
        waiting.add("insets", Kind.Map)

        assertEquals(listOf("words", "copy 1", "load plans", "insets"), waiting.take())
    }

    @Test
    fun `the latest words wait, where the first did`() {
        val waiting = WaitingCommands()
        waiting.add("english", Kind.Words)
        waiting.add("load trip", Kind.NewMap)
        waiting.add("spanish", Kind.Words)

        assertEquals(listOf("spanish", "load trip"), waiting.take())
    }

    @Test
    fun `a job's command leaves with its job`() {
        val waiting = WaitingCommands()
        waiting.add("snapshot 1", Kind.Job, job = 1)
        waiting.add("convert 2", Kind.Job, job = 2)
        waiting.forget(1)

        assertEquals(listOf("convert 2"), waiting.take())
    }

    @Test
    fun `a page that never says hello holds one map's commands at most`() {
        val waiting = WaitingCommands()

        repeat(1_000) { visit ->
            waiting.add("words", Kind.Words)
            waiting.add("load map $visit", Kind.NewMap)
            waiting.add("insets", Kind.Map)
            waiting.add("snapshot $visit", Kind.Job, job = visit)
            waiting.forget(visit)
            waiting.add("unload", Kind.NewMap)
        }

        assertEquals(listOf("words", "unload"), waiting.take())
    }
}
