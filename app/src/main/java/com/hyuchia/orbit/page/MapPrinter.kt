package com.hyuchia.orbit.page

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintJobInfo
import android.print.PrintManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.util.UUID

/** What a printed map's picture shows: web/print.ts's PrintRequest. */
@Serializable
data class PrintRequest(
    /** `focus`, an idea with its orbit, or `all`, the whole map. */
    val view: String,

    /** The idea in the middle of a focused picture; the top idea without one. */
    val focus: String? = null,
)

/** What a page laid out for paper says once it's laid out, and its pictures are on their way (web/web-page.ts). */
internal const val LAID_OUT = "laid-out"

/**
 * How many pictures a printed page goes without, from what it says once
 * they're in (web/web-page.ts): `ready`, or `ready:2` when two couldn't be
 * loaded. Null for anything else.
 */
internal fun picturesMissing(message: String?): Int? = when {
    message == "ready" -> 0
    message?.startsWith("ready:") == true -> message.removePrefix("ready:").toIntOrNull()?.takeIf { it > 0 }
    else -> null
}

/**
 * How long printing waits for a page laid out for paper before it gives up
 * (`givesUp`): [READY_TIMEOUT] from when the page starts loading until it
 * says it's laid out ([LAID_OUT]), which a big map takes most of, then
 * [PICTURES_TIMEOUT] from then for its pictures, which the page waits 15 s
 * for at most (web/print.ts's `PICTURE_WAIT`). A big map and a picture from
 * the web that never comes print without the picture, rather than not at all.
 */
internal class PrintPatience(private val handler: Handler, private val givesUp: Runnable) {
    private var laidOut = false

    /** The page starts loading. */
    fun start() {
        handler.postDelayed(givesUp, READY_TIMEOUT)
    }

    /** The page is laid out: its pictures have their own time, from now, once. */
    fun laidOut() {
        if (laidOut) {
            return
        }

        laidOut = true
        handler.removeCallbacks(givesUp)
        handler.postDelayed(givesUp, PICTURES_TIMEOUT)
    }

    /** The page is ready, or printing gave up on it already. */
    fun stop() {
        handler.removeCallbacks(givesUp)
    }

    companion object {
        /** How long loading a page and laying a big map out may take before printing gives up, in ms. */
        const val READY_TIMEOUT = 30_000L

        /** How long a laid-out page's pictures may take before printing gives up, in ms: the page's own wait, with room to spare. */
        const val PICTURES_TIMEOUT = 30_000L
    }
}

/**
 * Prints maps through Android's print framework, whose dialog saves PDFs too:
 * Android's own Save as PDF is there even on phones without Google's apps.
 * The map's web page ([WebPage]) is laid out for paper in a web view no one
 * sees, which says when its pictures are in: the map's own, inside the page,
 * and those from the web, over HTTPS as the map's page loads them, a YouTube
 * video's poster too. The view's print adapter then makes the pages, and the
 * view goes once the job is done with it, or with the window that printed
 * it. The page comes from a file, which the view reads as it loads: with a
 * map's pictures inside, it can be tens of megabytes, too much to keep in
 * memory.
 */
object MapPrinter {
    /** The cache's folder for pages to print, each in a file of its own until its print is done with it. */
    private const val PAGES = "print-pages"

    /** How long a page to print stays when its print never ended, as when Orbit stopped during it, in ms: a day. */
    private const val PAGE_KEPT = 24 * 60 * 60 * 1000L

    /** Where the page is, for the view: on the app's own host, where it says it's ready. */
    private const val PAGE_PATH = "/print/"

    /** The views printing now: each stays alive until its job is done with it. */
    private val printing = mutableSetOf<WebView>()

    /** The last print job, while its dialog may still be open. */
    private var latest: PrintJob? = null

    /**
     * Whether the last print job's dialog is still open, before anything was
     * printed, forgetting the job: it can't be cancelled then, and a dialog
     * closed with the window that opened it leaves it as it was, so as
     * private maps lock, MainActivity makes its window again once, and no map
     * stays over the lock. A job that has started printing goes on.
     */
    @MainThread
    fun takeOpenDialog(): Boolean {
        val job = latest ?: return false
        latest = null
        return runCatching { job.info.state == PrintJobInfo.STATE_CREATED }.getOrDefault(false)
    }

    /**
     * A new file for a page to print ([print] takes it), in the cache; pages
     * of prints that never ended go after a day.
     */
    fun pageFile(context: Context): File {
        val folder = File(context.cacheDir, PAGES)

        if (!folder.mkdirs() && !folder.isDirectory) {
            throw IOException("Couldn't make a folder for pages to print")
        }

        val now = System.currentTimeMillis()
        folder.listFiles().orEmpty().filter { now - it.lastModified() > PAGE_KEPT }.forEach { it.delete() }
        return File(folder, "${UUID.randomUUID()}.html")
    }

    /**
     * Prints `page`, a file from [pageFile] with the map's page laid out for
     * paper ([WebPage.write] with a [PrintRequest]), as `title`, and deletes
     * it once it's done with it. `then` hears true once the print dialog
     * opens, with how many pictures the print goes without, ones from the
     * web the phone couldn't reach, say; or false when the page couldn't be
     * laid out, or `goes`, asked right before the dialog opens, says it
     * mustn't: a private map that locked while its page loaded.
     */
    @MainThread
    fun print(activity: Activity, title: String, page: File, goes: () -> Boolean = { true }, then: (opened: Boolean, missing: Int) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            page.delete()
            then(false, 0)
            return
        }

        val view = WebView(activity)
        // Quiet as the map's page is: the console would put the map's words in the phone's log.
        view.webChromeClient = OrbitPage.QuietChrome(players = false)
        val handler = Handler(Looper.getMainLooper())
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        lateinit var closing: LifecycleEventObserver
        var settled = false
        printing += view

        fun release() {
            if (printing.remove(view)) {
                lifecycle?.removeObserver(closing)
                view.destroy()
                page.delete()
            }
        }

        val timeout = Runnable {
            if (!settled) {
                settled = true
                release()
                then(false, 0)
            }
        }

        val patience = PrintPatience(handler, timeout)

        // A window that closes (made again for a new language, say) takes its print dialog with it, and the job
        // never tells the adapter it's done: the view goes with the window rather than staying with no one to print.
        closing = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                settled = true
                patience.stop()
                release()
            }
        }

        lifecycle?.addObserver(closing)

        fun ready(missing: Int) {
            if (settled) {
                return
            }

            settled = true
            patience.stop()

            if (!goes()) {
                release()
                then(false, 0)
                return
            }

            val job: PrintJob? = activity.getSystemService(PrintManager::class.java).print(title, ReleasingAdapter(view.createPrintDocumentAdapter(title), ::release), null)

            // A window on its way out prints nothing, and the adapter would never hear from a job.
            if (job == null) {
                release()
                then(false, 0)
                return
            }

            latest = job
            then(true, missing)
        }

        view.settings.apply {
            javaScriptEnabled = true
            // The map's own pictures are inside the page; those from the web load from there, over HTTPS alone, as
            // the page's content policy has it, and nothing on the phone is anywhere for it to load.
            allowFileAccess = false
            allowContentAccess = false
        }

        view.webViewClient = object : WebViewClient() {
            // The page itself, read from its file as the view loads it; pictures from the web come as the page asks.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!request.isForMainFrame || request.url.host != PAGE_HOST || request.url.path != PAGE_PATH) {
                    return null
                }

                return try {
                    WebResourceResponse("text/html", "utf-8", page.inputStream())
                } catch (gone: IOException) {
                    null
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Before the dialog opens, printing gives up; after, the job fails and its adapter lets the view go.
                handler.post(timeout)
                return true
            }
        }

        // Only the page itself says it's laid out, then ready, from the origin it's loaded under.
        WebViewCompat.addWebMessageListener(view, "orbitPrint", setOf("https://$PAGE_HOST")) { _, message, _, isMainFrame, _ ->
            val missing = picturesMissing(message.data)

            if (isMainFrame && message.data == LAID_OUT) {
                patience.laidOut()
            } else if (isMainFrame && missing != null) {
                ready(missing)
            }
        }

        patience.start()
        view.loadUrl("https://$PAGE_HOST$PAGE_PATH")
    }
}

/** A web view's print adapter that lets the view go once the print job is done with it. */
private class ReleasingAdapter(private val adapter: PrintDocumentAdapter, private val release: () -> Unit) : PrintDocumentAdapter() {
    override fun onStart() {
        adapter.onStart()
    }

    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?) {
        adapter.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
    }

    override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor?, cancellationSignal: CancellationSignal?, callback: WriteResultCallback?) {
        adapter.onWrite(pages, destination, cancellationSignal, callback)
    }

    override fun onFinish() {
        adapter.onFinish()
        release()
    }
}
