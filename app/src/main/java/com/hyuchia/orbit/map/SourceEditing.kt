package com.hyuchia.orbit.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long typing pauses before the map is drawn from it and saved. */
private const val TYPING_PAUSE_MILLISECONDS = 280L

/**
 * Whether a map's text can be edited by hand, the map being `readOnly` for
 * that reason or not at all. A map Orbit couldn't read all of can be: editing
 * its text is how it gets fixed. One from a newer Orbit, in the trash or kept
 * by a newer Orbit is only shown, its text too.
 */
fun canEditText(readOnly: ReadOnly?): Boolean = readOnly == null || readOnly == ReadOnly.Unreadable

/** Text typed in the editor, markdown or `yaml`, and the change of the map it was typed on ([SourceEditing.follows] then). */
private class Typed(val text: String, val yaml: Boolean, val follow: Int)

/**
 * The map edited as text, while the source editor is open: what's typed is
 * drawn a moment after the typing stops, and saved once Orbit reads it; the
 * text switches between markdown and YAML; and it follows the map when the
 * map changes outside it (an edit on the map, an undo, the file changed
 * elsewhere), so the next keystroke edits the map as it is. Nothing typed
 * is lost to a conversion: one that lands after something was typed starts
 * over from what was typed. And no conversion lands over what came after
 * it: text typed before the map changed outside the editor isn't saved over
 * that change, an older text never over a newer one, and an undo or redo
 * waits for the typing before it to be saved. The editor lives in `ui`.
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

    /** The text typed and not saved yet, while there's some: saved once the typing stops, or at once by [flush]. */
    private var typed: Typed? = null

    /** Saves typed text one at a time, so a slow conversion's lands before the next one's, never over it. */
    private val saving = Mutex()

    /** The last change waiting for the typing before it to be saved ([afterTyping]): one asked for after it waits for it in turn. */
    private var waiting: Job? = null

    /** Goes up whenever the text changes but for a switch between markdown and YAML: typed, or following the map. */
    private var changes = 0

    /** Goes up with each change of the map the text follows, so only the newest one lands. */
    private var follows = 0

    /**
     * The markdown as it was when the text switched to YAML, and [changes]
     * then: switching back with nothing typed and the map as it was gives
     * that markdown back as it was written, rather than Orbit's own form.
     */
    private var beforeYaml: Pair<String, Int>? = null

    private val source: SourceState? get() = ui.value.source

    private fun update(change: (SourceState) -> SourceState) {
        ui.update { state -> state.source?.let { state.copy(source = change(it)) } ?: state }
    }

    /** Text typed in the editor: drawn a moment after the typing stops, and saved when Orbit reads it. */
    fun edit(text: String) {
        val source = source ?: return
        changes += 1
        update { it.copy(text = text) }
        typed = Typed(text, source.yaml, follows)
        typing?.cancel()

        typing = scope.launch {
            delay(TYPING_PAUSE_MILLISECONDS)
            draw(text)
            save()
        }
    }

    /**
     * Saves the text typed and not saved yet, if there's some. Typed markdown
     * is saved as typed; YAML only once Orbit reads all of it, since its
     * conversion would lose the rest. Text typed before the map changed
     * outside the editor isn't saved over that change, however long its
     * conversion took.
     */
    private suspend fun save() = saving.withLock {
        val typed = typed ?: return@withLock
        val markdown = if (typed.yaml) convert(typed.text, "markdown") else typed.text

        // Saved now, or never to be: text typed meanwhile is saved after it.
        if (this.typed === typed) {
            this.typed = null
        }

        if (typed.follow == follows && markdown != null && markdown.isNotBlank() && canEditText(ui.value.readOnly)) {
            keep(markdown)
        }
    }

    /**
     * Saves what was typed and isn't saved yet now, rather than once the
     * typing stops, and returns when it's saved, or when it can't be: what's
     * done before the map closes, even after the screen's own work stopped,
     * while its page is still there to convert YAML.
     */
    suspend fun flush() {
        typing?.cancel()
        save()
    }

    /** Switches the text to YAML or to markdown; text typed while it converts is converted too. */
    fun setYaml(yaml: Boolean) {
        val source = source ?: return

        if (source.yaml == yaml) {
            return
        }

        // Back to markdown, nothing changed since: the markdown as it was, its own shapes, comments and spacing included.
        val unchanged = beforeYaml?.takeIf { (_, seen) -> !yaml && seen == changes }?.first
        beforeYaml = if (yaml) source.text to changes else null

        if (unchanged != null) {
            update { it.copy(text = unchanged, yaml = false, converting = false) }
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
     * saved isn't saved over it, even typing that's converting as the editor
     * closes. YAML is converted from it first, and is left as it is if
     * something is typed meanwhile.
     */
    fun follow(markdown: String) {
        typing?.cancel()
        typed = null
        follows += 1
        // The markdown before YAML isn't the map's any more.
        beforeYaml = null
        val source = source ?: return
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
     * taken back by it. With nothing typed to save, and no change waiting
     * for typing before it, it runs at once.
     */
    fun afterTyping(change: () -> Unit) {
        val before = waiting?.takeIf { it.isActive }

        if (typed == null && before == null) {
            change()
            return
        }

        waiting = scope.launch {
            before?.join()
            flush()
            change()
        }
    }

    /** Closes the editor: text typed a moment ago is saved first, then `closed` runs. */
    fun close(closed: () -> Unit) {
        ui.update { it.copy(source = null) }
        afterTyping(closed)
    }
}
