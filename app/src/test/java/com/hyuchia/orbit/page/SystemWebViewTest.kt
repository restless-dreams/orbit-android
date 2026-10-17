package com.hyuchia.orbit.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWebViewTest {
    @Test
    fun `the Chromium version comes from the user agent`() {
        val google = "Mozilla/5.0 (Linux; Android 15; sdk_gphone64_x86_64 Build/AP3A.241105.008; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.122 Mobile Safari/537.36"
        // Huawei's WebView numbers its package on its own (14.0.0.331) but says its Chromium here.
        val huawei = "Mozilla/5.0 (Linux; Android 10; ELS-AN10 Build/HUAWEIELS-AN10; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/99.0.4844.88 Mobile Safari/537.36"
        assertEquals(141, chromiumVersion(google))
        assertEquals(99, chromiumVersion(huawei))
        assertNull(chromiumVersion(""))
    }

    @Test
    fun `maps draw from the minimum Chromium on, and when the version can't be read`() {
        assertTrue(SystemWebView("Huawei WebView", "com.huawei.webview", 99).drawsMaps)
        assertFalse(SystemWebView("Android System WebView", "com.android.webview", 74).drawsMaps)
        assertTrue(SystemWebView("Android System WebView", null, null).drawsMaps)
    }
}
