package com.hyuchia.orbit.nearby

/**
 * Events one stream may have waiting to be written. A reader further behind
 * is dropped: it reconnects, and catches up from the latest show, captions
 * and step.
 */
internal const val EVENT_BACKLOG = 64

/**
 * One browser's event stream: what's sent arrives in order, and closing it
 * ends the connection once what was sent before is written, even when it
 * closes before a connection takes it, as a browser's does when it's
 * removed while its stream starts. `onClose` hears once that it's over,
 * whichever side ended it. A reader that falls more than [EVENT_BACKLOG]
 * events behind is dropped.
 */
class EventStream(private val onClose: () -> Unit = {}) {
    /** The connection writing a stream. */
    internal interface Pump {
        /** There's something to write. */
        fun wake()

        /** End the connection now, written or not. */
        fun hangUp()
    }

    private val lock = Any()
    private val waiting = ArrayDeque<String>()
    private var closing = false
    private var over = false
    private var pump: Pump? = null

    /** Whether it's closed, or closing once what was sent is written: nothing more goes out. */
    val isClosed: Boolean get() = synchronized(lock) { closing }

    fun send(data: String) {
        val behind: Boolean
        val attached: Pump?

        synchronized(lock) {
            if (closing) {
                return
            }

            behind = waiting.size >= EVENT_BACKLOG

            if (behind) {
                waiting.clear()
                closing = true
            } else {
                waiting.addLast(data)
            }

            attached = pump
        }

        when {
            behind && attached != null -> attached.hangUp()
            behind -> finish()
            else -> attached?.wake()
        }
    }

    fun close() {
        val attached: Pump?
        val nothingLeft: Boolean

        synchronized(lock) {
            if (closing) {
                return
            }

            closing = true
            attached = pump
            nothingLeft = waiting.isEmpty()
        }

        when {
            attached != null -> attached.wake()
            // What was sent waits for the connection that takes the stream: it writes that, then ends.
            nothingLeft -> finish()
        }
    }

    /** A connection starts writing it; false once it's over, or closed with nothing left to write. */
    internal fun attach(pump: Pump): Boolean = synchronized(lock) {
        if (over || (closing && waiting.isEmpty())) {
            false
        } else {
            this.pump = pump
            true
        }
    }

    /** Everything waiting to be written, or null once it's closing with nothing left, when its connection ends. */
    internal fun take(): List<String>? = synchronized(lock) {
        when {
            waiting.isNotEmpty() -> ArrayList(waiting).also { waiting.clear() }
            closing -> null
            else -> emptyList()
        }
    }

    internal val hasWork: Boolean get() = synchronized(lock) { waiting.isNotEmpty() || closing }

    /** Its connection ended, for whatever reason. */
    internal fun finish() {
        val first = synchronized(lock) {
            closing = true
            waiting.clear()
            val first = !over
            over = true
            first
        }

        if (first) {
            onClose()
        }
    }
}
