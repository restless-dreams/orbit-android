package com.hyuchia.orbit.privacy

import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What private maps show now. `locked` once Orbit locked them, until the
 * phone's owner unlocks them. `keptOpen` are maps made private while the
 * others were locked: they stay as they were, on screen, until Orbit next
 * locks. Not `lockable` while the phone has no screen lock to open them with.
 * `lockedAway` when they locked while Orbit was away or the screen was off,
 * rather than from Orbit's own button: whatever was open then is covered.
 */
data class PrivateView(
    val locked: Boolean = true,
    val keptOpen: Set<String> = emptySet(),
    val lockable: Boolean = true,
    val lockedAway: Boolean = false,

    /** How many times something that showed has locked: every private map, or those made private since they last locked. */
    val locks: Int = 0,
) {
    /** Whether `map` shows as a private map that's locked: no title, picture, tags or progress. */
    fun hides(map: MapSummary): Boolean = map.metadata.isPrivate && hides(map.id)

    /** Whether a private map with this id shows locked. */
    fun hides(id: String): Boolean = locked && id !in keptOpen
}

/**
 * When private maps lock and unlock. One unlock opens every private map;
 * they lock again when Orbit has been off screen for [delay] milliseconds
 * or more, when the screen turns off, and when someone locks them. Orbit
 * starts locked, so a new start after Android closed it asks again.
 *
 * A phone without a screen lock has nothing to open them with, so they
 * never show locked there. Everything here happens at once, on the thread
 * that calls it, so a screen coming back already shows them locked.
 *
 * Time comes from `clock`: on phones, the time since they started, which
 * no one can set back; in tests, whatever they move it to.
 */
class PrivateLock(private val clock: () -> Long, screenLock: Boolean = true) {
    private var unlocked = false
    private var keptOpen = emptySet<String>()
    private var screenLock = screenLock
    private var lockedAway = false
    private var locks = 0
    private val state = MutableStateFlow(current())

    /** Whether private maps show locked, and the ones made private since they locked. */
    val view: StateFlow<PrivateView> = state.asStateFlow()

    /**
     * Each time private maps lock, once collected: their being locked already as it starts doesn't count, nor does
     * locking again what's locked. A map made private while the others were locked locks with them the next time.
     */
    val newlyLocked: Flow<Unit> = view.map { it.locks }.distinctUntilChanged().drop(1).map {}

    /** How long Orbit can be off screen before private maps lock again, in milliseconds. */
    @Volatile
    var delay: Long = 60_000

    /** When Orbit left the screen, while it's off it; null while it shows. */
    private var leftAt: Long? = null

    /**
     * While the phone asks for its screen lock. On Android 10 its PIN screen
     * is a screen of its own, which takes Orbit off screen: that isn't leaving.
     */
    @Volatile
    var asking = false

    /** The owner unlocked them: every private map opens until the next lock. */
    fun unlock() {
        unlocked = true
        keptOpen = emptySet()
        lockedAway = false
        publish()
    }

    /** Locks every private map now, the ones made private meanwhile included: from Orbit's own button, say. */
    fun lock() {
        lock(away = false)
    }

    private fun lock(away: Boolean) {
        if (unlocked || keptOpen.isNotEmpty()) {
            locks += 1
        }

        unlocked = false
        keptOpen = emptySet()
        lockedAway = away
        publish()
    }

    /** `id` was made private while the others were locked: it stays as it was until they lock again. */
    fun keepOpen(id: String) {
        if (!unlocked) {
            keptOpen = keptOpen + id
            publish()
        }
    }

    /** Whether the phone has a screen lock now: it can be set or removed while Orbit is away. */
    fun screenLockIs(present: Boolean) {
        screenLock = present
        publish()
    }

    /** Orbit went off screen. */
    fun left() {
        if (!asking) {
            leftAt = clock()
        }
    }

    /** Orbit came back on screen: private maps lock if it was away long enough, or for a time that can't be told. */
    fun returned() {
        val away = leftAt?.let { clock() - it }
        leftAt = null

        // A clock that went back counts as long enough: being away is never shortened by setting the time.
        if (away != null && (away < 0 || away >= delay)) {
            lock(away = true)
        }
    }

    /** The screen turned off: private maps lock at once. */
    fun screenOff() {
        lock(away = true)
    }

    /**
     * Runs `block` until it's done, or until private maps next lock, which
     * cancels it: what it gives, or null once they've locked. For what names
     * a private map while it's unlocked and can take a while, such as a
     * message waiting for the one before it to go: it goes as they lock,
     * as everything that names one does, even before it shows.
     */
    suspend fun <T : Any> unlessLocked(block: suspend () -> T): T? {
        val locks = view.value.locks

        return coroutineScope {
            val done = async { block() }
            // From the view as it is now: a lock since `locks` was read counts too.
            val locked = launch { view.first { it.locks != locks }.also { done.cancel() } }

            try {
                done.await()
            } catch (cancelled: CancellationException) {
                // Stopped by the lock, not by whoever waits for it.
                ensureActive()
                null
            } finally {
                locked.cancel()
            }
        }
    }

    private fun current(): PrivateView = if (screenLock) {
        PrivateView(locked = !unlocked, keptOpen = keptOpen, lockedAway = lockedAway && !unlocked, locks = locks)
    } else {
        PrivateView(locked = false, lockable = false)
    }

    private fun publish() {
        state.value = current()
    }
}

/**
 * Each time private maps lock, calls off the pictures being drawn of those
 * that hide now, wherever they're drawn (`revoke`, the pages'): `summary`
 * says what's known of a map, and a map whose details aren't known counts as
 * private, as it does everywhere. Runs until this scope ends.
 */
fun CoroutineScope.callOffPicturesAsPrivateMapsLock(lock: PrivateLock, summary: (String) -> MapSummary?, revoke: (hides: (String) -> Boolean) -> Unit): Job = launch {
    lock.newlyLocked.collect {
        val view = lock.view.value
        revoke { id -> summary(id)?.let(view::hides) ?: true }
    }
}
