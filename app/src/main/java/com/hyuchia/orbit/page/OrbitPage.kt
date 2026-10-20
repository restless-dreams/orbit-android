package com.hyuchia.orbit.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.R
import com.hyuchia.orbit.media.isPlayerHost
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.seconds

/** Pages are served from the app under a secure origin, so they can use the web's modern APIs. */
const val PAGE_HOST = "appassets.androidplatform.net"
private const val PAGE_URL = "https://$PAGE_HOST/assets/map/index.html"
/** The page an audience watches on a second screen while a map is presented. */
const val AUDIENCE_URL = "https://$PAGE_HOST/assets/map/audience.html"

/** Where a map's pictures are served from: its folder, as `baseUrl` for Orbit. */
fun mapBaseUrl(mapId: String): String = "https://$PAGE_HOST$MAPS_PATH$mapId/"

/** The players the audience's page frames for a map's YouTube and Vimeo videos, as Orbit's media viewer embeds them. */
private const val PLAYER_FRAMES = "https://www.youtube-nocookie.com https://player.vimeo.com"

/**
 * What the app's pages may load: their own scripts and nothing else to run,
 * no plugins and no forms; their own styles; pictures, videos and fonts of
 * their own or from the web; and YouTube's and Vimeo's players, framed.
 * Whatever is at an `http` address is asked for over `https`: WebView would
 * otherwise refuse a map's picture there, since nothing goes over the web
 * in the clear.
 */
internal const val PAGE_POLICY = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
    "img-src 'self' https: data: blob:; media-src 'self' https: blob:; font-src 'self' data:; connect-src 'self' https: data: blob:; " +
    "frame-src $PLAYER_FRAMES; object-src 'none'; base-uri 'none'; form-action 'none'; upgrade-insecure-requests"

/** A job the page couldn't do. */
class PageException(message: String) : Exception(message)

/**
 * A WebView with Orbit's page loaded, and the channel to it. Pages are made
 * ahead of time by [PagePool], so a map draws as soon as it opens; the
 * context wrapper lets a page made before any screen move into one.
 *
 * The page says hello when its script runs, on the `orbitNative` channel
 * the app adds; commands go back to it on the same channel, as messages it
 * reads as JSON, never as code to run. Commands sent before the hello wait
 * ([WaitingCommands]). Everything here runs on the main thread. `pageUrl` is
 * the map's page, or the audience's ([AUDIENCE_URL]). Either speaks the
 * app's language ([speak]).
 */
class OrbitPage internal constructor(private val application: Context, mapsDirectory: File, private val pageUrl: String = PAGE_URL) {
    /** The page's path: the only one of the app's own this view loads as a page. */
    private val pagePath: String? = pageUrl.toUri().path

    private val context = MutableContextWrapper(application)
    private var ready = false
    private val waiting = WaitingCommands()
    private var nextJob = 1
    private val jobs = mutableMapOf<Int, CompletableDeferred<JsonElement?>>()

    /** Where commands go once the page said hello: back to its own frame, on the channel it said hello on. */
    private var channel: JavaScriptReplyProxy? = null

    /** The map the page draws, whose pictures it shows. */
    private var showing: String? = null

    /** The map whose pictures the audience's page shows, while it shows one. */
    private var audienceMap: String? = null

    /** The maps jobs are drawing pictures of, by job, while they do. */
    private val jobMaps = mutableMapOf<Int, String>()

    /**
     * The maps whose pictures and videos the page may load: the one it shows,
     * and the ones it's drawing pictures of. Read as files are asked for, off
     * the main thread.
     */
    @Volatile
    private var readableMaps: Set<String> = emptySet()

    /** Who hears the page's messages: the screen showing it. */
    var listener: ((MapMessage) -> Unit)? = null

    /** The map the page was last asked to draw; messages about any other one arrived late, and go nowhere. */
    private var expectedMap: String? = null

    /** False once the page's process stopped; the page can't be used again. */
    var isAlive = true
        private set

    /** Whether the page itself didn't load: every job fails at once, rather than waiting for it. */
    private var loadFailed = false

    /** Whether the view is destroyed already: by the page's process stopping, or by [destroy]. */
    private var destroyed = false

    /** The listener waiting for the window's focus to bring the keyboard up, while one waits. */
    private var focusWaiter: Pair<ViewTreeObserver, ViewTreeObserver.OnWindowFocusChangeListener>? = null

    /** Whether the page is still working on something it was asked for. */
    val busy: Boolean
        get() = jobs.isNotEmpty()

    /** Whether the phone's WebView can draw maps. When it can't, the page isn't loaded and every job fails at once. */
    private val drawsMaps = SystemWebView.of(application).drawsMaps

    /** What the page says when the phone's WebView can't draw maps (see [SystemWebView]). */
    private val tooOld: String get() = application.getString(R.string.page_webview_too_old)

    /** The language and words the page last heard from [speak]. */
    private var spoken: PageWords? = null

    val webView: WebView = createView(application, mapsDirectory)

    init {
        speak()
    }

    fun send(command: MapCommand) {
        send(command, job = null)
    }

    /** Sends a command: `job` is its number, for a job's. */
    private fun send(command: MapCommand, job: Int?) {
        val kind = when (command) {
            is MapCommand.Load -> {
                expectedMap = command.mapId
                showing = command.mapId ?: mapIdOf(command.baseUrl)
                // Read in the language the app speaks now: a map that's just a list gets its top idea named in it.
                speak()
                WaitingCommands.Kind.NewMap
            }
            MapCommand.Unload -> {
                expectedMap = null
                showing = null
                WaitingCommands.Kind.NewMap
            }
            else -> if (job != null) WaitingCommands.Kind.Job else WaitingCommands.Kind.Map
        }

        updateReadableMaps()
        sendEncoded(protocolJson.encodeToString(MapCommand.serializer(), command), kind, job)
    }

    private fun updateReadableMaps() {
        readableMaps = setOfNotNull(showing, audienceMap) + jobMaps.values
    }

    /**
     * Tells the page the app's language, with the words the page says itself
     * ([PageWords]), when they changed since it last heard them: the language
     * of the screen the page is in, or the app's while it's in none. Orbit's
     * own words follow it. The page hears it as it starts, as it moves into a
     * screen, before each map and job, and whenever its screen's language
     * changes while it shows a map (see `MapCanvas`).
     */
    fun speak() {
        val words = pageWords(context)

        if (words == spoken) {
            return
        }

        spoken = words
        sendEncoded(protocolJson.encodeToString(LanguageCommand.serializer(), LanguageCommand(words)), WaitingCommands.Kind.Words)
    }

    fun send(command: AudienceCommand) {
        val kind = when (command) {
            is AudienceCommand.Show -> {
                audienceMap = mapIdOf(command.baseUrl)
                WaitingCommands.Kind.NewMap
            }
            AudienceCommand.End -> {
                audienceMap = null
                WaitingCommands.Kind.NewMap
            }
            else -> WaitingCommands.Kind.Map
        }

        updateReadableMaps()
        sendEncoded(protocolJson.encodeToString(AudienceCommand.serializer(), command), kind)
    }

    /** Sends a command now, or once the page says hello. A page that didn't load never will, so nothing waits for it. */
    private fun sendEncoded(encoded: String, kind: WaitingCommands.Kind, job: Int? = null) {
        if (!isAlive || !drawsMaps || loadFailed) {
            return
        }

        val channel = channel

        if (!ready || channel == null) {
            waiting.add(encoded, kind, job)
            return
        }

        post(channel, encoded)
    }

    /** Sends a command on the page's channel, which exists only where WebView has message listeners: the page loads nowhere else. */
    private fun post(channel: JavaScriptReplyProxy, encoded: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            channel.postMessage(encoded)
        }
    }

    /** Sends a job and waits for its answer. */
    suspend fun <T> request(answer: DeserializationStrategy<T>, command: (job: Int) -> MapCommand): T {
        if (!isAlive) {
            throw PageException(application.getString(R.string.page_stopped))
        }

        if (!drawsMaps) {
            throw PageException(tooOld)
        }

        if (loadFailed) {
            throw PageException(application.getString(R.string.page_did_not_load))
        }

        val job = nextJob++
        val result = CompletableDeferred<JsonElement?>()
        jobs[job] = result
        // Pictures, conversions and ideas caught on the go name what they make up, and say what went wrong, in the app's language.
        speak()
        val sent = command(job)

        // A picture of a map loads that map's pictures, and only while it's being drawn.
        (sent as? MapCommand.Snapshot)?.baseUrl?.let(::mapIdOf)?.let { jobMaps[job] = it }

        // An answer that never comes (a message lost, a page stuck) is given up on, rather than waited for forever.
        val value = try {
            send(sent, job)
            withTimeout(REQUEST_TIMEOUT) { result.await() }
        } catch (late: TimeoutCancellationException) {
            Logs.warn(TAG, "The page didn't answer job $job in $REQUEST_TIMEOUT")
            throw PageException(application.getString(R.string.page_did_not_answer))
        } finally {
            // Answered, given up on, or no longer wanted by whoever asked: a job left behind would keep the page busy for good.
            jobs.remove(job)
            waiting.forget(job)

            if (jobMaps.remove(job) != null) {
                updateReadableMaps()
            }
        }

        return protocolJson.decodeFromJsonElement(answer, value ?: throw PageException(application.getString(R.string.page_sent_nothing_back)))
    }

    private var attachment = 0

    /**
     * Moves the page into a screen, so menus, selection and the keyboard work
     * in it. Returns the attachment, for [detach]: when a screen changes its
     * layout, the new place can take the page before the old one lets go.
     */
    fun attach(screen: Context): Int {
        (webView.parent as? ViewGroup)?.removeView(webView)
        context.baseContext = screen
        attachment += 1
        // The page speaks the screen's language: a screen made again as the app's language changes takes the page it had.
        speak()
        return attachment
    }

    /** Takes the page out of its screen, unless another place took it since `attachment`. */
    fun detach(attachment: Int = this.attachment) {
        if (attachment != this.attachment) {
            return
        }

        // A keyboard still waiting for the screen's focus doesn't come up on the next screen.
        stopWaitingForFocus()
        (webView.parent as? ViewGroup)?.removeView(webView)
        context.baseContext = application
    }

    fun destroy() {
        detach()
        abandon(application.getString(R.string.page_closed))
        destroyView()
        isAlive = false
        listener = null
    }

    /** Destroys the view once: a page whose process stopped destroyed it already, and older WebViews don't take a second. */
    private fun destroyView() {
        if (!destroyed) {
            destroyed = true
            webView.destroy()
        }
    }

    /**
     * Brings the keyboard up for the field the page focused: a page can't,
     * without a tap of its own. A menu that just closed may still hold the
     * window's focus, and Android ignores the keyboard until it comes back.
     */
    fun showKeyboard() {
        val view = webView

        val show = Runnable {
            if (!view.hasFocus()) {
                view.requestFocus()
            }

            view.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(view, 0)
        }

        stopWaitingForFocus()

        if (view.hasWindowFocus()) {
            view.post(show)
            return
        }

        val observer = view.viewTreeObserver

        val waiter = object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (hasFocus) {
                    stopWaitingForFocus()
                    view.post(show)
                }
            }
        }

        observer.addOnWindowFocusChangeListener(waiter)
        focusWaiter = observer to waiter
    }

    fun hideKeyboard() {
        // A keyboard still waiting for the window's focus doesn't come up after the field closed.
        stopWaitingForFocus()
        webView.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(webView.windowToken, 0)
    }

    private fun stopWaitingForFocus() {
        focusWaiter?.let { (observer, waiter) ->
            if (observer.isAlive) {
                observer.removeOnWindowFocusChangeListener(waiter)
            }
        }

        focusWaiter = null
    }

    private fun receive(data: String, replyProxy: JavaScriptReplyProxy) {
        val element = try {
            protocolJson.parseToJsonElement(data).jsonObject
        } catch (error: IllegalArgumentException) {
            unreadable(null, error)
            return
        }

        val message = try {
            protocolJson.decodeFromJsonElement(MapMessage.serializer(), element)
        } catch (error: IllegalArgumentException) {
            unreadable(element, error)
            return
        }

        // A message about a map the page drew before this one (a save that arrived late, say) never reaches this one's screen.
        val about = (element["map"] as? JsonPrimitive)?.contentOrNull

        if (message !is MapMessage.Result && about != null && about != expectedMap) {
            return
        }

        when (message) {
            MapMessage.Hello -> {
                channel = replyProxy
                ready = true
                waiting.take().forEach { post(replyProxy, it) }
            }
            is MapMessage.Result -> {
                val job = jobs.remove(message.job) ?: return

                if (message.error != null) {
                    job.completeExceptionally(PageException(message.error))
                } else {
                    job.complete(message.value)
                }

                return
            }
            is MapMessage.EditorOpen -> showKeyboard()
            MapMessage.EditorClose -> hideKeyboard()
            else -> Unit
        }

        listener?.invoke(message)
    }

    /**
     * A message the app can't read, which means it and its pages disagree on
     * what they say (ProtocolTest checks they don't): a save in it would be
     * lost, so it's an error in every build. Release builds log its kind
     * alone, since the rest can be a map's text.
     */
    private fun unreadable(element: JsonObject?, error: IllegalArgumentException) {
        Logs.error(TAG, "The page sent a ${messageKind(element)} message the app can't read")
        Logs.warn(TAG, "Unreadable message from the page", error)
    }

    /** The page will do nothing more: every job fails with `reason`, and nothing waits for a hello that won't come. */
    private fun abandon(reason: String) {
        jobs.values.forEach { it.completeExceptionally(PageException(reason)) }
        jobs.clear()
        waiting.clear()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(application: Context, mapsDirectory: File): WebView {
        val assets = WebViewAssetLoader.Builder()
            .setDomain(PAGE_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(application))
            // The app's font, for the map: res/font/google_sans_flex.ttf is /res/font/google_sans_flex.ttf.
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(application))
            .build()
        val files = MapFiles(mapsDirectory)

        return WebView(context).apply {
            // The page is transparent: Compose paints the background, so there is never a white flash.
            setBackgroundColor(Color.TRANSPARENT)
            // A map has no forms to fill in, so the phone's autofill service, which can be another
            // company's, never sees what's typed into one.
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            // The app plays its own haptics; the view's long-press buzz would double them.
            isHapticFeedbackEnabled = false
            setOnLongClickListener { true }
            // With a keyboard, the map shows which idea has the focus itself: no tint over the whole view.
            defaultFocusHighlightEnabled = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            // The audience's screen has no one to touch it: its videos start, with their sound, as the presenter starts them.
            // Every other page waits for a tap.
            settings.mediaPlaybackRequiresUserGesture = pageUrl != AUDIENCE_URL
            // The page sizes its own text, and maps zoom: the phone's font size would only make labels outgrow their planets.
            settings.textZoom = 100
            // Taking the focus from a keyboard, the page keeps its own: by default, the view would move
            // the page's focus to its first node, out of a field the map just opened. The page puts the
            // focus on the map itself when a key needs it.
            settings.setNeedInitialFocus(false)
            // Pages work while no screen shows them: thumbnails are drawn off screen.
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            webViewClient = PageClient(assets, files)
            webChromeClient = QuietChrome(players = pageUrl == AUDIENCE_URL)

            if (drawsMaps && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                // Only the page itself talks to the app: a frame inside it, even one of the app's own files, doesn't.
                WebViewCompat.addWebMessageListener(this, "orbitNative", setOf("https://$PAGE_HOST")) { _, message, _, isMainFrame, replyProxy ->
                    if (isMainFrame) {
                        message.data?.let { receive(it, replyProxy) }
                    }
                }

                loadUrl(pageUrl)
            } else {
                post { listener?.invoke(MapMessage.Error(tooOld)) }
            }
        }
    }

    /**
     * Keeps the page's console out of the phone's log in release builds: its
     * messages can quote a map (a link it blocked, say). Like a view with no
     * such client, it shows no alert, confirm or prompt dialogs. With
     * `players`, for the audience's page, YouTube's and Vimeo's players may
     * play protected videos, as Vimeo's are; nothing else is granted.
     */
    private class QuietChrome(private val players: Boolean) : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean = !BuildConfig.DEBUG

        override fun onPermissionRequest(request: PermissionRequest) {
            val protectedMedia = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID

            if (players && isPlayerHost(request.origin.host) && request.resources.contentEquals(arrayOf(protectedMedia))) {
                request.grant(arrayOf(protectedMedia))
            } else {
                request.deny()
            }
        }

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
            result.cancel()
            return true
        }
    }

    /**
     * Serves the page, with its content policy, and the pictures and videos
     * of the maps it draws, a video the parts it asks for; sends other links
     * out, and survives the page's process stopping.
     */
    private inner class PageClient(private val assets: WebViewAssetLoader, private val files: MapFiles) : WebViewClientCompat() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url

            if (url.host == PAGE_HOST && url.path.orEmpty().startsWith(MAPS_PATH)) {
                val range = request.requestHeaders?.entries?.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
                return files.answer(url, range) { it in readableMaps }
            }

            val response = assets.shouldInterceptRequest(url) ?: return null
            // The page itself comes with what it may load; nothing of the app's is ever read as another type than its own.
            val policy = if (request.isForMainFrame && url.path == pagePath) mapOf("Content-Security-Policy" to PAGE_POLICY) else emptyMap()
            response.responseHeaders = response.responseHeaders.orEmpty() + policy + ("X-Content-Type-Options" to "nosniff")
            return response
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // The audience's page frames YouTube's and Vimeo's players, which load in their frames.
            if (!request.isForMainFrame && pageUrl == AUDIENCE_URL && isPlayerHost(request.url.host)) {
                return false
            }

            if (request.url.host == PAGE_HOST) {
                // Only the page itself loads here. Anything else of the app's own, such as a file in a
                // map's media folder, would run beside the page with its messages to the app.
                return !(request.isForMainFrame && request.url.path == pagePath)
            }

            listener?.invoke(MapMessage.Open(request.url.toString()))
            return true
        }

        // Keys the page didn't use, such as Ctrl+Z on the map, go to the app's shortcuts.
        override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
            if ((view.context.findActivity() as? MainActivity)?.dispatchUnusedKey(event) == true) {
                return
            }

            super.onUnhandledKeyEvent(view, event)
        }

        // The system can stop the page's process to free memory: lose the map, not the app.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Logs.warn(TAG, "The page's process stopped (crashed: ${detail.didCrash()})")
            isAlive = false
            ready = false
            channel = null
            abandon(application.getString(R.string.page_stopped))
            stopWaitingForFocus()
            (view.parent as? ViewGroup)?.removeView(view)
            destroyView()
            listener?.invoke(MapMessage.Stopped)
            return true
        }

        // The page itself didn't load: what waits for it fails now, and what's asked of it later fails at once.
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
            if (request.isForMainFrame) {
                val code = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)) error.errorCode else null
                Logs.warn(TAG, "The page didn't load (error $code)")
                loadFailed = true
                abandon(application.getString(R.string.page_did_not_load))
            }
        }
    }

    private companion object {
        const val TAG = "OrbitPage"

        /** How long a job may take before the page is taken for stuck: the biggest maps draw in seconds on slow phones, never a minute. */
        val REQUEST_TIMEOUT = 60.seconds
    }
}

/** A message's kind (`saved`, say), or `unknown` for anything that isn't one of the protocol's words, so a log never holds a map's text. */
internal fun messageKind(element: JsonObject?): String =
    (element?.get("type") as? JsonPrimitive)?.contentOrNull?.takeIf { it.matches(MESSAGE_KIND) } ?: "unknown"

/** What the protocol's kinds of messages look like: a word or two, run together. */
private val MESSAGE_KIND = Regex("[A-Za-z]{1,32}")

/** The activity a context belongs to, through the wrappers around it. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
