package com.hyuchia.orbit.data

import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.EditedMap

/**
 * Orbit's own editor as the folder's tests have it, where the app has the
 * map's page: a map for a newer format is refused, as the page refuses it,
 * orbit-md is renamed with [MapText.retitle], and a YAML map's center takes
 * the new name in its first `text`, every other line as it was.
 */
internal class TestEditor(private val words: Words) : MapTextEditor {
    override suspend fun edit(markdown: String, title: String?, tags: List<String>?): EditedMap =
        when {
            MapText.formatVersion(markdown) > MAP_FORMAT -> EditedMap(refused = "newer")
            title == null -> EditedMap(markdown = markdown)
            YAML_NODES.containsMatchIn(markdown) -> {
                val center = YAML_CENTER.find(markdown)
                EditedMap(markdown = center?.let { markdown.replaceRange(it.range, "${it.groupValues[1]}$title") } ?: markdown)
            }
            else -> EditedMap(markdown = MapText.retitle(markdown, title, words))
        }

    private companion object {
        val YAML_NODES = Regex("""^nodes\s*:""", RegexOption.MULTILINE)
        val YAML_CENTER = Regex("""^(\s*- text: ).*$""", RegexOption.MULTILINE)
    }
}
