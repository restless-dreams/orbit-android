package com.hyuchia.orbit.nearby

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * `output`, written only while `job` is active. Once it's cancelled, as
 * the server stopping cancels its handlers, the next write throws its
 * cancellation instead: blocking work writing a big file, such as a map's
 * zip, stops there rather than at its end.
 */
internal class WhileActive(output: OutputStream, private val job: Job) : FilterOutputStream(output) {
    override fun write(byte: Int) {
        job.ensureActive()
        out.write(byte)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        job.ensureActive()
        out.write(bytes, offset, length)
    }
}
