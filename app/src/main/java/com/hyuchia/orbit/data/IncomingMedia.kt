package com.hyuchia.orbit.data

import java.io.File

/**
 * Pictures, videos, sounds and files on their way into maps' folders, which
 * the maps' texts don't point at yet. Each counts against its map's room
 * ([MapStore.mediaSize]) from the moment its copy is let start
 * ([MapStore.arriving]), at the size it says it is, and once it's in, at the
 * size of its files, until a text the map has points at them ([settle]). So
 * files that come in together, one after another as a batch does or side by
 * side, never take a map past its room, as each would when it was checked
 * against the same map as the others.
 */
internal class IncomingMedia(
    /** Hears each name held for a file on its way in, once it's held: a test holds a copy there, to bring another in beside it. */
    private val holding: (String) -> Unit = {},
) {
    private val arrivals = HashMap<String, MutableList<Arrival>>()

    /**
     * The names held for files on their way into each map's media folder,
     * by the map's id: each without its extension, so a name holds every
     * file that starts with it, as a video's poster and a still's motion do.
     */
    private val held = HashMap<String, MutableSet<String>>()

    /**
     * Names for files on their way into map `id`'s media folder: each one
     * held is one no file there has and no other file on its way there
     * holds, chosen and held in one step, so two copies at once never get
     * the same name, and so never write one over the other. A name stays
     * held until this closes, by when its file is in, under it, or gone.
     */
    open inner class Names internal constructor(val id: String) : AutoCloseable {
        private val mine = HashSet<String>()

        /**
         * The first of `stems` that no file in `folder` starts with (its
         * name without its extension) and no other file on its way there
         * holds: held from now on, until this closes.
         */
        fun stem(folder: File, stems: Sequence<String>): String {
            val stem = synchronized(this@IncomingMedia) {
                val taken = folder.list().orEmpty().mapTo(HashSet()) { it.substringBeforeLast('.') }
                stems.first { it !in taken && free(it) }.also(::take)
            }

            holding(stem)
            return stem
        }

        /**
         * Holds `name` in `folder`, a file's whole name, from now on until
         * this closes: false, and nothing held, when a file there has it
         * already, or another on its way there holds its name without its
         * extension.
         */
        fun hold(folder: File, name: String): Boolean {
            val stem = name.substringBeforeLast('.')

            val taken = synchronized(this@IncomingMedia) {
                if (File(folder, name).exists() || !free(stem)) {
                    true
                } else {
                    take(stem)
                    false
                }
            }

            if (!taken) {
                holding(name)
            }

            return !taken
        }

        /** Whether `stem` is held by no other file on its way into the map; expects the lock held. */
        private fun free(stem: String): Boolean = stem in mine || held[id]?.contains(stem) != true

        /** Holds `stem`; expects the lock held. */
        private fun take(stem: String) {
            held.getOrPut(id) { HashSet() } += stem
            mine += stem
        }

        override fun close() {
            synchronized(this@IncomingMedia) {
                held[id]?.let { names ->
                    names -= mine

                    if (names.isEmpty()) {
                        held.remove(id)
                    }
                }

                mine.clear()
            }
        }
    }

    /** Names held for files on their way into map `id`'s media folder, apart from a copy's [Arrival]: closed once they're in. */
    fun names(id: String): Names = Names(id)

    /** Whether a file on its way into map `id`'s media folder holds `name`, or its name without its extension ([Names]). */
    @Synchronized
    fun holds(id: String, name: String): Boolean = held[id]?.contains(name.substringBeforeLast('.')) == true

    /**
     * One copy's way into map `id`, and the room it takes there: `bytes`
     * while it's copied, then its files' own once they're in ([arrived]).
     * `mapMedia` is what the map came to as it was let start, with all else
     * on its way in then: what the copy is kept within as it goes. Closed
     * before its files are in, it takes nothing more. The names it holds for
     * its files ([Names]) are held until it closes.
     */
    inner class Arrival internal constructor(id: String, private val bytes: Long, val mapMedia: Long) : Names(id) {
        private var files: List<File>? = null

        /** Its files are in: they count by their own sizes from now on, until the map's text points at them. */
        internal fun arrived(files: List<File>) {
            synchronized(this@IncomingMedia) {
                this.files = files
            }
        }

        /** What it takes of its map's room, besides the files the map's text points at (`pointedAt`), which count as the map's. */
        internal fun takes(pointedAt: Set<String>): Long = files?.filter { it.name !in pointedAt && it.isFile }?.sumOf { it.length() } ?: bytes

        /** Whether it's the map's now: its files are in, and its text points at each of them, or they went. */
        internal fun settled(pointedAt: Set<String>): Boolean = files?.all { it.name in pointedAt || !it.isFile } == true

        /** Whether it's still being copied. */
        internal val copying: Boolean get() = files == null

        /** The names of the files it brought in, none while it's still being copied. */
        internal val names: List<String> get() = files?.map { it.name }.orEmpty()

        /**
         * Its copy was stopped as its files came in, and whoever asked for
         * them never heard of them: they take none of the map's room from
         * now on, and stay for the map's tidying up.
         */
        internal fun letGo() {
            synchronized(this@IncomingMedia) {
                remove(this)
            }
        }

        override fun close() {
            synchronized(this@IncomingMedia) {
                super.close()

                if (files == null) {
                    remove(this)
                }
            }
        }
    }

    /** A copy of `bytes` let start into map `id`, which came to `mapMedia` with all else on its way in. */
    @Synchronized
    fun add(id: String, bytes: Long, mapMedia: Long): Arrival = Arrival(id, bytes, mapMedia).also { arrivals.getOrPut(id) { mutableListOf() } += it }

    /**
     * What's on its way into map `id` takes of its room, but `besides`,
     * besides the files its text points at (`pointedAt`). With `settling`,
     * `pointedAt` is the text the map has, and what it points at is the map's
     * from now on ([settle]).
     */
    @Synchronized
    fun taken(id: String, pointedAt: Set<String>, besides: Arrival? = null, settling: Boolean = false): Long {
        if (settling) {
            settle(id) { pointedAt }
        }

        return arrivals[id].orEmpty().filter { it !== besides }.sumOf { it.takes(pointedAt) }
    }

    /**
     * Map `id` has a text that points at the names `pointedAt` gives: what
     * came in that it points at is the map's now, and counts no more as
     * coming in. `pointedAt` is only read when something came in.
     */
    @Synchronized
    fun settle(id: String, pointedAt: () -> Set<String>) {
        val coming = arrivals[id]?.takeIf { list -> list.any { !it.copying } } ?: return
        val names = pointedAt()
        coming.removeAll { it.settled(names) }

        if (coming.isEmpty()) {
            arrivals.remove(id)
        }
    }

    /**
     * What came into map `id` that its text never pointed at, now that no
     * screen will: its files go when the map's tidied up, and take none of
     * its room. Copies still under way stay.
     */
    @Synchronized
    fun forget(id: String) {
        val coming = arrivals[id] ?: return
        coming.removeAll { !it.copying }

        if (coming.isEmpty()) {
            arrivals.remove(id)
        }
    }

    /**
     * What came into map `id` as the files `names` won't join it after all,
     * as a drop the map took no more by the time it was in: it takes none of
     * its room from now on. The files stay, for the map's tidying up.
     */
    @Synchronized
    fun release(id: String, names: Set<String>) {
        val coming = arrivals[id] ?: return
        coming.removeAll { it.names.any(names::contains) }

        if (coming.isEmpty()) {
            arrivals.remove(id)
        }
    }

    @Synchronized
    private fun remove(arrival: Arrival) {
        val coming = arrivals[arrival.id] ?: return
        coming.remove(arrival)

        if (coming.isEmpty()) {
            arrivals.remove(arrival.id)
        }
    }
}
