package com.hyuchia.orbit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun linksAreReadTheWayBrowsersReadThem() {
        assertNull(linkScheme(" javascript:alert(1)"))
        assertNull(linkScheme("java\tscript:alert(1)"))
        assertEquals("https", linkScheme("  https://example.com/a\n"))
        assertEquals("https://example.com/a", cleanLink("\u0000 https://exa\tmple.com/a \n"))
        assertEquals("javascript:alert(1)", cleanLink(" java\tscript:alert(1)"))
    }
}
