package com.hyuchia.orbit.data

import android.content.Context
import java.io.InputStream

/**
 * Where `path` is in the app's assets for the language the app is in:
 * `<language>/<path>` when that language has its own file or folder there
 * (see TRANSLATING.md), else `path` itself. It's the language of the app's
 * configuration, which its strings are in and which can differ from the
 * phone's.
 */
fun Context.assetPath(path: String): String {
    val language = resources.configuration.locales[0]?.language.orEmpty()

    if (language.isEmpty()) {
        return path
    }

    val translated = "$language/$path"
    val beside = assets.list(translated.substringBeforeLast('/')).orEmpty()

    return if (translated.substringAfterLast('/') in beside) {
        translated
    } else {
        path
    }
}

/** Opens `path` from the app's assets, in the language the app is in when that language has its own (see [assetPath]). */
fun Context.openAsset(path: String): InputStream = assets.open(assetPath(path))
