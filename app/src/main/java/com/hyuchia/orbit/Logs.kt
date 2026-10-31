package com.hyuchia.orbit

import android.util.Log

/**
 * The app's log. Notes and warnings only reach it in debug builds, so a
 * release build logs nothing of what people write. Errors always do: never
 * pass them a map's text, an idea, a link or a device's name, in the
 * message or the exception. Logs can be read by tools on the phone.
 */
object Logs {
    /** Whether Android's log is there to write to: not in the unit tests, which run on the JVM without it. */
    private val onAndroid = System.getProperty("java.vm.name") == "Dalvik"

    fun note(tag: String, message: String) {
        if (BuildConfig.DEBUG && onAndroid) {
            Log.i(tag, message)
        }
    }

    fun warn(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG && onAndroid) {
            Log.w(tag, message, error)
        }
    }

    fun error(tag: String, message: String, error: Throwable? = null) {
        if (onAndroid) {
            Log.e(tag, message, error)
        }
    }
}
