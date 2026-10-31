package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun webMailCallAndTextLinksOpen() {
        assertEquals("https", linkScheme("https://example.com"))
        assertEquals("http", linkScheme("HTTP://EXAMPLE.COM"))
        assertEquals("mailto", linkScheme("mailto:team@example.com"))
        assertEquals("tel", linkScheme("tel:+15551234"))
        assertEquals("sms", linkScheme("sms:+15551234"))
    }

    @Test
    fun scriptsFilesAndOtherAppsDoNot() {
        for (link in listOf("javascript:alert(1)", "intent://scan#Intent;end", "file:///sdcard/a.txt", "content://contacts/people", "data:text/html,x", "relative/page.html")) {
            assertNull(link, linkScheme(link))
        }
    }

    @Test
    fun picturesAtHttpAddressesLoadOverHttpsAndOthersAsTheyAre() {
        assertEquals("https://example.com/tokyo.jpg", pictureAddress("http://example.com/tokyo.jpg"))
        assertEquals("https://EXAMPLE.COM/tokyo.jpg", pictureAddress(" HTTP://EXAMPLE.COM/tokyo.jpg\n"))
        assertEquals("https://example.com/tokyo.jpg", pictureAddress("https://example.com/tokyo.jpg"))
        assertEquals("data:image/png;base64,iVBORw0KGgo=", pictureAddress("data:image/png;base64,iVBORw0KGgo="))
        assertEquals("javascript:alert(1)", pictureAddress("javascript:alert(1)"))
    }

    @Test
    fun picturesLoadFromTheWebOrInsideTheMapAndNothingElse() {
        for (address in listOf("https://example.com/tokyo.jpg", "HTTP://EXAMPLE.COM/tokyo.jpg", " https://example.com/a.png\n", "data:image/png;base64,iVBORw0KGgo=")) {
            assertTrue(address, isWebPicture(address))
        }

        // Orbit's own files, other apps', and anything that isn't a picture.
        for (address in listOf("file:///data/user/0/com.hyuchia.orbit/files/maps/diary/media/photo.jpg", "content://com.hyuchia.orbit.files/maps/diary/map.md", "content://media/external/images/1", "file:///android_asset/map/index.html", "javascript:alert(1)", "data:text/html,x", "ftp://example.com/a.jpg", "android.resource://com.hyuchia.orbit/raw/x")) {
            assertFalse(address, isWebPicture(address))
        }
    }

    @Test
    fun linksAreReadTheWayBrowsersReadThem() {
        assertNull(linkScheme(" javascript:alert(1)"))
        assertNull(linkScheme("java\tscript:alert(1)"))
        assertEquals("https", linkScheme("  https://example.com/a\n"))
        assertEquals("https://example.com/a", cleanLink("\u0000 https://exa\tmple.com/a \n"))
        assertEquals("javascript:alert(1)", cleanLink(" java\tscript:alert(1)"))
    }
}
