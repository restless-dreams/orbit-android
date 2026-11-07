package com.hyuchia.orbit.nearby

/** How many offers answered (opened or declined) a device keeps saying so for, the newest. */
internal const val SETTLED_OFFERS_KEPT = 256

/**
 * Where each offer made here is, for `GET /offer/<id>`, with the session
 * that proves it to its sender ([OfferSession.stateProof]). An offer going
 * on is kept until it's opened or declined, which the places offers take
 * under the limits keep few; then for `settledFor` milliseconds on `clock`,
 * long enough for its sender to hear, and only the newest `mostSettled` of
 * those, so a device offering again and again can't fill the memory.
 */
internal class OfferStates(private val clock: () -> Long, private val settledFor: Long, private val mostSettled: Int = SETTLED_OFFERS_KEPT) {
    private class Entry(val state: OfferState, val session: OfferSession, val since: Long)

    /** In the order they last changed. */
    private val entries = LinkedHashMap<String, Entry>()

    /** How many it keeps now. */
    val size: Int get() = synchronized(this) { entries.size }

    /** Where offer `id` is, or null when it's none this device knows, or one it no longer says. */
    operator fun get(id: String): OfferState? = synchronized(this) {
        forget()
        entries[id]?.state
    }

    /** Offer `id`, of `session`, is in `state` now. */
    fun set(id: String, state: OfferState, session: OfferSession) {
        synchronized(this) {
            entries.remove(id)
            entries[id] = Entry(state, session, clock())
            forget()
        }
    }

    /** What `GET /offer/<id>` answers: where it is, and the proof; null when it's none this device knows, or no longer says. */
    fun answer(id: String): StateAnswer? {
        val entry = synchronized(this) {
            forget()
            entries[id]
        } ?: return null

        val state = entry.state.name.lowercase()
        return StateAnswer(state, base64Url(entry.session.stateProof(id, state)))
    }

    /** Lets go of offers answered too long ago, and of the oldest answered past the most it keeps. */
    private fun forget() {
        val now = clock()
        entries.values.removeAll { it.state.settled && now - it.since > settledFor }
        var extra = entries.values.count { it.state.settled } - mostSettled
        val oldestFirst = entries.values.iterator()

        while (extra > 0 && oldestFirst.hasNext()) {
            if (oldestFirst.next().state.settled) {
                oldestFirst.remove()
                extra -= 1
            }
        }
    }
}
