package com.hyuchia.orbit.page

import android.app.Activity
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.Serializable

/** What a printed map's picture shows: web/print.ts's PrintRequest. */
@Serializable
data class PrintRequest(
    /** `focus`, an idea with its orbit, or `all`, the whole map. */
    val viewMode: String,

    /** The idea in the middle of a focused picture; the top idea without one. */
    val focus: String? = null,
)

/**
 * Prints maps through Android's print framework, whose dialog saves PDFs too:
 * Android's own Save as PDF is there even on phones without Google's apps.
 * The map's web page ([WebPage]) is laid out for paper in a web view no one
 * sees, which says when its pictures are in. The view's print adapter then
 * makes the pages, and the view goes once the job is done with it, or with
 * the window that printed it.
 */
object MapPrinter {
    /** How long laying out a big map may take before printing gives up, in ms. */
    private const val READY_TIMEOUT = 30_000L

    /** The views printing now: each stays alive until its job is done with it. */
    private val printing = mutableSetOf<WebView>()

    /**
     * Prints `html`, a page from [WebPage.build] with a [PrintRequest], as
     * `title`. `then` hears true once the print dialog opens, or false when the
     * page couldn't be laid out.
     */
    @MainThread
    fun print(activity: Activity, title: String, html: String, then: (Boolean) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            then(false)
            return
        }

        val view = WebView(activity)
        val handler = Handler(Looper.getMainLooper())
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        lateinit var closing: LifecycleEventObserver
        var settled = false
        printing += view

        fun release() {
            if (printing.remove(view)) {
                lifecycle?.removeObserver(closing)
                view.destroy()
            }
        }

        val timeout = Runnable {
            if (!settled) {
                settled = true
                release()
                then(false)
            }
        }

        // A window that closes (made again for a new language, say) takes its print dialog with it, and the job
        // never tells the adapter it's done: the view goes with the window rather than staying with no one to print.
        closing = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                settled = true
                handler.removeCallbacks(timeout)
                release()
            }
        }

        lifecycle?.addObserver(closing)

        fun ready() {
            if (settled) {
                return
            }

            settled = true
            handler.removeCallbacks(timeout)
            val job: PrintJob? = activity.getSystemService(PrintManager::class.java).print(title, ReleasingAdapter(view.createPrintDocumentAdapter(title), ::release), null)

            // A window on its way out prints nothing, and the adapter would never hear from a job.
            if (job == null) {
                release()
                then(false)
                return
            }

            then(true)
        }

        view.settings.apply {
            javaScriptEnabled = true
            // Everything the page shows is inside it: nothing loads from anywhere else.
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
        }

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Before the dialog opens, printing gives up; after, the job fails and its adapter lets the view go.
                handler.post(timeout)
                return true
            }
        }

        // Only the page itself says it's ready, from the origin it's loaded under.
        WebViewCompat.addWebMessageListener(view, "orbitPrint", setOf("https://$PAGE_HOST")) { _, message, _, isMainFrame, _ ->
            if (isMainFrame && message.data == "ready") {
                ready()
            }
        }

        handler.postDelayed(timeout, READY_TIMEOUT)
        view.loadDataWithBaseURL("https://$PAGE_HOST/print/", html, "text/html", "utf-8", null)
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
