package com.hyuchia.orbit.nearby

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.escapeHtml
import com.hyuchia.orbit.page.fill
import com.hyuchia.orbit.page.htmlIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * How long someone following a presentation stays listed and counted once
 * their browser stopped following: long enough for a reload, or a
 * connection that dropped, to come back. Then they're away: their place is
 * free, and their session takes them back in when their browser returns,
 * as a phone that slept during the talk does, while there's room.
 */
private const val AWAY_MILLISECONDS = 60_000L
/** How long an away session is kept for its browser to come back. */
private const val FORGET_MILLISECONDS = 30 * 60_000L
/** The most sessions a room keeps, in and away together: past it, the longest away are forgotten. */
private const val SESSIONS_KEPT = 512

/** The bytes in a session's token: 128 bits, never guessed. */
private const val TOKEN_BYTES = 16

private val random = SecureRandom()

/** Where people join: a presentation followed in browsers, or a map opened on a computer. */
enum class RoomKind { Presentation, Share }

/** The choices for "How many at once": 0 is no limit beyond what the phone can serve. */
val JOINING_LIMITS = listOf(0, 1, 5, 10, 25, 50, 100)

/** Who may join a room, and what joining asks of them. */
data class JoiningOptions(
    /** Joining asks for a name; without one, people are Guest 1, Guest 2 and so on. */
    val askNames: Boolean = true,

    /** Joining takes a 4-digit PIN shown on the phone, besides the code in the address. */
    val requirePin: Boolean = false,

    /** How many may be in at once; 0 for as many as the phone can serve. */
    val limit: Int = 0,

    /** Whether anyone new may join. People already in stay either way. */
    val open: Boolean = true,
)

/** Someone in a room: the name they gave, or a guest's; the address they joined from; and when, in milliseconds since 1970. */
data class Member(val id: Long, val name: String, val address: String, val joined: Long)

/** Where a browser stands with a room, as `/<code>/status` says: so a page can tell why it was turned away. */
enum class RoomState(val json: String) {
    Joined("joined"),
    Removed("removed"),
    Full("full"),
    Closed("closed"),
    Ended("ended"),

    /** Not in, but free to join: a session that ended while its browser was away, or none yet. */
    Left("left"),
}

/** What joining says to people, in a presentation or a shared map: each string's resource. */
private class Wording(
    @StringRes val title: Int,
    @StringRes val button: Int,
    @StringRes val pinLabel: Int,
    @StringRes val wrongPin: Int,

    /** Too many wrong PINs: when to try again, and with which PIN. */
    @PluralsRes val pinWait: Int,

    @StringRes val full: Int,
    @StringRes val closed: Int,
    @StringRes val removed: Int,
    @StringRes val ended: Int,
)

private fun wording(kind: RoomKind): Wording = when (kind) {
    RoomKind.Presentation -> Wording(
        title = R.string.nearby_join_the_presentation,
        button = R.string.nearby_join,
        pinLabel = R.string.nearby_pin_on_the_presenter_phone,
        wrongPin = R.string.nearby_wrong_pin_for_the_presentation,
        pinWait = R.plurals.nearby_too_many_wrong_pins_for_the_presentation,
        full = R.string.nearby_presentation_is_full,
        closed = R.string.nearby_presenter_is_not_letting_anyone_new_in,
        removed = R.string.nearby_presenter_removed_you,
        ended = R.string.nearby_presentation_ended,
    )
    RoomKind.Share -> Wording(
        title = R.string.nearby_open_the_map,
        button = R.string.common_open,
        pinLabel = R.string.nearby_pin_on_the_other_device,
        wrongPin = R.string.nearby_wrong_pin_for_the_shared_map,
        pinWait = R.plurals.nearby_too_many_wrong_pins_for_the_shared_map,
        full = R.string.nearby_shared_map_is_full,
        closed = R.string.nearby_other_device_is_not_letting_anyone_new_in,
        removed = R.string.nearby_other_device_removed_you,
        ended = R.string.nearby_other_device_stopped_sharing,
    )
}

/**
 * Who's in a room, a presentation or a shared map at a code, and how
 * others get in. Joining gives a browser a session: a random token in a
 * cookie for the room's path alone, which only works from the address it
 * joined from. Everything in the room but joining needs one.
 *
 * With nothing to ask, opening the room's page joins at once. Otherwise
 * the page is a form, for a name, and the PIN shown on the phone when the
 * room takes one; wrong PINs make a device wait, as wrong codes do. The
 * phone can cap how many are in at once, stop letting anyone new in, and
 * remove someone: their session ends, and their address can't join again.
 *
 * `template` is the join page (web/join.html), and `words` what it says,
 * in their language.
 */
class Joining(
    val code: String,
    val kind: RoomKind,
    options: JoiningOptions,
    private val template: () -> String,
    private val words: Words,
    private val hardLimit: Int = ServerLimits().connections,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Session(val token: String, val member: Member, var streams: Int, var awaySince: Long, var away: Boolean = false)

    private sealed interface Admission {
        class In(val token: String, val member: Member) : Admission
        class Out(val state: RoomState) : Admission
    }

    private val wording = wording(kind)
    private val cookie = if (kind == RoomKind.Presentation) "orbit_presentation" else "orbit_share"
    private val lock = Any()
    private val sessions = LinkedHashMap<String, Session>()
    private val removed = HashSet<String>()
    private var guests = 0
    private var nextId = 1L
    private var ended = false
    private val pinGuard = CodeGuard(clock, together = null)
    private val currentOptions = MutableStateFlow(options.limited())
    private val currentPin = MutableStateFlow(if (options.requirePin) newPin() else null)
    private val currentMembers = MutableStateFlow<List<Member>>(emptyList())

    val options: StateFlow<JoiningOptions> = currentOptions.asStateFlow()

    /** The PIN to join with, while the room takes one. */
    val pin: StateFlow<String?> = currentPin.asStateFlow()

    /** Who's in, first to join first. */
    val members: StateFlow<List<Member>> = currentMembers.asStateFlow()

    /** When a device was last kept from guessing the PIN, or 0: for the phone to say so. */
    val pinLockedOut: StateFlow<Long> = pinGuard.lockedOut

    /** New choices: they apply to whoever joins next, and never send anyone already in away. */
    fun change(options: JoiningOptions) {
        val next = options.limited()
        currentOptions.value = next

        if (next.requirePin && currentPin.value == null) {
            currentPin.value = newPin()
        }

        if (!next.requirePin) {
            currentPin.value = null
        }
    }

    /** A new PIN for whoever joins next: people already in stay. */
    fun replacePin() {
        if (currentOptions.value.requirePin) {
            currentPin.value = newPin()
        }
    }

    private fun JoiningOptions.limited() = copy(limit = limit.coerceIn(0, hardLimit))

    /**
     * Removes someone: their session ends, and so does any other from their
     * address, which can't join this room again. Returns the members who left.
     */
    fun remove(id: Long): Set<Long> = synchronized(lock) {
        val address = sessions.values.firstOrNull { it.member.id == id }?.member?.address ?: return emptySet()
        removed += address
        val gone = sessions.values.filter { it.member.address == address }
        gone.forEach { sessions.remove(it.token) }
        publish()
        gone.map { it.member.id }.toSet()
    }

    /** Whether someone is still in. */
    fun has(id: Long): Boolean = synchronized(lock) { sessions.values.any { it.member.id == id } }

    /** The room is over: nobody is in, and everyone who asks hears it ended. */
    fun end() {
        synchronized(lock) {
            ended = true
            sessions.clear()
            publish()
        }
    }

    /** A member's browser started following the presentation. */
    fun streamOpened(id: Long) {
        synchronized(lock) {
            session(id)?.let { it.streams += 1 }
        }
    }

    /** A member's browser stopped following, on its own or because its connection dropped. */
    fun streamClosed(id: Long) {
        synchronized(lock) {
            val session = session(id) ?: return
            session.streams = maxOf(0, session.streams - 1)

            if (session.streams == 0) {
                session.awaySince = clock()
            }
        }
    }

    /** Marks people whose browsers stopped following a presentation a while ago as away, so their places are free. */
    fun tidy() {
        synchronized(lock) {
            tidyHolding()
        }
    }

    private fun tidyHolding() {
        if (kind != RoomKind.Presentation) {
            return
        }

        val now = clock()
        val leaving = sessions.values.filter { !it.away && it.streams == 0 && now - it.awaySince > AWAY_MILLISECONDS }
        leaving.forEach { it.away = true }
        sessions.values.removeAll { it.away && now - it.awaySince > FORGET_MILLISECONDS }

        if (leaving.isNotEmpty()) {
            publish()
        }
    }

    private fun session(id: Long): Session? = sessions.values.firstOrNull { it.member.id == id }

    /** The sessions of the people in now: listed on the phone, and counted against the limit. */
    private fun present(): List<Session> = sessions.values.filter { !it.away }

    private fun limit(): Int = currentOptions.value.limit.takeIf { it > 0 } ?: hardLimit

    private fun publish() {
        currentMembers.value = present().map { it.member }
    }

    /**
     * Who a request's session belongs to: one made here, used from the
     * address that joined, and not ended since. Someone back from away is
     * in again, without the form, while there's room.
     */
    fun member(request: Request): Member? = synchronized(lock) {
        if (ended) {
            return null
        }

        tidyHolding()
        val session = request.cookies(cookie).firstNotNullOfOrNull { token -> sessions[token]?.takeIf { it.member.address == request.from } }
            ?: return null

        if (session.away) {
            if (present().size >= limit()) {
                return null
            }

            session.away = false
            session.awaySince = clock()
            publish()
        }

        session.member
    }

    /**
     * Answers a request in the room, where `rest` is its path after the
     * code: anyone may join and ask where they stand; everything else needs
     * a session, and `content` answers it for the member.
     */
    suspend fun serve(request: Request, rest: List<String>, content: suspend (Member, List<String>) -> Response): Response {
        val member = member(request)
        val route = rest.firstOrNull()

        return when {
            route == "status" && rest.size == 1 -> Response.json(200, """{"state":"${state(request, member).json}"}""")
            route == "join" && rest.size == 1 -> if (request.method == "POST") join(request, member) else seeRoom(emptyMap())
            request.method != "GET" -> Response.text(405, "Only reading here")
            member != null -> content(member, rest)
            route == null -> enter(request, content)
            else -> joinFirst()
        }
    }

    private fun state(request: Request, member: Member?): RoomState = if (member != null) RoomState.Joined else refusal(request.from) ?: RoomState.Left

    /** The room's page for someone not in yet: in at once when there's nothing to ask, else the form. */
    private suspend fun enter(request: Request, content: suspend (Member, List<String>) -> Response): Response {
        val options = currentOptions.value

        if (options.askNames || options.requirePin) {
            val refusal = refusal(request.from)
            return form(refusal?.let(::status) ?: 200, refusal?.let(::message).orEmpty(), refusal)
        }

        return when (val admission = admit(request.from, name = null)) {
            is Admission.Out -> form(status(admission.state), message(admission.state), admission.state)
            is Admission.In -> {
                val page = content(admission.member, emptyList())
                if (page is Response.Bytes) page.withHeaders(mapOf("Set-Cookie" to cookieFor(admission.token))) else page
            }
        }
    }

    private fun join(request: Request, member: Member?): Response {
        if (member != null) {
            return seeRoom(emptyMap())
        }

        // Another site's page posting the form is no one joining.
        if (request.headers["sec-fetch-site"] == "cross-site" || request.headers["sec-fetch-site"] == "same-site") {
            return Response.text(403, "Join from the page at the address on the phone.")
        }

        val fields = formFields(request.body)
        val options = currentOptions.value
        val name = if (options.askNames) senderName(fields["name"]) else null
        refusal(request.from)?.let { refusal -> return form(status(refusal), message(refusal), refusal, name) }

        if (options.requirePin) {
            val from = request.from

            if (pinGuard.waitFor(from) > 0) {
                return form(429, pinGuard.waitingMessage(from, words, wording.pinWait), null, name)
            }

            if (!pinMatches(fields["pin"])) {
                return if (pinGuard.wrong(from) > 0) {
                    form(429, pinGuard.waitingMessage(from, words, wording.pinWait), null, name)
                } else {
                    form(403, words.string(wording.wrongPin), null, name)
                }
            }

            pinGuard.right(from)
        }

        return when (val admission = admit(request.from, name)) {
            is Admission.Out -> form(status(admission.state), message(admission.state), admission.state, name)
            is Admission.In -> seeRoom(mapOf("Set-Cookie" to cookieFor(admission.token)))
        }
    }

    private fun pinMatches(typed: String?): Boolean {
        val pin = currentPin.value ?: return false
        return MessageDigest.isEqual(pin.toByteArray(), typed.orEmpty().trim().toByteArray())
    }

    /** Lets someone in, unless the room turns them away: then why. */
    private fun admit(address: String, name: String?): Admission = synchronized(lock) {
        refusal(address)?.let { return Admission.Out(it) }

        if (sessions.size >= SESSIONS_KEPT) {
            sessions.values.filter { it.away }.minByOrNull { it.awaySince }?.let { sessions.remove(it.token) }
        }

        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val now = clock()
        val member = Member(nextId++, name ?: words.string(R.string.nearby_guest, ++guests), address, now)
        sessions[token] = Session(token, member, streams = 0, awaySince = now)
        publish()
        Admission.In(token, member)
    }

    /** Why someone at `address` can't join now, or null when they can. */
    private fun refusal(address: String): RoomState? = synchronized(lock) {
        tidyHolding()

        when {
            ended -> RoomState.Ended
            address in removed -> RoomState.Removed
            !currentOptions.value.open -> RoomState.Closed
            present().size >= limit() -> RoomState.Full
            else -> null
        }
    }

    private fun status(state: RoomState): Int = if (state == RoomState.Ended) 410 else 403

    private fun message(state: RoomState): String = when (state) {
        RoomState.Removed -> words.string(wording.removed)
        RoomState.Full -> words.string(wording.full)
        RoomState.Closed -> words.string(wording.closed)
        RoomState.Ended -> words.string(wording.ended)
        RoomState.Joined, RoomState.Left -> ""
    }

    private fun cookieFor(token: String): String = "$cookie=$token; Path=/$code/; HttpOnly; SameSite=Strict"

    /** To the room's page, after joining or for someone already in. */
    private fun seeRoom(headers: Map<String, String>): Response =
        Response.Bytes(303, "text/plain; charset=utf-8", "See /$code/".toByteArray(), mapOf("Location" to "/$code/") + headers)

    /**
     * The join form, saying `problem` when there is one. Someone the room is
     * full for, or closed to, keeps the form to try again; someone removed,
     * or a room that ended, gets only the message.
     */
    private fun form(status: Int, problem: String, state: RoomState?, name: String? = null): Response {
        val options = currentOptions.value
        val asking = state == null || state == RoomState.Full || state == RoomState.Closed
        var page = section(template(), "code", keep = false)
        page = section(page, "room", keep = true)
        page = section(page, "name field", keep = asking && options.askNames)
        page = section(page, "pin field", keep = asking && options.requirePin)
        page = section(page, "button", keep = asking)
        // After a wrong PIN, the PIN is what's typed next; otherwise the first field.
        val pinFirst = options.requirePin && (!options.askNames || !name.isNullOrEmpty())
        val values = mapOf(
            htmlIn(words.language),
            "<!-- title -->" to escapeHtml(words.string(wording.title)),
            "<!-- name label -->" to escapeHtml(words.string(R.string.pages_your_name)),
            "<!-- typed name -->" to escapeHtml(name.orEmpty()),
            "<!-- name focus -->" to if (pinFirst) "" else " autofocus",
            "<!-- pin focus -->" to if (pinFirst) " autofocus" else "",
            "<!-- pin label -->" to escapeHtml(words.string(wording.pinLabel)),
            "<!-- button label -->" to escapeHtml(words.string(if (state == null) wording.button else R.string.nearby_try_again)),
            "<!-- problem -->" to escapeHtml(problem),
        )
        return Response.page(status, fill(page, values))
    }
}

/** A form's fields, as a browser posts them (application/x-www-form-urlencoded). */
private fun formFields(body: ByteArray): Map<String, String> =
    body.decodeToString().split('&').filter { it.isNotEmpty() }.associate { pair ->
        formDecode(pair.substringBefore('=')) to formDecode(pair.substringAfter('=', ""))
    }

private fun formDecode(text: String): String = try {
    URLDecoder.decode(text, StandardCharsets.UTF_8.name())
} catch (malformed: IllegalArgumentException) {
    ""
}

/** What a room answers anyone not in it who asks for more than its page. */
internal fun joinFirst(): Response = Response.text(403, "Join first, at the address on the phone.")

/**
 * The answer for a code that isn't a room's. Where a code is typed or asked
 * about (a room's page, its form, its status) it's a guess, which `guard`
 * counts, and `page` says so, with `wrong` from `words`. What a page loads
 * by itself, such as its events, gets what a room gives anyone not in it:
 * nothing to learn from, and nothing counted against a browser still trying
 * a room that's gone.
 */
internal fun unknownRoom(request: Request, rest: List<String>, guard: CodeGuard, words: Words, @StringRes wrong: Int, page: (Int, String) -> Response): Response {
    val route = rest.firstOrNull()

    if (route != null && route != "join" && route != "status") {
        return joinFirst()
    }

    val waiting = guard.wrong(request.from) > 0

    return when {
        // A page following a room that's gone hears it ended, and stops asking.
        route == "status" -> Response.json(200, """{"state":"${RoomState.Ended.json}"}""")
        waiting -> page(429, guard.waitingMessage(request.from, words))
        else -> page(200, words.string(wrong))
    }
}

/**
 * The page asking for a room's code, for someone who typed only the
 * address, or a code that isn't there: `label` says where the code is, in
 * `language`, the page's.
 */
internal fun codePage(template: String, status: Int, label: String, problem: String, language: String): Response {
    var page = section(template, "code", keep = true)
    page = section(page, "room", keep = false)
    return Response.page(status, fill(page, mapOf(htmlIn(language), "<!-- label -->" to escapeHtml(label), "<!-- problem -->" to escapeHtml(problem))))
}

/** The template with the part between `<!-- name -->` and `<!-- /name -->` kept without its markers, or left out. */
internal fun section(template: String, name: String, keep: Boolean): String {
    val start = "<!-- $name -->"
    val end = "<!-- /$name -->"
    val from = template.indexOf(start)
    val to = template.indexOf(end, from + start.length)

    if (from < 0 || to < 0) {
        return template
    }

    return if (keep) {
        template.removeRange(to, to + end.length).removeRange(from, from + start.length)
    } else {
        template.removeRange(from, to + end.length)
    }
}

/** When someone joined, as the phone lists them: "Joined 3 minutes ago". */
fun joinedAgo(joined: Long, now: Long, words: Words): String {
    val minutes = ((now - joined).coerceAtLeast(0) / 60_000).toInt()
    val hours = minutes / 60

    return when {
        minutes < 1 -> words.string(R.string.nearby_joined_just_now)
        hours < 1 -> words.plural(R.plurals.nearby_joined_minutes_ago, minutes, minutes)
        else -> words.plural(R.plurals.nearby_joined_hours_ago, hours, hours)
    }
}
