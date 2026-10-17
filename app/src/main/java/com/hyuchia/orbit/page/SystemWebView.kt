package com.hyuchia.orbit.page

import android.content.Context
import android.content.Intent
import android.webkit.WebSettings
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat

/**
 * The phone's WebView, which draws every map: Android System WebView on most
 * phones, Huawei's own on Huawei phones. Orbit's page needs Chromium
 * [MINIMUM_CHROMIUM] or later (see Orbit's README, "Browsers"). With an
 * older one, Orbit says what to update instead of showing maps it can't draw.
 */
data class SystemWebView(
    /** Its name, as the phone lists it: "Android System WebView", "Huawei WebView". */
    val name: String,

    val packageName: String?,

    /** The Chromium it runs, from its user agent: its package's version doesn't say (Huawei's counts its own). */
    val chromium: Int?,
) {
    /** Whether it can draw maps. A version that can't be read gets the benefit of the doubt. */
    val drawsMaps: Boolean get() = chromium == null || chromium >= MINIMUM_CHROMIUM

    /** Its page in the phone's app store, when the phone has one that opens it: Google Play, AppGallery or another. */
    fun storePage(context: Context): Intent? {
        val name = packageName ?: return null
        return Intent(Intent.ACTION_VIEW, "market://details?id=$name".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    companion object {
        /** The oldest Chromium Orbit's page draws in. */
        const val MINIMUM_CHROMIUM = 99

        @Volatile
        private var known: SystemWebView? = null

        /** The phone's WebView, read once: reading it starts the WebView, so it's done where pages are made. */
        fun of(context: Context): SystemWebView = known ?: read(context.applicationContext).also { known = it }

        private fun read(context: Context): SystemWebView {
            val agent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull().orEmpty()
            val installed = WebViewCompat.getCurrentWebViewPackage(context)
            val name = installed?.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: "Android System WebView"
            return SystemWebView(name, installed?.packageName, chromiumVersion(agent))
        }
    }
}

/** The Chromium in a user agent, by its major version: `Chrome/99.0.4844.88` is 99. */
internal fun chromiumVersion(userAgent: String): Int? = Regex("""Chrome/(\d+)""").find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
