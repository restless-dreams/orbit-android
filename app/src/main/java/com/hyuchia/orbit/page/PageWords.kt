package com.hyuchia.orbit.page

import android.content.Context
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.R
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * What the pages say themselves, in the app's language: the words Orbit's
 * own languages don't have, such as the kinds of ideas, a goal ring's words,
 * the map page's messages and the presentation pages' (web/words.ts). The
 * map's page and a second screen hear them as a `language` command, a TV
 * when a presentation starts on it, and the pages the phone serves or
 * exports carry them. Pages speak English until they get them. A web page's
 * buttons, from [present] on, are written into its HTML ([WebPage]).
 */
@Serializable
data class PageWords(
    /** The language they're in, as a BCP 47 tag ("es-MX"): the page's `lang`, which Orbit's own words follow. */
    val language: String,

    /** The kinds of ideas, by the type a map writes: `milestone` is "Milestone". */
    val kinds: Map<String, String>,

    /** Under a goal's number, its target, where `{target}` is: "of {target}". */
    val goalOfTarget: String,

    val goalLeft: String,
    val untitledMap: String,
    val noIdeas: String,
    val textNotRead: String,
    val mapNotAddedTo: String,
    val mapFromNewerOrbit: String,
    val waiting: String,
    val looking: String,
    val removed: String,
    val full: String,
    val closed: String,
    val ended: String,
    val tapForSound: String,
    val present: String,
    val presentKey: String,
    val presentation: String,
    val previousIdea: String,
    val nextIdea: String,
    val stopPresentingKey: String,
    val downloadForOrbit: String,
    val drawnWithOrbit: String,
) {
    companion object {
        /** The pages' words in `language`, from the app's strings: `string` reads one as the app's resources have it. */
        fun from(language: String, string: (Int) -> String): PageWords = PageWords(
            language = language,
            kinds = KINDS.mapValues { (_, id) -> string(id) },
            // The page puts the target where `{target}` is.
            goalOfTarget = String.format(Locale.ROOT, string(R.string.pages_goal_of_target), TARGET),
            goalLeft = string(R.string.pages_goal_left),
            untitledMap = string(R.string.common_untitled_map),
            noIdeas = string(R.string.pages_no_ideas),
            textNotRead = string(R.string.pages_text_not_read),
            mapNotAddedTo = string(R.string.pages_map_not_added_to),
            mapFromNewerOrbit = string(R.string.pages_map_from_a_newer_orbit),
            waiting = string(R.string.pages_waiting_for_the_presenter),
            looking = string(R.string.pages_looking_for_the_presenter),
            // A browser turned away says why in the words the join page uses for the same reasons.
            removed = string(R.string.nearby_presenter_removed_you),
            full = string(R.string.nearby_presentation_is_full),
            closed = string(R.string.nearby_presenter_is_not_letting_anyone_new_in),
            ended = string(R.string.nearby_presentation_ended),
            tapForSound = string(R.string.pages_tap_for_sound),
            present = string(R.string.common_present),
            presentKey = string(R.string.pages_present_key),
            presentation = string(R.string.pages_presentation),
            previousIdea = string(R.string.pages_previous_idea),
            nextIdea = string(R.string.pages_next_idea),
            stopPresentingKey = string(R.string.pages_stop_presenting_key),
            downloadForOrbit = string(R.string.pages_download_for_orbit),
            drawnWithOrbit = string(R.string.pages_drawn_with_orbit),
        )

        /** Where the page puts a goal's target in [goalOfTarget]. */
        private const val TARGET = "{target}"

        /** The kinds of ideas the app offers, by the type a map writes, and their names: the map screen's. */
        private val KINDS = mapOf(
            "milestone" to R.string.map_kind_milestone,
            "question" to R.string.map_kind_question,
            "decision" to R.string.map_kind_decision,
            "risk" to R.string.map_kind_risk,
            "source" to R.string.map_kind_source,
            "person" to R.string.map_kind_person,
            "progress" to R.string.map_kind_progress,
            "goal" to R.string.map_kind_goal,
        )
    }
}

/** The pages' words in the language of `context`: the app's, or the screen's a page is in. */
fun pageWords(context: Context): PageWords = PageWords.from(pageLanguage(context)) { context.getString(it) }

/** The language the pages of `context` speak (see the other [pageLanguage]). */
fun pageLanguage(context: Context): String {
    val locales = context.resources.configuration.locales
    return pageLanguage((0 until locales.size()).map { locales[it] }, context.getString(R.string.pages_language))
}

/**
 * The language pages speak, as a BCP 47 tag: `written`, the language the
 * app's strings are in, with the region of the first of `locales` in it,
 * for its numbers ("es-MX"). A phone in a language the app doesn't speak
 * reads the app's English, and so do its pages, their screen readers too.
 */
fun pageLanguage(locales: List<Locale>, written: String): String =
    locales.firstOrNull { it.language == written }?.toLanguageTag() ?: written

/** What tells a page the app's language and its words: web/bridge.ts's and web/audience.ts's `language` command. */
@Serializable
internal data class LanguageCommand(val words: PageWords, val type: String = "language")

/** What the pages say themselves, in these words' language (see [PageWords]). */
fun Words.pageWords(): PageWords = PageWords.from(language) { id -> string(id) }
