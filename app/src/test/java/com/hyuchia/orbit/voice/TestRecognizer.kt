package com.hyuchia.orbit.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

/**
 * A speech service that says what a test tells it to, to the listener
 * [Listening] made it with, as a real one does: whether anyone is still
 * listening or not, and however late.
 */
internal class TestRecognizer(private val listener: RecognitionListener) : Recognizer {
    /** How many times it was asked to listen. */
    var listened = 0
        private set

    var stopped = false
        private set

    var destroyed = false
        private set

    override fun startListening(intent: Intent) {
        listened += 1
    }

    override fun stopListening() {
        stopped = true
    }

    override fun destroy() {
        destroyed = true
    }

    /** It heard `words` and says so, as a speech service does once a pause ends them. */
    fun says(words: String) {
        listener.onResults(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(words)) })
    }

    /** It heard nothing, or gave up, as `error` says. */
    fun fails(error: Int) {
        listener.onError(error)
    }
}
