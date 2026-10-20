package com.hyuchia.orbit.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long typing pauses before the map is drawn from it and saved. */
private const val TYPING_PAUSE_MILLISECONDS = 280L

/**
 * Whether a map's text can be edited by hand, the map being `readOnly` for
 * that reason or not at all. A map Orbit couldn't read all of can be: editing
 * its text is how it gets fixed. One from a newer Orbit, in the trash or kept
 * by a newer Orbit is only shown, its text too.
 */
fun canEditText(readOnly: ReadOnly?): Boolean = readOnly == null || readOnly == ReadOnly.Unreadable

/**
 * The map edited as text, while the source editor is open: what's typed is
 * drawn a moment after the typing stops, and saved once Orbit reads it; the
 * text switches between markdown and YAML; and it follows the map when the
 * map changes outside it (an edit on the map, an undo, the file changed
 * elsewhere), so the next keystroke edits the map as it is. Nothing typed
 * is lost to a conversion: one that lands after something was typed starts
 * over from what was typed. The editor lives in `ui`.
 */
class SourceEditing(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<MapUi>,

    /** Draws typed text on the map; Orbit says what it made of it. */
    private val draw: (String) -> Unit,

    /** The text converted to `markdown` or `yaml`, or null when it can't be. */
    private val convert: suspend (text: String, to: String) -> String?,

    /** Makes markdown typed in the editor the map's text, and saves it. */
    private val keep: (String) -> Unit,
) {
    /** Draws and saves the text a moment after the typing stops. */
    private var typing: Job? = null

    /** Goes up whenever the text changes but for a switch between markdown and YAML: typed, or following the map. */
    private var changes = 0

    /** Goes up with each change of the map the text follows, so only the newest one lands. */
    private var follows = 0

    private val source: SourceState? get() = ui.value.source

    private fun update(change: (SourceState) -> SourceState) {
        ui.update { state -> state.source?.let { state.copy(source = change(it)) } ?: state }
    }

    /** Text typed in the editor: drawn a moment after the typing stops, and saved when Orbit reads it. */
    fun edit(text: String) {
        val source = source ?: return
        changes += 1
        update { it.copy(text = text) }
        typing?.cancel()

        typing = scope.launch {
            delay(TYPING_PAUSE_MILLISECONDS)
            draw(text)
            save(text, source.yaml)
        }
    }

    /** Typed markdown is saved as typed. YAML only once Orbit reads all of it: its conversion would lose the rest. */
    private suspend fun save(text: String, yaml: Boolean) {
        val markdown = if (yaml) convert(text, "markdown") else text

        if (markdown != null && markdown.isNotBlank() && canEditText(ui.value.readOnly)) {
            keep(markdown)
        }
    }

    /** Switches the text to YAML or to markdown; text typed while it converts is converted too. */
    fun setYaml(yaml: Boolean) {
        val source = source ?: return

        if (source.yaml == yaml) {
            return
        }

        update { it.copy(converting = true) }

        scope.launch {
            var converted: String?

            do {
                val seen = changes
                val text = this@SourceEditing.source?.text ?: return@launch
                converted = convert(text, if (yaml) "yaml" else "markdown")
            } while (converted != null && seen != changes)

            update { it.copy(text = converted ?: it.text, yaml = if (converted != null) yaml else it.yaml, converting = false) }
        }
    }

    /**
     * Shows the map's text after it changed outside the editor: an edit on
     * the map, an undo or redo, or the file changed elsewhere. Typing not yet
     * drawn isn't saved over it. YAML is converted from it first, and is left
     * as it is if something is typed meanwhile.
     */
    fun follow(markdown: String) {
        val source = source ?: return
        typing?.cancel()
        follows += 1
        val follow = follows

        if (!source.yaml) {
            changes += 1
            update { it.copy(text = markdown) }
            return
        }

        val seen = changes

        scope.launch {
            val yaml = convert(markdown, "yaml")

            if (follow != follows || seen != changes) {
                return@launch
            }

            changes += 1

            update { editor ->
                if (editor.yaml && yaml != null) {
                    editor.copy(text = yaml)
                } else {
                    // A switch to markdown landed meanwhile, or the map can't be YAML now: it shows as markdown.
                    editor.copy(text = markdown, yaml = false)
                }
            }
        }
    }

    /**
     * Runs `change` once text typed a moment ago is saved, so an undo or redo
     * comes after the typing, which it can bring back, rather than being
     * taken back by it.
     */
    fun afterTyping(change: () -> Unit) {
        val typed = source?.takeIf { typing?.isActive == true }

        if (typed == null) {
            change()
            return
        }

        typing?.cancel()

        scope.launch {
            save(typed.text, typed.yaml)
            change()
        }
    }

    /** Closes the editor: text typed a moment ago is saved first, then `closed` runs. */
    fun close(closed: () -> Unit) {
        val typed = source?.takeIf { typing?.isActive == true }
        typing?.cancel()
        ui.update { it.copy(source = null) }

        scope.launch {
            typed?.let { save(it.text, it.yaml) }
            closed()
        }
    }
}
