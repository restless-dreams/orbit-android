package com.hyuchia.orbit

import android.content.Context
import java.io.File
import java.util.UUID

/** Where Orbit's own intents that open a map carry [OpenMark]. */
const val EXTRA_OPEN_MARK = "mark"

/**
 * This install's mark on the intents Orbit makes to open a map: its
 * shortcuts' and its widgets'. Another app can send the same action with a
 * map's id, but not the mark, so it can't learn that a hidden private map is
 * there: without it, one is opened only while private maps show. Kept where
 * nothing backs it up, and made once.
 */
object OpenMark {
    @Volatile
    private var mark: String? = null

    fun of(context: Context): String = mark ?: synchronized(this) {
        mark ?: read(File(context.noBackupFilesDir, "open-mark")).also { mark = it }
    }

    private fun read(file: File): String {
        file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.length >= 32 }?.let { return it }
        val made = UUID.randomUUID().toString()
        file.writeText(made)
        return made
    }
}
