package com.hyuchia.orbit.data

import java.io.IOException

/**
 * What the person sharing answered about the map's files Orbit can't clean
 * ([Uncleaned]), each file as it was when they were asked, by its stamp
 * ([FileStamp]): those they were asked about, and of those, the ones they
 * chose to share as they are. An answer is about that very file: one that
 * changed since, or another put in its place under its name, is a file no
 * one was asked about. A way out that can ask stops for it (`stops`,
 * [FileChanged]), so the person is asked about it as it is now, as a share
 * does; one that can't leaves it on this device, as a presentation's
 * browsers do ([lasting]). Either way, a file Orbit can clean goes clean.
 */
data class AsTheyAreFiles(
    val asked: Set<FileStamp> = emptySet(),
    val chosen: Set<FileStamp> = emptySet(),
    val stops: Boolean = true,
) {
    /** The same answers, for a way out that lasts and can't ask: a file that changed since stays on this device. */
    fun lasting(): AsTheyAreFiles = copy(stops = false)

    /** Those of `files` chosen to go as they are, as they are now, by name. */
    fun chosenIn(files: Uncleaned): Set<String> = files.names.filterTo(LinkedHashSet()) { name -> files.stamps[name]?.let { it in chosen } == true }

    companion object {
        /** No answers, from a way out that asked nothing: a file Orbit can't clean stays on this device. */
        val NONE = AsTheyAreFiles(stops = false)
    }
}

/**
 * A map's file that changed since it was planned, or since the person
 * sharing answered about it ([AsTheyAreFiles]): what was being made with it
 * is thrown away, so nothing of it leaves the phone, and the way out starts
 * again, asking about it as it is now.
 */
class FileChanged(val name: String) : IOException("$name changed since it was planned")
