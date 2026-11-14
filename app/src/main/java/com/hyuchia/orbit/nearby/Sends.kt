package com.hyuchia.orbit.nearby

import androidx.compose.runtime.mutableStateMapOf
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.FileChanged
import com.hyuchia.orbit.data.OutputFailed
import com.hyuchia.orbit.data.watched
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** How old a map left in the sending folder is before it's cleared away: older than any send takes. */
private const val STALE_MILLISECONDS = 60 * 60 * 1000L

/**
 * The sends from one send sheet, by the address of the device each goes
 * to, on `scope`, the sheet's: one at a time to each device, from the tap
 * on, while other devices can be sent to meanwhile.
 */
internal class Sends(private val scope: CoroutineScope) {
    /** Where each send is, by address. */
    val states = mutableStateMapOf<String, SendState>()

    /** Whether a send to `address` is under way: its map made ready, its code compared, or its answer awaited. */
    fun underWay(address: String): Boolean = when (states[address]) {
        is SendState.Sending, is SendState.Confirming, is SendState.Waiting -> true
        else -> false
    }

    /**
     * Starts `send` to `address`, which says where it is through the
     * function it's given, unless a send there is under way: the device
     * shows it's sending from the tap, while its map is made ready, so
     * another tap sends nothing more. False when one was under way.
     */
    fun start(address: String, send: suspend (onState: (SendState) -> Unit) -> Unit): Boolean {
        if (underWay(address)) {
            return false
        }

        states[address] = SendState.Sending
        scope.launch { send { state -> states[address] = state } }
        return true
    }

    /**
     * Starts a send of a map to `address`, as [start] does: `prepare` makes
     * its zip ready in a file of its own ([preparedBundle]), and `send` sends
     * it, the file going once it's sent, however that ends. When one of the
     * files going as they are changed after the person answered about it
     * ([FileChanged]), nothing goes: the device is free again, and
     * `askAgain` asks them about it as it is now, their answer starting the
     * send again; with no `askAgain`, as once asked again, it fails. False
     * when a send there was under way.
     */
    fun sendBundle(
        address: String,
        prepare: suspend () -> File,
        askAgain: (suspend () -> Unit)?,
        send: suspend (bundle: File, onState: (SendState) -> Unit) -> Unit,
    ): Boolean = start(address) { onState ->
        val bundle = try {
            prepare()
        } catch (changed: FileChanged) {
            if (askAgain == null) {
                onState(SendState.Failed(R.string.nearby_send_could_not_get_the_map_ready))
            } else {
                // Not sending while the person answers about the file as it is now: a tap meanwhile starts afresh.
                states.remove(address)
                askAgain()
            }

            return@start
        } catch (full: OutputFailed) {
            onState(SendState.Failed(R.string.nearby_send_no_room_here))
            return@start
        } catch (failed: IOException) {
            onState(SendState.Failed(R.string.nearby_send_could_not_get_the_map_ready))
            return@start
        }

        try {
            send(bundle, onState)
        } finally {
            bundle.delete()
        }
    }
}

/**
 * A map's zip made ready to send, in a file of its own in `folder` that
 * `write` fills, on the IO threads. However making it stops, failing in any
 * way or cancelled as the sheet closes, even once it's written but before
 * it's handed back, the file goes: the only one left is the one returned,
 * for the caller to delete once it's sent. First clears away what a send
 * that never finished left, when Orbit was stopped halfway.
 */
internal suspend fun preparedBundle(folder: File, write: suspend (OutputStream) -> Unit): File {
    var bundle: File? = null

    try {
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            folder.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - STALE_MILLISECONDS }.forEach { it.delete() }
            val file = File.createTempFile("map", ".zip", folder)
            bundle = file
            file.outputStream().watched().use { write(it) }
        }
    } catch (stopped: Throwable) {
        bundle?.delete()
        throw stopped
    }

    return checkNotNull(bundle)
}
