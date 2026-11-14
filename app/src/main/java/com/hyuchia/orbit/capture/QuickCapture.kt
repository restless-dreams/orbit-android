package com.hyuchia.orbit.capture

import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.WriteResult
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.PagePool
import com.hyuchia.orbit.page.mapBaseUrl
import kotlinx.serialization.builtins.serializer
import java.io.IOException

/** A list item's marker, a checkbox's, or a number's, before an idea on its line. */
private val ItemMarker = Regex("""^\s*(?:[-*+•]\s+(?:\[[ xX/]]\s+)?|\d+[.)]\s+)""")

/** How many times quick capture starts over when the map changes while ideas are added to it. */
private const val ATTEMPTS = 3

/**
 * The ideas in text caught on the go, for counting them: one for each line,
 * without the list markers a copied list brings along. Blank lines are none.
 * The page adds them nested, as pasting nests them (see [QuickCapture.add]).
 */
fun ideasFromText(text: String): List<String> =
    text.lines()
        .map { line -> line.replace(ItemMarker, "").replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotEmpty() }

/**
 * The map's text with the caught ideas in it, as the page gave it back for
 * `markdown` (see [MapCommand.AddIdeas]): the very same text when Orbit read
 * no idea in what was caught, which adds none and throws, for the sheet to
 * say so ([CaptureFailure.NoIdeas]) rather than say ideas were added.
 */
internal fun withCaughtIdeas(markdown: String, changed: String): String {
    if (changed == markdown) {
        throw CaptureException(CaptureFailure.NoIdeas)
    }

    return changed
}

/** Why ideas didn't join a map, for the sheet that caught them to say. */
enum class CaptureFailure {
    /** The map can only be read here, or its file couldn't be. */
    Refused,

    /** The map isn't here anymore. */
    Missing,

    /** The map changed on every try. */
    KeptChanging,

    /** The map's file couldn't be written, as when the device's storage is full. */
    NotWritten,

    /** Orbit read no idea in the text, as in frontmatter alone: nothing was added. */
    NoIdeas,
}

/** Ideas that didn't join a map, and why. */
class CaptureException(val failure: CaptureFailure) : IOException("Ideas weren't added: $failure")

/**
 * Adds ideas to a map without opening it: from the Quick Settings tile, a
 * home screen widget, or text shared from another app. Orbit adds them on
 * the spare page, so the map's text is written the way Orbit writes it, and
 * a screen showing the map catches up.
 */
class QuickCapture(private val pool: PagePool, private val maps: MapRepository, private val settings: SettingsRepository) {
    /**
     * Adds the ideas in `text` at the top of map `mapId`, a line each,
     * nested as their lines are indented, as pasting nests them, and
     * remembers it as the map ideas go to next. When the map changes on the
     * way (an edit on its screen, a sync), the ideas are added to it as it is
     * now. Throws when they couldn't be added, the map left as it was: a
     * [CaptureException] saying why when the map didn't take them.
     */
    suspend fun add(mapId: String, text: String) {
        if (ideasFromText(text).isEmpty()) {
            return
        }

        repeat(ATTEMPTS) {
            val markdown = maps.read(mapId)

            val changed = withCaughtIdeas(
                markdown,
                pool.work { page ->
                    // Drawn out of sight to add them, the map's pictures come from its own folder.
                    page.request(String.serializer()) { job -> MapCommand.AddIdeas(job, markdown, text, baseUrl = mapBaseUrl(mapId)) }
                },
            )

            when (maps.change(mapId, changed, base = markdown)) {
                WriteResult.Written -> {
                    settings.update { it.copy(captureMapId = mapId) }
                    return
                }
                WriteResult.Changed -> Unit
                WriteResult.Refused -> throw CaptureException(CaptureFailure.Refused)
                WriteResult.Missing -> throw CaptureException(CaptureFailure.Missing)
                WriteResult.NotWritten -> throw CaptureException(CaptureFailure.NotWritten)
            }
        }

        throw CaptureException(CaptureFailure.KeptChanging)
    }
}
