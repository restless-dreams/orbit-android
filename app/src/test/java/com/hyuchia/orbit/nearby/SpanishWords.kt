package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.Words
import java.util.Locale

/** The app's Spanish strings, read from its resource files, for tests: what a phone in Mexico shows. */
object SpanishWords : Words {
    private val words = FileWords(Locale.forLanguageTag("es-MX"))

    override val language: String = "es-MX"

    override fun string(id: Int, vararg arguments: Any): String = words.string(id, *arguments)

    override fun plural(id: Int, count: Int, vararg arguments: Any): String = words.plural(id, count, *arguments)
}
