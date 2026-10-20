package com.hyuchia.orbit.present

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.LanguageCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.PageWords
import com.hyuchia.orbit.page.inlinePictures
import com.hyuchia.orbit.page.pageWords
import com.hyuchia.orbit.page.protocolJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

/** The app's Cast channel: the receiver on the TV (web/cast-receiver.ts) listens on the same one. */
private const val NAMESPACE = "urn:x-cast:com.hyuchia.orbit.present"
/** Characters of a command per Cast message: messages carry at most 64 KB, and escaping can double the text. */
internal const val CAST_PART_LENGTH = 12_000
/** Pictures go to a TV at most this many pixels on their longer side: enough for the screen, quick to send. */
private const val TV_PICTURE_SIDE = 1280
private const val TAG = "Cast"

/** The build for Google Play casts to TVs with Google Cast, where it can; elsewhere, Android's screen casting. */
val tvCasting: TvCasting = GoogleCast

/** Whether this build casts to TVs: it needs a receiver registered with Google Cast (see CONTRIBUTING.md). */
private val castsToTvs: Boolean get() = BuildConfig.CAST_RECEIVER_ID.isNotBlank()

/**
 * Google Cast once a presentation started it, or null before. Presenting
 * starts it, and only presenting: until then it doesn't run, nor look for
 * TVs. Once started, Google Cast itself looks for TVs whenever the app
 * comes back on screen, until the app's process ends: nothing it offers
 * turns that off.
 */
private val started = MutableStateFlow<CastContext?>(null)

private object GoogleCast : TvCasting {
    @Composable
    override fun rememberTv(): Tv? {
        val session = rememberCastSession(rememberCast())
        val context = LocalContext.current
        return remember(session, context) { session?.let { CastTv(it, context) } }
    }

    @Composable
    override fun Button(dark: Boolean, modifier: Modifier, screenCast: Boolean) {
        when (val cast = rememberCast()) {
            is CastState.Ready -> CastRouteButton(cast.context, dark, modifier)
            // Phones without Google Play services, Huawei's say, still get a presentation onto a TV.
            CastState.Unavailable -> if (screenCast) {
                ScreenCastButton(modifier)
            }
            CastState.Starting -> Unit
        }
    }

    @Composable
    override fun CastingButton(dark: Boolean, modifier: Modifier) {
        val cast by started.collectAsState()
        val context = cast ?: return
        val session = rememberCastSession(remember(context) { CastState.Ready(context) })

        if (session != null) {
            RouteButton(context, dark, modifier)
        }
    }
}

/** Google Cast's settings for the app: the receiver a TV runs, the audience's page. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(BuildConfig.CAST_RECEIVER_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** A TV Google Cast is connected to, which presentations reach in the language of `context`, the app's screen. */
private class CastTv(private val session: CastSession, private val context: Context) : Tv {
    override fun audience(mapFolder: File, scope: CoroutineScope): Audience = CastAudience(session, mapFolder, scope, pageWords(context))
}

/** What an audience on a TV needs of the Cast session to it: the TV's name, and the app's channel both ways. */
internal interface TvChannel {
    val name: String?

    /** Sends a message to the TV, from the main thread; it goes nowhere once the TV is gone. */
    fun send(message: String)

    /** Hears each message the TV sends, until it's given null. */
    fun listen(listener: ((String) -> Unit)?)
}

/** The app's channel on a Cast session. */
private class SessionChannel(private val session: CastSession) : TvChannel {
    override val name: String? = session.castDevice?.friendlyName

    override fun send(message: String) {
        session.sendMessage(NAMESPACE, message)
    }

    override fun listen(listener: ((String) -> Unit)?) {
        try {
            if (listener == null) {
                session.removeMessageReceivedCallbacks(NAMESPACE)
            } else {
                session.setMessageReceivedCallbacks(NAMESPACE) { _, _, message -> listener(message) }
            }
        } catch (failure: IOException) {
            Logs.warn(TAG, "Couldn't listen to the TV, or stop", failure)
        }
    }
}

/**
 * A TV the phone casts to: it runs the audience's page and hears each step
 * on the app's channel, and says on it how a video it plays is going. A TV
 * can't reach the pictures kept on the phone, so they go inside the map
 * (`withPictures`), shrunk for the screen; commands keep their order while
 * that happens. The map's video files are too big to go along: the TV shows
 * their posters.
 *
 * The phone says hello first, with the protocol it speaks, and the TV's
 * receiver answers with its own (see [ReceiverWatch]): a command newer than
 * the receiver is left out, and the presenter hears when the TV's Orbit is
 * older than the map. Then the TV hears the app's language and `words`, so
 * the presentation speaks the phone's language.
 */
class CastAudience internal constructor(
    private val channel: TvChannel,
    scope: CoroutineScope,
    words: PageWords,
    private val withPictures: (String) -> String,
) : Audience {
    constructor(session: CastSession, mapFolder: File, scope: CoroutineScope, words: PageWords) :
        this(SessionChannel(session), scope, words, { markdown -> inlinePictures(markdown, mapFolder, TV_PICTURE_SIDE) })

    override val name: String? = channel.name

    override val playsMapVideos: Boolean = false

    private val queue = Channel<AudienceCommand>(Channel.UNLIMITED)

    private val watch = ReceiverWatch()

    override val notice: StateFlow<ScreenNotice?> = watch.notice

    @Volatile
    private var mediaListener: ((MapMessage.MediaState) -> Unit)? = null

    /** The commands on their way to the TV, in order, until the audience closes. */
    private val delivery: Job

    init {
        channel.listen { message -> watch.heard(message)?.let { report -> mediaListener?.invoke(report) } }

        delivery = scope.launch {
            deliver(protocolJson.encodeToString(SenderHello.serializer(), SenderHello(AUDIENCE_PROTOCOL, BuildConfig.VERSION_NAME)))
            deliver(protocolJson.encodeToString(LanguageCommand.serializer(), LanguageCommand(words)))

            for (command in queue) {
                if (!watch.follows(command)) {
                    Logs.note(TAG, "The TV's receiver speaks protocol ${watch.known.protocol}: a command from ${command.protocol} is left out")
                    continue
                }

                val sendable = if (command is AudienceCommand.Show) {
                    watch.showing(command.markdown)
                    command.copy(markdown = withContext(Dispatchers.IO) { tvMarkdown(command.markdown, withPictures) }, baseUrl = null)
                } else {
                    command
                }

                deliver(protocolJson.encodeToString(AudienceCommand.serializer(), sendable))
            }
        }

        scope.launch {
            delay(RECEIVER_ANSWER_WAIT)
            watch.silent()
        }
    }

    override fun send(command: AudienceCommand) {
        queue.trySend(command)
    }

    override fun listen(listener: (MapMessage.MediaState) -> Unit) {
        mediaListener = listener
    }

    /** The TV stays connected, back on its waiting screen, ready for the next presentation. */
    override fun close() {
        // The screen's work ends with the presentation, so the end can't wait in line behind what's still on its
        // way, such as a map whose pictures are being shrunk: that's dropped, and the TV hears the end now.
        delivery.cancel()
        queue.close()
        deliver(protocolJson.encodeToString(AudienceCommand.serializer(), AudienceCommand.End))
        channel.listen(null)
    }

    private fun deliver(json: String) {
        if (json.length <= CAST_PART_LENGTH) {
            channel.send(json)
            return
        }

        val parts = castParts(json)

        parts.forEachIndexed { index, text ->
            channel.send(protocolJson.encodeToString(CastPart.serializer(), CastPart(index, parts.size, text)))
        }
    }
}

/**
 * The map for a TV, its pictures inside by `withPictures`; as it is when
 * they can't be read, one gone from the phone say: the TV shows the map
 * without them rather than no map at all.
 */
internal fun tvMarkdown(markdown: String, withPictures: (String) -> String): String = try {
    withPictures(markdown)
} catch (unreadable: IOException) {
    markdown
}

/** A piece of a command too big for one Cast message; the receiver joins them in order. */
@Serializable
private data class CastPart(val index: Int, val count: Int, val text: String, val type: String = "part")

/** The text in pieces of at most [CAST_PART_LENGTH], never splitting a character made of two halves, such as an emoji. */
internal fun castParts(text: String): List<String> {
    val parts = mutableListOf<String>()
    var start = 0

    while (start < text.length) {
        var end = minOf(start + CAST_PART_LENGTH, text.length)

        if (end < text.length && Character.isHighSurrogate(text[end - 1])) {
            end -= 1
        }

        parts += text.substring(start, end)
        start = end
    }

    return parts
}

/** Where Google Cast is on this phone. */
private sealed interface CastState {
    data object Starting : CastState

    /** The build has no receiver, the phone has no Google Play services, or Cast didn't start. */
    data object Unavailable : CastState

    class Ready(val context: CastContext) : CastState
}

/** Google Cast, once it's ready, or why it isn't. */
@Composable
private fun rememberCast(): CastState {
    val context = LocalContext.current

    val cast by produceState<CastState>(CastState.Starting, context) {
        if (!castsToTvs || !hasPlayServices(context)) {
            value = CastState.Unavailable
            return@produceState
        }

        try {
            CastContext.getSharedInstance(context.applicationContext, ContextCompat.getMainExecutor(context))
                .addOnSuccessListener {
                    started.value = it
                    value = CastState.Ready(it)
                }
                .addOnFailureListener { error ->
                    Logs.warn(TAG, "Google Cast isn't available", error)
                    value = CastState.Unavailable
                }
        } catch (unavailable: RuntimeException) {
            Logs.warn(TAG, "Google Cast isn't available", unavailable)
            value = CastState.Unavailable
        }
    }

    return cast
}

/** Whether the phone has Google Play services, which Google Cast runs on. */
private fun hasPlayServices(context: Context): Boolean =
    runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS }.getOrDefault(false)

/** The TV the phone is casting to, while it is. */
@Composable
private fun rememberCastSession(cast: CastState): CastSession? {
    val manager = (cast as? CastState.Ready)?.context?.sessionManager
    var session by remember(manager) { mutableStateOf(manager?.currentCastSession?.takeIf { it.isConnected }) }

    DisposableEffect(manager) {
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(started: CastSession, sessionId: String) {
                session = started
            }

            override fun onSessionResumed(resumed: CastSession, wasSuspended: Boolean) {
                session = resumed
            }

            override fun onSessionEnded(ended: CastSession, error: Int) {
                session = null
            }

            override fun onSessionSuspended(suspended: CastSession, reason: Int) {
                session = null
            }

            override fun onSessionStarting(starting: CastSession) = Unit
            override fun onSessionStartFailed(failed: CastSession, error: Int) = Unit
            override fun onSessionEnding(ending: CastSession) = Unit
            override fun onSessionResuming(resuming: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(failed: CastSession, error: Int) = Unit
        }

        manager?.addSessionManagerListener(listener, CastSession::class.java)
        onDispose { manager?.removeSessionManagerListener(listener, CastSession::class.java) }
    }

    return session
}

/** Whether there's a TV around to cast to. */
@Composable
private fun rememberTvsAround(cast: CastContext): Boolean {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context) }
    val selector = cast.mergedSelector ?: return false
    var around by remember(selector) { mutableStateOf(router.isRouteAvailable(selector, MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE)) }

    DisposableEffect(router, selector) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh(router)
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh(router)
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh(router)

            private fun refresh(router: MediaRouter) {
                around = router.isRouteAvailable(selector, MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE)
            }
        }

        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        onDispose { router.removeCallback(callback) }
    }

    return around
}

/** Google's Cast button: it picks a TV, or stops casting to it, and shows only when there's a TV around. */
@Composable
private fun CastRouteButton(cast: CastContext, dark: Boolean, modifier: Modifier) {
    val session = rememberCastSession(remember(cast) { CastState.Ready(cast) })

    if (!rememberTvsAround(cast) && session == null) {
        return
    }

    RouteButton(cast, dark, modifier)
}

/** Google's Cast button itself. It looks for TVs only once it's tapped, in the list it opens. */
@Composable
private fun RouteButton(cast: CastContext, dark: Boolean, modifier: Modifier) {
    key(dark, cast) {
        AndroidView(
            factory = { context ->
                // Google's button and its dialogs are built on AppCompat's themes.
                val theme = if (dark) androidx.appcompat.R.style.Theme_AppCompat_NoActionBar else androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar

                MediaRouteButton(ContextThemeWrapper(context, theme)).apply {
                    CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
                }
            },
            modifier = modifier.size(48.dp),
        )
    }
}
