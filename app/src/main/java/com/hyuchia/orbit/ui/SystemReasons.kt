package com.hyuchia.orbit.ui

import android.content.res.Resources

/**
 * Why something failed, as Android or a library says it. That's in English, so only an English sentence takes
 * it: in another language, the sentence reads better without it than with English in the middle.
 */
fun Resources.systemReason(failure: Throwable): String? = failure.message?.takeIf { configuration.locales[0].language == "en" }
