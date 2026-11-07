package com.hyuchia.orbit.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import com.hyuchia.orbit.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** A speech service that hasn't answered this long after being asked to stop has nothing more to say. */
private const val STOP_WAIT_MILLISECONDS = 3_000L

/** How long to wait before asking a busy speech service again. */
private const val BUSY_WAIT_MILLISECONDS = 300L

/** Where one sentence ends and the next begins, in what was said: a space after its mark, or none after a full-width one. */
private val SentenceEnd = Regex("(?<=[.!?])\\s+|(?<=[。！？])\\s*|\\n+")

/** How this phone can turn speech into ideas. */
enum class Hearing {
    /** A speech service Orbit listens through itself, idea after idea. */
    Service,

    /** Only another app's listening screen, which hears one go at a time. */
    Screen,

    /** Neither, as on many phones without Google: the keyboard's microphone is the way. */
    None,
}

/** The way this phone hears: a speech service, another app's listening screen, or neither. */
fun hearing(context: Context): Hearing = when {
    SpeechRecognizer.isRecognitionAvailable(context) -> Hearing.Service
    listeningScreen(context).resolveActivity(context.packageManager) != null -> Hearing.Screen
    else -> Hearing.None
}

/** Another app's screen that listens once and answers with what it heard, asking for ideas in the language of `context`, and hearing them in it. */
fun listeningScreen(context: Context): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, listeningLanguage(context))
    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_say_your_ideas))
}

/**
 * The language ideas are heard in: Orbit's, as `context` has it, which
 * Settings can make other than the phone's (Android 13 and later). Someone
 * who chose Spanish for Orbit on an English phone says their ideas in
 * Spanish, as Orbit asks for them.
 */
internal fun listeningLanguage(context: Context): String = context.resources.configuration.locales[0].toLanguageTag()

/**
 * The ideas in what was said: one for each sentence, starting with a
 * capital and without a closing full stop. A speech service that doesn't
 * punctuate gives one idea for each pause.
 */
fun ideasFrom(text: String, locale: Locale = Locale.getDefault()): List<String> =
    text.split(SentenceEnd)
        .map { it.trim().trimEnd('.', '。').trim() }
        .filter { it.isNotEmpty() }
        .map { sentence -> sentence.replaceFirstChar { it.titlecase(locale) } }

/** What's heard: whether it's listening, the words of the idea being said, and how loud they are. */
data class Heard(
    val listening: Boolean = false,
    val words: String = "",

    /** The voice's loudness, from 0 to 1. */
    val level: Float = 0f,

    /** Why it stopped listening, when something went wrong. */
    val problem: String? = null,
)

/**
 * A speech service, as [Listening] hears through it: the phone's own
 * ([SpeechRecognizer]), or a stand-in in tests. It tells what it hears to
 * the [RecognitionListener] it was made with.
 */
internal interface Recognizer {
    fun startListening(intent: Intent)

    /** Stops listening after what's being said, which it still answers for. */
    fun stopListening()

    /** Lets go of the speech service, and of the microphone it may still hold: it answers nothing more. */
    fun destroy()
}

/** The phone's speech service, telling `listener` what it hears. */
private class ServiceRecognizer(context: Context, listener: RecognitionListener) : Recognizer {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(listener) }

    override fun startListening(intent: Intent) = recognizer.startListening(intent)

    override fun stopListening() = recognizer.stopListening()

    override fun destroy() = recognizer.destroy()
}

/**
 * Listens through the phone's speech service until [stop], in Orbit's
 * language ([listeningLanguage]). A pause ends each idea, and its
 * sentences come out through `onIdeas`. Speech services
 * stop listening at a pause, so it starts again at once while it's wanted.
 * `onFinished` hears when listening is over, with the reason when something
 * went wrong. Everything happens on the main thread, as speech services need.
 *
 * Each talk has a speech service of its own, made as it starts and let go
 * as it ends: one that's slow to answer, or never does, gives nothing to a
 * talk that's over, and keeps no microphone. `recognizers` makes them.
 */
class Listening internal constructor(
    private val context: Context,
    private val onIdeas: (List<String>) -> Unit,
    private val onFinished: (problem: String?) -> Unit,
    private val recognizers: (RecognitionListener) -> Recognizer,
) {
    constructor(context: Context, onIdeas: (List<String>) -> Unit, onFinished: (problem: String?) -> Unit) :
        this(context, onIdeas, onFinished, { listener -> ServiceRecognizer(context, listener) })

    private val state = MutableStateFlow(Heard())
    val heard: StateFlow<Heard> = state.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())

    /** The talk's speech service, while there's a talk. */
    private var recognizer: Recognizer? = null

    /** Listening is wanted: after each idea, it listens for the next. */
    private var wanted = false

    /** The speech service is hearing an idea, and will answer for it. */
    private var hearing = false

    fun start() {
        handler.removeCallbacksAndMessages(null)
        // A talk before this one that's still finishing gives this one nothing: its speech service goes.
        letGo()
        wanted = true
        state.value = Heard(listening = true)
        listen()
    }

    /** Stops after the idea being said, which still comes out, unless the speech service takes too long to say it. */
    fun stop() {
        wanted = false
        handler.removeCallbacksAndMessages(null)

        if (hearing) {
            recognizer?.stopListening()
            handler.postDelayed(::finish, STOP_WAIT_MILLISECONDS)
        } else {
            finish()
        }
    }

    /** Stops at once, the idea being said left out, and lets the speech service and the microphone go. */
    fun close() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        letGo()
        hearing = false
        state.update { it.copy(listening = false, words = "", level = 0f) }
    }

    /** Lets the talk's speech service go: whatever it says after this is heard by no one. */
    private fun letGo() {
        recognizer?.destroy()
        recognizer = null
    }

    /** A speech service for the talk, whose answers the talk alone hears. */
    private fun newRecognizer(): Recognizer {
        val answers = Answers()

        return recognizers(answers).also { made ->
            answers.from = made
            recognizer = made
        }
    }

    private fun listen() {
        val recognizer = recognizer ?: newRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, listeningLanguage(context))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

            // Punctuation, where the service can add it, splits what's said into sentences, each an idea.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
        }

        hearing = true
        recognizer.startListening(intent)
    }

    /** Listens again once the speech service is done with the last idea. */
    private fun listenAgain(delay: Long = 0L) {
        handler.postDelayed({
            if (wanted) {
                listen()
            }
        }, delay)
    }

    /** The talk is over: the last idea is in, the speech service gave up, or it took too long to say the last. */
    private fun finish() {
        handler.removeCallbacksAndMessages(null)
        hearing = false
        letGo()

        if (state.value.listening) {
            state.update { it.copy(listening = false, words = "", level = 0f) }
            onFinished(null)
        }
    }

    /** Stops listening, saying why in the app's language. */
    private fun fail(@StringRes reason: Int) {
        val problem = context.getString(reason)
        wanted = false
        handler.removeCallbacksAndMessages(null)
        hearing = false
        letGo()
        state.update { it.copy(listening = false, words = "", level = 0f, problem = problem) }
        onFinished(problem)
    }

    /** What one speech service says, heard only while it's the talk's: a talk that's over, or another one, hears none of it. */
    private inner class Answers : RecognitionListener {
        /** The speech service these answers come from. */
        var from: Recognizer? = null

        private val current: Boolean get() = from != null && from === recognizer

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (!current) {
                return
            }

            // Speech services report about -2 dB in a quiet room and 10 dB for a raised voice.
            state.update { it.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val words = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

            if (current && words.isNotBlank()) {
                state.update { it.copy(words = words) }
            }
        }

        override fun onResults(results: Bundle?) {
            // Words that come too late, from a talk that's over, are left out.
            if (!current) {
                return
            }

            hearing = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val ideas = ideasFrom(text)
            state.update { it.copy(words = "", level = 0f) }

            if (ideas.isNotEmpty()) {
                onIdeas(ideas)
            }

            if (wanted) {
                listenAgain()
            } else {
                finish()
            }
        }

        override fun onError(error: Int) {
            if (!current) {
                return
            }

            hearing = false

            when (error) {
                // Silence, a busy service, or the end of a stop: listen on while wanted.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    if (wanted) {
                        listenAgain(BUSY_WAIT_MILLISECONDS)
                    } else {
                        finish()
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(R.string.voice_needs_microphone)
                SpeechRecognizer.ERROR_AUDIO -> fail(R.string.voice_microphone_busy)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> fail(R.string.voice_needs_connection)
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> fail(R.string.voice_language_missing)
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> fail(R.string.voice_service_busy)
                else -> fail(R.string.voice_service_stopped)
            }
        }
    }
}
