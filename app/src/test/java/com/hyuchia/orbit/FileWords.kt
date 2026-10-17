package com.hyuchia.orbit

import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app's strings as a phone in `locale`'s language shows them, read from
 * the resource files, for tests, which have no resources: the language's
 * own over the English ones, as Android falls back. Plurals are picked by
 * English's and Spanish's rules.
 */
class FileWords(private val locale: Locale) : Words {
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        for (folder in listOf("values", "values-${locale.language}")) {
            val read = FOLDERS.getOrPut(folder) { readFolder(File(RESOURCES, folder)) }
            strings += read.strings
            plurals += read.plurals
        }
    }

    override val language: String get() = string(R.string.pages_language)

    override fun string(id: Int, vararg arguments: Any): String {
        val text = strings.getValue(nameOf(R.string::class.java, id))

        return if (arguments.isEmpty()) {
            text
        } else {
            String.format(locale, text, *arguments)
        }
    }

    override fun plural(id: Int, count: Int, vararg arguments: Any): String {
        val forms = plurals.getValue(nameOf(R.plurals::class.java, id))
        return String.format(locale, forms[quantity(count)] ?: forms.getValue("other"), *arguments)
    }

    /** The plural a count takes: `one` for 1, `many` for whole millions in Spanish, `other` for the rest. */
    private fun quantity(count: Int): String = when {
        count == 1 -> "one"
        locale.language == "es" && count != 0 && count % 1_000_000 == 0 -> "many"
        else -> "other"
    }

    /** A folder's strings and plurals, by name. */
    private class Folder(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private companion object {
        /** The app's resources, from the module's folder, where Gradle runs its tests. */
        val RESOURCES = File("src/main/res")

        /** Each folder as it was read, once for all the tests. */
        val FOLDERS = ConcurrentHashMap<String, Folder>()

        fun readFolder(folder: File): Folder {
            val strings = mutableMapOf<String, String>()
            val plurals = mutableMapOf<String, Map<String, String>>()
            val files = folder.listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }.orEmpty()

            for (file in files) {
                val entries = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes

                for (index in 0 until entries.length) {
                    val entry = entries.item(index) as? Element ?: continue
                    val name = entry.getAttribute("name")

                    when (entry.tagName) {
                        "string" -> strings[name] = unescape(entry.textContent)
                        "plurals" -> {
                            val items = entry.getElementsByTagName("item")
                            plurals[name] = (0 until items.length).map { items.item(it) as Element }.associate { it.getAttribute("quantity") to unescape(it.textContent) }
                        }
                    }
                }
            }

            return Folder(strings, plurals)
        }

        /** A resource's name, from its id in the app's R class. */
        fun nameOf(type: Class<*>, id: Int): String = type.fields.first { it.getInt(null) == id }.name

        /** Text as Android reads it from a resource: escapes undone, quotes dropped, and spaces outside them run together. */
        fun unescape(raw: String): String {
            val text = StringBuilder()
            var quoted = false
            var space = false

            fun append(char: Char) {
                if (space) {
                    text.append(' ')
                    space = false
                }

                text.append(char)
            }

            var index = 0

            while (index < raw.length) {
                val char = raw[index]

                when {
                    char == '\\' && index + 1 < raw.length -> {
                        index += 1

                        when (val escaped = raw[index]) {
                            'n' -> append('\n')
                            't' -> append('\t')
                            'u' -> {
                                append(raw.substring(index + 1, index + 5).toInt(16).toChar())
                                index += 4
                            }
                            else -> append(escaped)
                        }
                    }
                    char == '"' -> quoted = !quoted
                    char.isWhitespace() && !quoted -> space = text.isNotEmpty()
                    else -> append(char)
                }

                index += 1
            }

            return text.toString()
        }
    }
}
