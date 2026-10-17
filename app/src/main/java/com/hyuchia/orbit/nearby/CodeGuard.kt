package com.hyuchia.orbit.nearby

import androidx.annotation.PluralsRes
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/** Wrong codes one device may type before it has to wait. */
private const val FREE_TRIES = 5
/**
 * Wrong codes every device together may type before new ones all wait: a
 * room of people mistyping never gets near it, while someone taking
 * addresses one after another to guess from each can't go on for long.
 * Devices already in are never kept out by it.
 */
private const val FREE_TRIES_TOGETHER = 100
/** The first wait, which doubles with each wrong code after it. */
private const val FIRST_WAIT_MILLISECONDS = 30_000L
/** The longest wait. */
private const val LONGEST_WAIT_MILLISECONDS = 60 * 60 * 1000L
/** The address the waits of every device together are kept under. */
private const val EVERYONE = "*"

private val random = SecureRandom()

/** A code to type in a browser: six digits, so 900,000 to guess from. */
fun newCode(): String = (100_000 + random.nextInt(900_000)).toString()

/** A PIN to say aloud in the room: four digits, never in an address or a QR code. */
fun newPin(): String = random.nextInt(10_000).toString().padStart(4, '0')

/** Whether a path's first part is shaped like a code, so a wrong one counts as a guess (unlike a browser asking for its icon). */
fun looksLikeCode(part: String?): Boolean = part != null && part.length in 1..12 && part.all { it in '0'..'9' }

/**
 * Keeps guessers away from codes: a device that types wrong codes gets a
 * few tries, then waits before it may try again, twice as long each time;
 * so do all new devices once `together` wrong codes came from them all,
 * when there's such a limit. The phone can say when someone was kept out.
 */
class CodeGuard(private val clock: () -> Long = System::currentTimeMillis, private val together: Int? = FREE_TRIES_TOGETHER) {
    private class Tries {
        var wrong = 0
        var lockedUntil = 0L
    }

    private val tries = ConcurrentHashMap<String, Tries>()
    private val lastLockout = MutableStateFlow(0L)

    /** When a device was last kept out, or 0 when none has been: for the phone to show. */
    val lockedOut: StateFlow<Long> = lastLockout.asStateFlow()

    /** How long `from` has to wait before typing a code again, in milliseconds; 0 when it may. */
    fun waitFor(from: String): Long = max(waitOf(from), waitOf(EVERYONE))

    private fun waitOf(key: String): Long {
        val entry = tries[key] ?: return 0
        return synchronized(entry) { max(0L, entry.lockedUntil - clock()) }
    }

    /** A wrong code from `from`. Returns how long it has to wait now, as [waitFor]. */
    fun wrong(from: String): Long {
        count(from, FREE_TRIES)
        together?.let { count(EVERYONE, it) }
        return waitFor(from)
    }

    private fun count(key: String, free: Int) {
        val entry = tries.getOrPut(key) { Tries() }

        synchronized(entry) {
            entry.wrong += 1

            if (entry.wrong >= free) {
                val doublings = min(entry.wrong - free, 12)
                entry.lockedUntil = clock() + min(LONGEST_WAIT_MILLISECONDS, FIRST_WAIT_MILLISECONDS shl doublings)
                lastLockout.value = clock()
            }
        }
    }

    /** The right code: `from` starts over. */
    fun right(from: String) {
        tries.remove(from)
    }

    /** Everyone starts over, as with a new code. */
    fun reset() {
        tries.clear()
        lastLockout.value = 0L
    }

    /** What a device that has to wait reads, in its browser: `message` says what it typed wrong, and what to try again with. */
    fun waitingMessage(from: String, words: Words, @PluralsRes message: Int = R.plurals.nearby_too_many_wrong_codes): String {
        val minutes = ((waitFor(from) + 59_999) / 60_000).toInt().coerceAtLeast(1)
        return words.plural(message, minutes, minutes)
    }
}
