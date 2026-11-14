package com.hyuchia.orbit.map

import com.hyuchia.orbit.page.MapNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The kind an idea is, as the editor picks it and its details say it: as the map's page draws it, which reads a
 * type as Orbit does, whatever its spaces and capitals, and draws one written for a newer version of its kind as no
 * kind at all.
 */
class NodeKindsTest {
    private fun idea(type: String?, version: Int? = null) = MapNode("trip", "Trip", type = type, typeVersion = version)

    private fun kind(type: String) = NodeKinds.single { it.type == type }

    @Test
    fun `a type is read whatever its spaces and capitals`() {
        assertEquals(kind("milestone"), kindOf(idea("milestone")))
        assertEquals(kind("milestone"), kindOf(idea(" Milestone ")))
        assertEquals(kind("goal"), kindOf(idea("GOAL\t")))
    }

    @Test
    fun `an idea with no type, or a blank one, is an idea`() {
        assertEquals(NodeKinds.first(), kindOf(idea(null)))
        assertEquals(NodeKinds.first(), kindOf(idea("  ")))
        assertNull(NodeKinds.first().type)
    }

    @Test
    fun `a kind the app doesn't know, or written for a newer version of one, is no kind it shows`() {
        assertNull(kindOf(idea("task")))
        assertNull(kindOf(idea("milestone", version = 2)))
        assertNull(kindOf(idea(" goal ", version = 2)))
        assertEquals(kind("milestone"), kindOf(idea("milestone", version = 1)))
    }

    @Test
    fun `an idea is a goal to the editor and the details exactly when the app steps it as one`() {
        val goals = listOf(idea("goal"), idea(" Goal "), idea("goal", version = 1), idea("goal", version = 2), idea("GOAL", version = 99), idea("goals"))

        goals.forEach { node -> assertEquals(node.toString(), node.isGoal, kindOf(node) == kind("goal")) }
    }

    /** `node` with `changes` made, as the page makes them: a change to null takes the attribute off. */
    private fun MapNode.with(changes: Map<String, JsonElement?>): MapNode = copy(
        type = if ("type" in changes) changes["type"]?.jsonPrimitive?.content else type,
        typeVersion = if ("typeVersion" in changes) changes["typeVersion"]?.jsonPrimitive?.int else typeVersion,
    )

    @Test
    fun `another kind picked is the kind the idea becomes, whatever version of its old kind it was written for`() {
        val ideas = listOf(idea("task", version = 3), idea("goal", version = 2), idea(" Milestone ", version = 2), idea("progress", version = 2), idea("goal", version = 1), idea(null), idea("question"))

        for (node in ideas) {
            for (kind in NodeKinds.filter { it != kindNamed(node) }) {
                assertEquals("$node to ${kind.type}", kind, kindOf(node.with(kindChanges(node, kind))))
            }
        }
    }

    @Test
    fun `the kind an idea's type names already, picked again, leaves it as written, a newer version's fields with it`() {
        val ideas = listOf(idea("goal", version = 2), idea(" Milestone ", version = 2), idea("PROGRESS", version = 2), idea("goal", version = 1), idea("decision"), idea(null), idea("  "))

        for (node in ideas) {
            val named = kindNamed(node)!!

            assertEquals("$node", emptyMap<String, JsonElement?>(), kindChanges(node, named))
        }

        // The editor shows it as chosen, though the page draws a newer version's idea as no kind.
        assertEquals(kind("goal"), kindNamed(idea("goal", version = 2)))
        assertNull(kindOf(idea("goal", version = 2)))
        assertNull(kindNamed(idea("task", version = 3)))
    }

    @Test
    fun `a goal that becomes another kind takes its numbers with it, and an old type's version goes`() {
        assertEquals(mapOf("type" to JsonPrimitive("milestone"), "typeVersion" to null), kindChanges(idea("task", version = 3), kind("milestone")))
        assertEquals(mapOf("type" to JsonPrimitive("milestone"), "typeVersion" to null), kindChanges(idea("goal", version = 2), kind("milestone")))
        assertEquals(
            mapOf("type" to JsonPrimitive("risk"), "typeVersion" to null, "attrs" to JsonObject(Goal.removed)),
            kindChanges(idea("goal", version = 1), kind("risk")),
        )
        assertEquals(mapOf("type" to null, "attrs" to JsonObject(Goal.removed)), kindChanges(idea("goal"), NodeKinds.first()))
        assertEquals(mapOf("type" to JsonPrimitive("goal")), kindChanges(idea("question"), kind("goal")))
    }
}
