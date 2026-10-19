package com.hyuchia.orbit.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.openLink
import java.io.ByteArrayInputStream

/** A video in the provider's fullscreen: the view the page handed over, and how to hand it back. */
private class Fullscreen(val view: View, val callback: WebChromeClient.CustomViewCallback)

/**
 * A YouTube or Vimeo video, full screen, in the provider's own player inside
 * the app: YouTube's in its privacy-enhanced mode, Vimeo's told not to track.
 * It plays at once, the keys play, pause and seek it, its fullscreen button
 * works, and a button opens the video on its site or in its app.
 *
 * The player has a web view of its own, apart from the map's: no channel to
 * the app, no files or content, JavaScript only because the players need
 * it, no cookies from them, and nothing loads in it but its page and the
 * players' frames; any other page opens outside, in a tab. It pauses as the
 * app leaves the screen, and goes as the window closes. `onKey` takes keys
 * before the player's own, as a presentation does.
 */
@Composable
fun WebVideoPlayer(source: VideoSource, title: String, onClosed: () -> Unit, onKey: (KeyEvent) -> Boolean = { false }) {
    val context = LocalContext.current
    val origin = remember { playerOrigin(context.packageName) }
    val page = remember(source, title) { playerPage(source, title, origin) }
    var fullscreen by remember { mutableStateOf<Fullscreen?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val closed by rememberUpdatedState(onClosed)

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        webView?.let { view ->
            view.evaluateJavascript("window.orbitPlayer && window.orbitPlayer.pause()", null)
            view.onPause()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { webView?.onResume() }

    if (page == null) {
        return
    }

    FullScreenWindow(
        onClosed = onClosed,
        immersive = fullscreen != null,
        // Escape leaves the player's fullscreen first, as it does in a browser.
        onEscape = { window ->
            val shown = fullscreen

            if (shown != null) {
                shown.callback.onCustomViewHidden()
                fullscreen = null
            } else {
                window.close()
            }
        },
    ) { window ->
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Column(Modifier.fillMaxSize()) {
                    WebPlayerTopBar(title, source, onClose = window::close)
                    AndroidView(
                        factory = { viewContext ->
                            playerView(
                                viewContext,
                                origin,
                                page,
                                onFullscreen = { fullscreen = it },
                                onGone = { closed() },
                            ).also { webView = it }
                        },
                        onRelease = { view ->
                            fullscreen?.callback?.onCustomViewHidden()
                            webView = null
                            view.stopLoading()
                            (view.parent as? ViewGroup)?.removeView(view)
                            view.destroy()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))),
                    )
                }

                fullscreen?.let { shown ->
                    // The provider's fullscreen: its video over everything, until its button or Back hands it back.
                    key(shown) {
                        AndroidView(
                            factory = { shown.view.apply { (parent as? ViewGroup)?.removeView(this) } },
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                        )
                    }
                }
            }
        }

        // Back leaves the player's fullscreen first.
        BackHandler(enabled = fullscreen != null) {
            fullscreen?.callback?.onCustomViewHidden()
            fullscreen = null
        }

        PlayerKeys(onKey) { command -> webView?.evaluateJavascript("window.orbitPlayer && window.orbitPlayer.$command", null) }
    }
}

/**
 * The player's keys, whatever has the focus inside it: space, K and the
 * media key play and pause, the arrows go five seconds back and on, J and L
 * ten, and M turns the sound off and on, as on YouTube. They reach the
 * player through its page's `orbitPlayer`, with `run`, unless `first` takes them.
 */
@Composable
private fun PlayerKeys(first: (KeyEvent) -> Boolean, run: (String) -> Unit) {
    val view = LocalView.current
    val command by rememberUpdatedState(run)
    val before by rememberUpdatedState(first)

    DisposableEffect(view) {
        val window = generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        val original = window?.callback

        if (window == null || original == null) {
            return@DisposableEffect onDispose {}
        }

        window.callback = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (before(event)) {
                    return true
                }

                val modified = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed

                val action = when (event.keyCode) {
                    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "toggle()"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "seek(-5)"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "seek(5)"
                    KeyEvent.KEYCODE_J -> "seek(-10)"
                    KeyEvent.KEYCODE_L -> "seek(10)"
                    KeyEvent.KEYCODE_M -> "mute()"
                    else -> null
                }

                if (action == null || modified) {
                    return original.dispatchKeyEvent(event)
                }

                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    command(action)
                }

                return true
            }
        }

        onDispose { window.callback = original }
    }
}

/** Closing, the video's name, and watching it on its site or in its app. */
@Composable
private fun WebPlayerTopBar(title: String, source: VideoSource, onClose: () -> Unit) {
    val context = LocalContext.current
    val site = if (source is VideoSource.Vimeo) "Vimeo" else "YouTube"
    val close = stringResource(R.string.common_close)
    val watch = stringResource(R.string.media_watch_on, site)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Hint(close) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = close) }
        }

        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))

        Hint(watch) {
            IconButton(onClick = { openVideoElsewhere(context, source) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = watch)
            }
        }
    }
}

/** The web view the provider's player plays in, loading `page` as `origin`. */
@SuppressLint("SetJavaScriptEnabled")
private fun playerView(context: Context, origin: String, page: String, onFullscreen: (Fullscreen?) -> Unit, onGone: () -> Unit): WebView =
    WebView(context).apply {
        setBackgroundColor(AndroidColor.BLACK)
        // A player has nothing to fill in, so the phone's autofill service stays out of it.
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        // The players run on JavaScript, and keep their settings in their pages' storage.
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        // The video plays as the player opens, as it was asked for.
        settings.mediaPlaybackRequiresUserGesture = false
        // A link that opens a window loads here instead, where it's sent outside.
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = PlayerClient(origin, onGone)
        webChromeClient = PlayerChrome(onFullscreen)
        loadDataWithBaseURL("$origin/", page, "text/html", "UTF-8", null)
    }

/**
 * Keeps the player's page where it is: only the players' own frames load in
 * it, and other pages open outside. Lint doesn't see Kotlin's
 * [onRenderProcessGone] here, as it doesn't on the map's page either.
 */
@SuppressLint("MissingOnRenderProcessGone")
private class PlayerClient(origin: String, private val onGone: () -> Unit) : WebViewClient() {
    private val pageHost = origin.toUri().host

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url

        if (!request.isForMainFrame) {
            return !isPlayerHost(url.host)
        }

        if (url.scheme == "http" || url.scheme == "https") {
            openLink(view.context, url.toString())
        }

        return true
    }

    // The keyboard goes to the page, and from there to the player, whose own keys then work.
    override fun onPageFinished(view: WebView, url: String?) {
        view.requestFocus()
    }

    // The page's own address answers nothing: it's a name, not a site.
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        if (request.url.host == pageHost) WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0))) else null

    // The system can stop the page's process: the player closes, and the app goes on.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Logs.warn("OrbitVideo", "The player's page stopped (crashed: ${detail.didCrash()})")
        onGone()
        return true
    }
}

/**
 * The players' fullscreen, and what protected videos need (Vimeo plays its
 * videos that way), for the players alone; nothing else of what a browser
 * offers pages: no dialogs, no camera or microphone.
 */
private class PlayerChrome(private val onFullscreen: (Fullscreen?) -> Unit) : WebChromeClient() {
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onFullscreen(Fullscreen(view, callback))
    }

    override fun onHideCustomView() {
        onFullscreen(null)
    }

    // No grey play symbol before the player draws its own.
    override fun getDefaultVideoPoster(): Bitmap = createBitmap(1, 1)

    override fun onPermissionRequest(request: PermissionRequest) {
        val protectedMedia = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID

        if (isPlayerHost(request.origin.host) && request.resources.contentEquals(arrayOf(protectedMedia))) {
            request.grant(arrayOf(protectedMedia))
        } else {
            request.deny()
        }
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean = !BuildConfig.DEBUG

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
