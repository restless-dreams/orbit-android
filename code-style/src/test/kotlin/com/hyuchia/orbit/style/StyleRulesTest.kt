package com.hyuchia.orbit.style

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class StyleRulesTest {
    private val engine = KtLintRuleEngine(
        ruleProviders = setOf(
            RuleProvider { BlankLinesAroundBlocksRule() },
            RuleProvider { BlankLinesAroundDocumentedMembersRule() },
        ),
    )

    private fun format(source: String): String =
        engine.format(Code.fromSnippet(source), rerunAfterAutocorrect = true, defaultAutocorrect = true) {
            AutocorrectDecision.ALLOW_AUTOCORRECT
        }

    private fun problems(source: String): List<String> {
        val found = mutableListOf<String>()
        engine.lint(Code.fromSnippet(source)) { found.add("${it.line}: ${it.detail}") }
        return found
    }

    @Test
    fun `ifs, loops and tries get a blank line before and after them`() {
        val source = """
            fun tick(now: Long) {
                var changed = paintRequested
                for (task in tasks.toList()) {
                    val result = try {
                        task(now)
                    } catch (error: IllegalStateException) {
                        false
                    }
                    if (!result) {
                        tasks.remove(task)
                    }
                    changed = changed || result
                }
                paintRequested = false
            }
        """.trimIndent()

        val expected = """
            fun tick(now: Long) {
                var changed = paintRequested

                for (task in tasks.toList()) {
                    val result = try {
                        task(now)
                    } catch (error: IllegalStateException) {
                        false
                    }

                    if (!result) {
                        tasks.remove(task)
                    }

                    changed = changed || result
                }

                paintRequested = false
            }
        """.trimIndent()

        assertEquals(expected, format(source))
    }

    @Test
    fun `a composable with content is a block, and a call on several lines is not`() {
        val source = """
            @Composable
            fun Header(title: String) {
                Text(title)
                Row {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text(title)
                }
                Spacer(
                    modifier = Modifier.height(8.dp),
                )
                Text(title)
            }
        """.trimIndent()

        val expected = """
            @Composable
            fun Header(title: String) {
                Text(title)

                Row {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text(title)
                }

                Spacer(
                    modifier = Modifier.height(8.dp),
                )
                Text(title)
            }
        """.trimIndent()

        assertEquals(expected, format(source))
    }

    @Test
    fun `a comment stays with the block it's about`() {
        val source = """
            fun run() {
                start()
                // Every item, in order.
                for (item in items) {
                    use(item)
                }
            }
        """.trimIndent()

        val expected = """
            fun run() {
                start()

                // Every item, in order.
                for (item in items) {
                    use(item)
                }
            }
        """.trimIndent()

        assertEquals(expected, format(source))
    }

    @Test
    fun `blocks that start or end a block, and one-line guards, need no blank lines`() {
        val source = """
            fun run(ready: Boolean) {
                if (!ready) return
                start()
                if (late) {
                    hurry()
                }
            }

            fun stop() {
                when (state) {
                    State.Running -> halt()
                    State.Stopped -> Unit
                }
            }
        """.trimIndent()

        assertEquals(listOf("4: Add a blank line before this block"), problems(source))
    }

    @Test
    fun `members with doc comments get a blank line before and after them`() {
        val source = """
            data class SceneOptions(
                val viewport: Viewport,
                /** The parts of the viewport the page's own controls cover. */
                val insets: Insets?,
                val colors: Map<String, String>,
                val maxDepth: Int?,
                /** Keep a place in the orbit for a new idea. */
                val addSlot: Int?,
            )

            class Clock {
                private val tasks = mutableSetOf<Task>()
                /** Whether a frame is waiting to be painted. */
                var paintRequested = false
                private var frame: Long? = null
            }
        """.trimIndent()

        val expected = """
            data class SceneOptions(
                val viewport: Viewport,

                /** The parts of the viewport the page's own controls cover. */
                val insets: Insets?,

                val colors: Map<String, String>,
                val maxDepth: Int?,

                /** Keep a place in the orbit for a new idea. */
                val addSlot: Int?,
            )

            class Clock {
                private val tasks = mutableSetOf<Task>()

                /** Whether a frame is waiting to be painted. */
                var paintRequested = false

                private var frame: Long? = null
            }
        """.trimIndent()

        assertEquals(expected, format(source))
    }
}
