package com.hyuchia.orbit.nearby

import com.hyuchia.orbit.FileWords
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.TimeUnit

class JoiningTest {
    /** What a phone in English shows. */
    private val english = FileWords(Locale.US)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mapFolder: File = Files.createTempDirectory("orbit-map").toFile()
    private val rooms = mutableListOf<BrowserPresenting>()
    private val sockets = mutableListOf<Socket>()

    /** A presentation in browsers, served over the loopback from the app's own pages. */
    private fun present(options: JoiningOptions): BrowserPresenting =
        BrowserPresenting(::asset, english, mapFolder, scope, options, preferredPort = 0, policy = anyone).also {
            rooms += it
            it.start()
        }

    private fun asset(name: String): String = File("src/main/assets/map/$name").readText()

    @After
    fun stop() {
        sockets.forEach { runCatching { it.close() } }
        rooms.forEach { it.stop() }
        scope.cancel()
        mapFolder.deleteRecursively()
    }

    private val BrowserPresenting.pagePath get() = "/$code/"
    private val BrowserPresenting.joinPath get() = "/$code/join"
    private val BrowserPresenting.statusPath get() = "/$code/status"

    /** The status of an answer a test made without a connection. */
    private val Response.status get() = (this as Response.Bytes).status

    @Test
    fun withNothingToAskOpeningThePageJoinsAtOnce() {
        val room = present(JoiningOptions(askNames = false))
        val browser = Browser(room.port)
        val page = browser.get(room.pagePath)
        assertEquals(200, page.status)
        assertTrue(page.body.contains("browser.js"))
        val cookie = page.header("set-cookie")!!
        assertTrue(cookie, Regex("orbit_presentation=[0-9a-f]{32}; .*").matches(cookie))
        listOf("HttpOnly", "SameSite=Strict", "Path=${room.pagePath}").forEach { assertTrue(it, cookie.split("; ").contains(it)) }
        assertEquals(listOf("Guest 1"), room.joining.members.value.map { it.name })
        assertEquals(200, browser.get("/${room.code}/browser.js").status)
        assertEquals("""{"state":"joined"}""", browser.get(room.statusPath).body)

        // Without the session, nothing in the room opens.
        listOf("browser.js", "events", "media/picture.png").forEach { part ->
            assertEquals(part, 403, request(room.port, "/${room.code}/$part").status)
        }

        assertEquals("""{"state":"left"}""", request(room.port, room.statusPath).body)
    }

    @Test
    fun aSessionWorksOnlyFromTheAddressThatJoined() {
        assumeTrue("Needs another loopback address to connect from", canBindFrom("127.0.0.2"))
        val room = present(JoiningOptions(askNames = false))
        val browser = Browser(room.port)
        browser.get(room.pagePath)
        val stolen = browser.cookieHeaders.single()
        assertEquals(200, request(room.port, "/${room.code}/browser.js", headers = mapOf("Cookie" to stolen)).status)
        assertEquals(403, request(room.port, "/${room.code}/browser.js", headers = mapOf("Cookie" to stolen), from = "127.0.0.2").status)
        // Nor does a session for another room, or one made up.
        assertEquals(403, request(room.port, "/${room.code}/browser.js", headers = mapOf("Cookie" to "orbit_presentation=${"0".repeat(32)}")).status)
    }

    @Test
    fun namesAreAskedForAndCleanedAndEmptyOnesAreGuests() {
        val room = present(JoiningOptions(askNames = true))
        val ana = Browser(room.port)
        val form = ana.get(room.pagePath)
        assertEquals(200, form.status)
        assertTrue(form.body.contains("Your name"))
        assertTrue(form.body.contains("action=\"join\""))
        assertFalse(form.body.contains("name=\"pin\""))
        assertFalse(form.body.contains("id=\"code\""))
        assertFalse(form.body.contains("<!--"))
        assertNull(form.header("set-cookie"))
        assertTrue(room.joining.members.value.isEmpty())

        val joined = ana.post(room.joinPath, mapOf("name" to "  Ana‮\u0007  "))
        assertEquals(303, joined.status)
        assertEquals(room.pagePath, joined.header("location"))
        assertTrue(joined.header("set-cookie")!!.contains("HttpOnly"))
        assertTrue(ana.get(room.pagePath).body.contains("browser.js"))

        Browser(room.port).post(room.joinPath, mapOf("name" to ""))
        Browser(room.port).post(room.joinPath, mapOf("name" to " ​ "))
        Browser(room.port).post(room.joinPath, emptyMap())
        assertEquals(listOf("Ana", "Guest 1", "Guest 2", "Guest 3"), room.joining.members.value.map { it.name })

        assertEquals("a".repeat(40), run {
            Browser(room.port).post(room.joinPath, mapOf("name" to "a".repeat(100)))
            room.joining.members.value.last().name
        })
    }

    @Test
    fun aPinIsNeededAndWrongOnesMakeADeviceWait() {
        assumeTrue("Needs another loopback address to connect from", canBindFrom("127.0.0.2"))
        val room = present(JoiningOptions(askNames = true, requirePin = true))
        val pin = room.joining.pin.value!!
        assertTrue(Regex("[0-9]{4}").matches(pin))
        val form = Browser(room.port).get(room.pagePath)
        assertTrue(form.body.contains("name=\"pin\""))
        assertTrue(form.body.contains("The PIN on the presenter's phone"))
        // The name comes first.
        assertTrue(form.body.contains("value=\"\" autofocus>"))
        assertFalse(form.body.contains("required autofocus>"))
        assertFalse("The PIN is never in the page", form.body.contains(">$pin<") || form.body.contains("\"$pin\""))

        val guesser = Browser(room.port)
        val wrong = if (pin == "0000") "1111" else "0000"
        val first = guesser.post(room.joinPath, mapOf("name" to "<Eve>", "pin" to wrong))
        assertEquals(403, first.status)
        assertTrue(first.body.contains("That isn't the PIN on the presenter's phone."))
        // What was typed comes back as text, never as the page's own markup, and the PIN is next.
        assertTrue(first.body.contains("value=\"&lt;Eve&gt;\">"))
        assertTrue(first.body.contains("required autofocus>"))
        repeat(3) { assertEquals(403, guesser.post(room.joinPath, mapOf("pin" to wrong)).status) }
        val locked = guesser.post(room.joinPath, mapOf("pin" to wrong))
        assertEquals(429, locked.status)
        assertTrue(locked.body.contains("Too many wrong PINs. Try again in a minute, with the PIN on the presenter's phone."))
        // Even the right PIN waits now, and the phone hears of it.
        assertEquals(429, guesser.post(room.joinPath, mapOf("pin" to pin)).status)
        assertTrue(room.joining.pinLockedOut.value > 0)
        assertTrue(room.joining.members.value.isEmpty())

        // Another device isn't kept waiting.
        val ana = Browser(room.port, from = "127.0.0.2")
        assertEquals(303, ana.post(room.joinPath, mapOf("name" to "Ana", "pin" to pin)).status)
        // A new PIN is for whoever joins next: Ana stays in.
        room.joining.replacePin()
        val next = room.joining.pin.value!!

        if (next != pin) {
            assertEquals(403, Browser(room.port, from = "127.0.0.2").post(room.joinPath, mapOf("pin" to pin)).status)
        }

        assertEquals(200, ana.get("/${room.code}/browser.js").status)
        assertEquals(listOf("Ana"), room.joining.members.value.map { it.name })

        // Turned off, the PIN goes, and so does the need for it.
        room.joining.change(room.joining.options.value.copy(requirePin = false))
        assertNull(room.joining.pin.value)
        assertEquals(303, Browser(room.port, from = "127.0.0.2").post(room.joinPath, mapOf("name" to "Bo")).status)
    }

    @Test
    fun theLimitTurnsAwayOneTooMany() {
        val room = present(JoiningOptions(askNames = false, limit = 1))
        val first = Browser(room.port)
        assertEquals(200, first.get(room.pagePath).status)
        val second = Browser(room.port)
        val full = second.get(room.pagePath)
        assertEquals(403, full.status)
        assertTrue(full.body.contains("This presentation is full."))
        assertTrue(full.body.contains("Try again"))
        assertEquals("""{"state":"full"}""", second.get(room.statusPath).body)
        assertEquals("""{"state":"joined"}""", first.get(room.statusPath).body)
        assertEquals(listOf("Guest 1"), room.joining.members.value.map { it.name })

        room.joining.change(room.joining.options.value.copy(limit = 5))
        assertEquals(200, second.get(room.pagePath).status)
        assertEquals(2, room.joining.members.value.size)

        // No limit is still the server's.
        room.joining.change(room.joining.options.value.copy(limit = 0))
        val capped = Joining("123456", RoomKind.Presentation, JoiningOptions(askNames = false), template = { asset("join.html") }, words = english, hardLimit = 2)
        val guests = (1..3).map { runBlocking { capped.serve(get("/123456/", from = "10.0.0.$it"), emptyList()) { _, _ -> Response.text(200, "in") } }.status }
        assertEquals(listOf(200, 200, 403), guests)
    }

    @Test
    fun withNoOneNewLetInThoseInStay() {
        val room = present(JoiningOptions(askNames = false))
        val inside = Browser(room.port)
        inside.get(room.pagePath)
        room.joining.change(room.joining.options.value.copy(open = false))
        assertEquals(200, inside.get("/${room.code}/browser.js").status)

        val late = Browser(room.port)
        val closed = late.get(room.pagePath)
        assertEquals(403, closed.status)
        assertTrue(closed.body.contains("The presenter isn't letting anyone new in."))
        assertEquals("""{"state":"closed"}""", late.get(room.statusPath).body)
        assertEquals(403, late.post(room.joinPath, emptyMap()).status)
        assertEquals(1, room.joining.members.value.size)

        room.joining.change(room.joining.options.value.copy(open = true))
        assertEquals(200, late.get(room.pagePath).status)
    }

    @Test
    fun removingSomeoneEndsTheirStreamAndKeepsTheirAddressOut() {
        val room = present(JoiningOptions(askNames = true))
        val ana = Browser(room.port)
        ana.post(room.joinPath, mapOf("name" to "Ana"))
        val (socket, lines) = ana.follow("/${room.code}/events")
        sockets += socket
        assertEquals("HTTP/1.1 200 OK", lines.head().first())
        assertTrue(eventually { room.watching.value == 1 })

        room.remove(room.joining.members.value.single())
        assertEquals("data: {\"type\":\"removed\"}", lines.readLine())
        assertEquals("", lines.readLine())
        assertNull(lines.readLine())
        assertTrue(eventually { room.watching.value == 0 })
        assertTrue(room.joining.members.value.isEmpty())

        assertEquals(403, ana.get("/${room.code}/browser.js").status)
        assertEquals(403, ana.get("/${room.code}/events").status)
        assertEquals("""{"state":"removed"}""", ana.get(room.statusPath).body)
        // From the same address, joining again is refused, with or without the old cookie.
        val again = Browser(room.port).post(room.joinPath, mapOf("name" to "Ana again"))
        assertEquals(403, again.status)
        assertTrue(again.body.contains("The presenter removed you."))
        assertFalse(again.body.contains("Try again"))
        assertTrue(room.joining.members.value.isEmpty())

        assumeTrue("Needs another loopback address to connect from", canBindFrom("127.0.0.2"))
        assertEquals(303, Browser(room.port, from = "127.0.0.2").post(room.joinPath, mapOf("name" to "Bo")).status)
    }

    @Test
    fun aBrowserThatJoinsCatchesUpWithThePresentationSoFar() {
        val room = present(JoiningOptions(askNames = false))
        room.audience.send(AudienceCommand.Show("# Talk", AppTheme(dark = false, tokens = emptyMap()), focus = "talk"))
        room.audience.send(AudienceCommand.Captions(false))
        room.audience.send(AudienceCommand.Step("talk", 0, 3))
        val browser = Browser(room.port)
        browser.get(room.pagePath)
        val (socket, lines) = browser.follow("/${room.code}/events")
        sockets += socket
        lines.head()
        assertTrue(lines.readLine().startsWith("data: {\"type\":\"show\""))
        assertEquals("", lines.readLine())
        assertEquals("data: {\"type\":\"captions\",\"on\":false}", lines.readLine())
        assertEquals("", lines.readLine())
        assertEquals("data: {\"type\":\"step\",\"id\":\"talk\",\"index\":0,\"total\":3}", lines.readLine())
        room.audience.send(AudienceCommand.Step("next", 1, 3))
        assertEquals("", lines.readLine())
        assertEquals("data: {\"type\":\"step\",\"id\":\"next\",\"index\":1,\"total\":3}", lines.readLine())
    }

    @Test
    fun browsersAlreadyInAreNeverKeptOutByWrongCodes() {
        val room = present(JoiningOptions(askNames = false))
        val inside = Browser(room.port)
        inside.get(room.pagePath)
        // Wrong codes from its own address make that address wait...
        repeat(5) { inside.get("/000000/") }
        assertEquals(429, Browser(room.port).get(room.pagePath).status)
        // ...but the browser already in carries on.
        assertEquals(200, inside.get("/${room.code}/browser.js").status)
        val (socket, lines) = inside.follow("/${room.code}/events")
        sockets += socket
        assertEquals("HTTP/1.1 200 OK", lines.head().first())

        // So it does once wrong codes from address after address keep every new device waiting.
        assumeTrue("Needs other loopback addresses to connect from", canBindFrom("127.0.0.21") && canBindFrom("127.0.0.30"))

        for (device in 2..21) {
            repeat(5) { request(room.port, "/000000/", from = "127.0.0.$device") }
        }

        assertEquals(429, Browser(room.port, from = "127.0.0.30").get(room.pagePath).status)
        assertEquals(200, inside.get("/${room.code}/browser.js").status)
        assertEquals("""{"state":"joined"}""", inside.get(room.statusPath).body)
    }

    @Test
    fun aBrowserStillFollowingAnEndedPresentationHearsItEndedWithoutGuessing() {
        val first = present(JoiningOptions(askNames = false))
        val browser = Browser(first.port)
        browser.get(first.pagePath)
        first.stop()

        // The next presentation, at a new code: the old page keeps trying its events, as browsers do.
        val next = present(JoiningOptions(askNames = false))
        val old = browser.cookieHeaders.single()

        repeat(12) {
            assertEquals(403, request(next.port, "/${first.code}/events", headers = mapOf("Cookie" to old)).status)
        }

        assertEquals("{\"state\":\"ended\"}", request(next.port, "/${first.code}/status", headers = mapOf("Cookie" to old)).body)
        assertTrue(request(next.port, first.pagePath).body.contains("The presentation ended."))
        // None of it counted as guessing: the same device joins the new one at once.
        assertEquals(200, request(next.port, next.pagePath).status)
        assertEquals(0L, next.lockedOut.value)
    }

    @Test
    fun aPageStillTryingARoomThatsGoneIsNeverCountedAsGuessing() {
        val room = present(JoiningOptions(askNames = false))

        // What a page loads by itself, at a code that isn't there, reads as it would at the right code without joining.
        listOf("events", "browser.js", "media/picture.png").forEach { part ->
            val gone = request(room.port, "/000001/$part")
            val here = request(room.port, "/${room.code}/$part")
            assertEquals(part, here.status, gone.status)
            assertEquals(part, here.body, gone.body)
        }

        repeat(20) { request(room.port, "/000001/events") }
        assertEquals(0L, room.lockedOut.value)
        // Asked, the room's status says it ended, and that one counts.
        assertEquals("{\"state\":\"ended\"}", request(room.port, "/000001/status").body)
        assertEquals(200, request(room.port, room.pagePath).status)
        // Typing codes still counts: five wrong ones, and this device waits.
        repeat(5) { request(room.port, "/000001/") }
        assertEquals(429, request(room.port, room.pagePath).status)
    }

    @Test
    fun aFormPostedFromAnotherSiteIsRefused() {
        val room = present(JoiningOptions(askNames = true))
        val browser = Browser(room.port)
        assertEquals(403, browser.post(room.joinPath, mapOf("name" to "Mallory"), mapOf("Sec-Fetch-Site" to "cross-site")).status)
        assertEquals(403, browser.post(room.joinPath, mapOf("name" to "Mallory"), mapOf("Sec-Fetch-Site" to "same-site")).status)
        assertTrue(room.joining.members.value.isEmpty())
        assertEquals(303, browser.post(room.joinPath, mapOf("name" to "Ana"), mapOf("Sec-Fetch-Site" to "same-origin")).status)
    }

    @Test
    fun theCodePageStillAsksForTheCode() {
        val room = present(JoiningOptions())
        val page = request(room.port, "/")
        assertEquals(200, page.status)
        assertTrue(page.body.contains("id=\"code\""))
        assertTrue(page.body.contains("The code on the presenter"))
        assertFalse(page.body.contains("Your name"))
        assertFalse(page.body.contains("<!--"))
        val wrong = request(room.port, "/000000/")
        assertTrue(wrong.body.contains("That code isn"))
        // Its inline script runs under the page's policy.
        val policy = page.header("content-security-policy")!!
        inlineScripts(page.body).forEach { assertTrue(policy.contains(scriptHash(it))) }
    }

    @Test
    fun peopleWhoStoppedFollowingAreAwayAfterAMinuteAndBackWhenTheirBrowserIs() {
        var now = 1_000_000L
        val joining = Joining("123456", RoomKind.Presentation, JoiningOptions(askNames = true, limit = 1), template = { asset("join.html") }, words = english, clock = { now })
        val page: suspend (Member, List<String>) -> Response = { _, _ -> Response.text(200, "in") }
        val joined = runBlocking { joining.serve(post("/123456/join", "name=Ana"), listOf("join"), page) } as Response.Bytes
        val cookie = joined.headers.getValue("Set-Cookie").substringBefore(';')
        val ana = joining.members.value.single()
        joining.streamOpened(ana.id)
        now += 10 * 60_000
        joining.tidy()
        assertEquals(listOf(ana), joining.members.value)

        // Her phone sleeps: for a minute she's still in, and the room still full.
        joining.streamClosed(ana.id)
        now += 59_000
        joining.tidy()
        assertEquals(listOf(ana), joining.members.value)
        assertEquals(403, runBlocking { joining.serve(post("/123456/join", "name=Bo", from = "10.0.0.9"), listOf("join"), page) }.status)

        // Then she's away: her place is free, and Bo takes it.
        now += 2_000
        joining.tidy()
        assertTrue(joining.members.value.isEmpty())
        assertEquals(303, runBlocking { joining.serve(post("/123456/join", "name=Bo", from = "10.0.0.9"), listOf("join"), page) }.status)
        val bo = joining.members.value.single()
        assertEquals("Bo", bo.name)

        // Back while the room is full, her browser hears so, and keeps her session.
        val full = runBlocking { joining.serve(get("/123456/status", cookie = cookie), listOf("status"), page) } as Response.Bytes
        assertEquals("{\"state\":\"full\"}", full.body.decodeToString())
        assertEquals(403, runBlocking { joining.serve(get("/123456/events", cookie = cookie), listOf("events"), page) }.status)

        // With room again, she's in at once, without the form, as when she joined.
        joining.change(joining.options.value.copy(limit = 2))
        assertEquals(200, runBlocking { joining.serve(get("/123456/events", cookie = cookie), listOf("events"), page) }.status)
        assertEquals(setOf("Ana", "Bo"), joining.members.value.map { it.name }.toSet())
        assertEquals(ana.joined, joining.members.value.first { it.name == "Ana" }.joined)

        // After half an hour away, a session is forgotten, and its browser joins again like anyone.
        joining.streamOpened(bo.id)
        now += 31 * 60_000
        joining.tidy()
        val left = runBlocking { joining.serve(get("/123456/status", cookie = cookie), listOf("status"), page) } as Response.Bytes
        assertEquals("{\"state\":\"left\"}", left.body.decodeToString())
        assertEquals(listOf("Bo"), joining.members.value.map { it.name })
    }

    @Test
    fun aSharedMapSpeaksOfTheOtherDevice() {
        val joining = Joining("654321", RoomKind.Share, JoiningOptions(askNames = false, requirePin = true), template = { asset("join.html") }, words = english)
        val form = runBlocking { joining.serve(get("/654321/"), emptyList()) { _, _ -> Response.text(200, "map") } } as Response.Bytes
        val page = form.body.decodeToString()
        assertTrue(page.contains("Open the map"))
        assertTrue(page.contains("The PIN on the other device"))
        assertFalse(page.contains("Your name"))
        assertEquals(403, runBlocking { joining.serve(get("/654321/map.zip"), listOf("map.zip")) { _, _ -> Response.text(200, "zip") } }.status)

        joining.end()
        val ended = runBlocking { joining.serve(get("/654321/"), emptyList()) { _, _ -> Response.text(200, "map") } } as Response.Bytes
        assertEquals(410, ended.status)
        assertTrue(ended.body.decodeToString().contains("The other device stopped sharing this map."))
        val status = runBlocking { joining.serve(get("/654321/status"), listOf("status")) { _, _ -> Response.notFound() } } as Response.Bytes
        assertEquals("""{"state":"ended"}""", status.body.decodeToString())
    }

    @Test
    fun theJoinPageSpeaksThePresentersLanguage() {
        val joining = Joining("123456", RoomKind.Presentation, JoiningOptions(askNames = true), template = { asset("join.html") }, words = SpanishWords)
        val form = runBlocking { joining.serve(get("/123456/"), emptyList()) { _, _ -> Response.text(200, "room") } } as Response.Bytes
        val page = form.body.decodeToString()
        assertTrue(page.contains("<html lang=\"es-MX\">"))
        assertTrue(page.contains("Unirse a la presentación"))
        assertTrue(page.contains(">Tu nombre</label>"))
        assertFalse(page.contains("<!--"))

        val english = Joining("123456", RoomKind.Presentation, JoiningOptions(askNames = true), template = { asset("join.html") }, words = english)
        val englishPage = (runBlocking { english.serve(get("/123456/"), emptyList()) { _, _ -> Response.text(200, "room") } } as Response.Bytes).body.decodeToString()
        assertTrue(englishPage.contains("<html lang=\"en\">"))
        assertTrue(englishPage.contains(">Your name</label>"))

        val code = codePage(asset("join.html"), 200, SpanishWords.string(R.string.nearby_code_on_the_presenter_phone), "", SpanishWords.language) as Response.Bytes
        val codeBody = code.body.decodeToString()
        assertTrue(codeBody.contains("<html lang=\"es-MX\">"))
        assertTrue(codeBody.contains("El código que aparece en el teléfono de quien presenta"))
        assertFalse(codeBody.contains("<!--"))
    }

    @Test
    fun theLimitNeverPassesTheServers() {
        val joining = Joining("123456", RoomKind.Presentation, JoiningOptions(limit = 500), template = { "" }, words = english, hardLimit = 128)
        assertEquals(128, joining.options.value.limit)
        joining.change(JoiningOptions(limit = -3))
        assertEquals(0, joining.options.value.limit)
    }

    @Test
    fun joiningTimesReadPlainly() {
        val joined = 1_000_000L
        assertEquals("Joined just now", joinedAgo(joined, joined + 59_000, english))
        assertEquals("Joined 1 minute ago", joinedAgo(joined, joined + 60_000, english))
        assertEquals("Joined 3 minutes ago", joinedAgo(joined, joined + 3 * 60_000 + 30_000, english))
        assertEquals("Joined 1 hour ago", joinedAgo(joined, joined + 61 * 60_000, english))
        assertEquals("Joined 2 hours ago", joinedAgo(joined, joined + 2 * 60 * 60_000, english))
        assertEquals("Joined just now", joinedAgo(joined, joined - 5_000, english))
    }

    @Test
    fun sectionsAreKeptOrLeftOut() {
        val template = "a<!-- x -->b<!-- /x -->c"
        assertEquals("abc", section(template, "x", keep = true))
        assertEquals("ac", section(template, "x", keep = false))
        assertEquals(template, section(template, "y", keep = false))
    }

    private fun get(path: String, from: String = "10.0.0.2", cookie: String? = null) =
        Request("GET", path, emptyMap(), cookie?.let { mapOf("cookie" to it) }.orEmpty(), ByteArray(0), from)

    private fun post(path: String, form: String, from: String = "10.0.0.2") =
        Request("POST", path, emptyMap(), mapOf("content-type" to "application/x-www-form-urlencoded"), form.toByteArray(), from)

    private companion object {
        fun canBindFrom(address: String): Boolean = runCatching { Socket().use { it.bind(InetSocketAddress(address, 0)) } }.isSuccess

        fun eventually(condition: () -> Boolean): Boolean {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)

            while (System.nanoTime() < until) {
                if (condition()) {
                    return true
                }

                Thread.sleep(10)
            }

            return condition()
        }
    }
}
