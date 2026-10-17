package com.hyuchia.orbit.capture

import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.WriteResult
import com.hyuchia.orbit.page.CapturedIdea
import com.hyuchia.orbit.page.MapCommand
import com.hyuchia.orbit.page.PagePool
import kotlinx.serialization.builtins.serializer
import java.io.IOException

/** A list item's marker, a checkbox's, or a number's, before an idea on its line. */
private val ItemMarker = Regex("""^\s*(?:[-*+•]\s+(?:\[[ xX/]]\s+)?|\d+[.)]\s+)""")

/** The longest an idea's name gets: a quick capture is a name, and a longer line is kept whole as its note. */
private const val IDEA_LENGTH = 200

/** How many times quick capture starts over when the map changes while ideas are added to it. */
private const val ATTEMPTS = 3

/**
 * The ideas in text caught on the go: one for each line, without the list
 * markers a copied list brings along. Blank lines are none.
 */
fun ideasFromText(text: String): List<String> =
    text.lines()
        .map { line -> line.replace(ItemMarker, "").replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotEmpty() }

/** Ideas as they join a map: a long one named by its start, the whole of it kept as its note. */
fun capturedIdeas(ideas: List<String>): List<CapturedIdea> =
    ideas.map { idea -> if (idea.length <= IDEA_LENGTH) CapturedIdea(idea) else CapturedIdea(MapText.shorten(idea, IDEA_LENGTH), note = idea) }

/** Why ideas didn't join a map, for the sheet that caught them to say. */
enum class CaptureFailure {
    /** The map can only be read here, or its file couldn't be. */
    Refused,

    /** The map isn't here anymore. */
    Missing,

    /** The map changed on every try. */
    KeptChanging,
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
     * Adds `ideas` at the top of map `mapId`, and remembers it as the map
     * ideas go to next. When the map changes on the way (an edit on its
     * screen, a sync), the ideas are added to it as it is now. Throws when
     * they couldn't be added, the map left as it was: a [CaptureException]
     * saying why when the map didn't take them.
     */
    suspend fun add(mapId: String, ideas: List<String>) {
        if (ideas.isEmpty()) {
            return
        }

        repeat(ATTEMPTS) {
            val markdown = maps.read(mapId)

            val changed = pool.work { page ->
                page.request(String.serializer()) { job -> MapCommand.AddIdeas(job, markdown, capturedIdeas(ideas)) }
            }

            when (maps.change(mapId, changed, base = markdown)) {
                WriteResult.Written -> {
                    settings.update { it.copy(captureMapId = mapId) }
                    return
                }
                WriteResult.Changed -> Unit
                WriteResult.Refused -> throw CaptureException(CaptureFailure.Refused)
                WriteResult.Missing -> throw CaptureException(CaptureFailure.Missing)
            }
        }

        throw CaptureException(CaptureFailure.KeptChanging)
    }
}
