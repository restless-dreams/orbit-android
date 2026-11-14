package com.hyuchia.orbit.media

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hyuchia.orbit.data.FileStamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * A file of the map's that Orbit couldn't clean, as it was then (`stamp`),
 * and the picture, video or sound on screen it's of: what the person is
 * asked about, and whether it's asked about again, as it `changed` after
 * they answered.
 */
data class UncleanFile(val item: GalleryItem, val file: File, val stamp: FileStamp, val changed: Boolean = false)

/**
 * Sharing from the full-screen viewer: a clean copy of the picture, video
 * or sound on screen goes ([share]). One Orbit couldn't clean waits for the
 * person to say whether it goes as it is (`asking`), as the very item and
 * file it was about, whatever page is on screen by then, so its name and
 * kind are its own, and as that file was then: one that changed before it
 * went is asked about again, as it is now. And one no copy could be made
 * of, clean or as it is, says so (`failed`, the kind it was), rather than
 * nothing happening.
 */
@Stable
class ViewerSharing internal constructor(
    private val scope: CoroutineScope,
    private val share: suspend (GalleryItem, File) -> Shared,

    /** Shares a file as it is, the one the person was asked about (its stamp), once they chose to. */
    private val shareAsItIs: suspend (GalleryItem, File, FileStamp) -> Shared,
) {
    /** A copy is being made, clean or as it is: another tap waits for it. */
    var busy by mutableStateOf(false)
        private set

    var asking by mutableStateOf<UncleanFile?>(null)
        private set

    var failed by mutableStateOf<MediaKind?>(null)
        private set

    /** Shares `file`, `item`'s, as a clean copy, or asks about it as it is. */
    fun start(item: GalleryItem, file: File) {
        if (busy) {
            return
        }

        busy = true

        scope.launch {
            try {
                cleanly(item, file, changed = false)
            } finally {
                busy = false
            }
        }
    }

    /** Shares a clean copy of `file`, or asks about it as it is, saying when it `changed` after the person answered. */
    private suspend fun cleanly(item: GalleryItem, file: File, changed: Boolean) {
        when (val shared = share(item, file)) {
            is Shared.NotClean -> asking = UncleanFile(item, file, shared.stamp, changed)
            Shared.Failed, Shared.Changed -> failed = item.kind
            Shared.Clean -> Unit
        }
    }

    /** The person's answer: whether the file asked about goes as it is. */
    fun answer(asItIs: Boolean) {
        val asked = asking ?: return
        asking = null

        if (asItIs) {
            // Its copy takes a while, as a big video's does: a tap on Share meanwhile makes no second one.
            busy = true

            scope.launch {
                try {
                    when (shareAsItIs(asked.item, asked.file, asked.stamp)) {
                        // Another file in its place, or the same one written again: it's shared clean, or asked about again, once.
                        Shared.Changed -> {
                            if (asked.changed) {
                                failed = asked.item.kind
                            } else {
                                cleanly(asked.item, asked.file, changed = true)
                            }
                        }
                        Shared.Failed -> failed = asked.item.kind
                        Shared.Clean, is Shared.NotClean -> Unit
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

    fun dismissFailure() {
        failed = null
    }
}
