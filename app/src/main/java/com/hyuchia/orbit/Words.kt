package com.hyuchia.orbit

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.hyuchia.orbit.page.pageLanguage

/**
 * The app's strings, for code that makes text people see without a screen
 * to read them from: the maps and their files, the pages the local servers
 * serve, a goal's numbers in words. In the app they're its resources, in
 * the language it's in ([ResourceWords]); tests read them from the
 * resource files.
 */
interface Words {
    /** The language the strings are in, as a BCP 47 tag ("es-MX"): the `lang` of the pages they're written into. */
    val language: String

    fun string(@StringRes id: Int, vararg arguments: Any): String

    /** The plural `count` takes, filled in with `arguments`. */
    fun plural(@PluralsRes id: Int, count: Int, vararg arguments: Any): String
}

/** The app's own strings in the language `context` is in, read as they're needed, so a new language shows at once. */
class ResourceWords(private val context: Context) : Words {
    override val language: String get() = pageLanguage(context)

    override fun string(id: Int, vararg arguments: Any): String =
        if (arguments.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *arguments)
        }

    override fun plural(id: Int, count: Int, vararg arguments: Any): String = context.resources.getQuantityString(id, count, *arguments)
}

/** The app's strings in the language the screen shows. */
@Composable
fun rememberWords(): Words {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(configuration, context) { ResourceWords(context) }
}
