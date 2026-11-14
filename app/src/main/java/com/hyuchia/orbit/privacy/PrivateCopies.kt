package com.hyuchia.orbit.privacy

import com.hyuchia.orbit.ui.forgetExport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Copies of private maps' files handed to other apps, as an idea's file
 * opens in one: each is deleted as private maps lock (`newlyLocked`), so
 * nothing of a locked map stays where another app can read it. A copy's
 * folder is marked as a private map's, so one Orbit stopped before it was
 * locked goes as Orbit starts again, locked, too. So does every copy a
 * presentation's browsers were served of a file as it is (`servedAsTheyAre`),
 * which no presentation outlives.
 */
class PrivateCopies(
    private val scope: CoroutineScope,
    newlyLocked: Flow<Unit>,
    private val exports: File,

    /** The copies of files as they are a presentation's browsers were served, if Orbit stopped before it ended. */
    private val servedAsTheyAre: File? = null,

    /** Where copies are deleted: off the main thread, or a test's own. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val kept = mutableSetOf<File>()

    init {
        scope.launch(io) {
            exports.listFiles().orEmpty().filter { File(it, MARK).isFile }.forEach { it.deleteRecursively() }
            servedAsTheyAre?.deleteRecursively()
        }

        scope.launch {
            newlyLocked.collect { forgetAll() }
        }
    }

    /** Keeps `copy`, a private map's file in the exports, until private maps lock. */
    fun keep(copy: File) {
        runCatching { copy.parentFile?.let { File(it, MARK).createNewFile() } }

        synchronized(kept) {
            kept += copy
        }
    }

    /** Deletes every copy kept: private maps locked. */
    fun forgetAll() {
        val copies = synchronized(kept) { kept.toList().also { kept.clear() } }

        if (copies.isNotEmpty()) {
            scope.launch(io) { copies.forEach(::forgetExport) }
        }
    }

    private companion object {
        /** Beside a private map's copy, in its folder of its own. */
        const val MARK = ".private"
    }
}
