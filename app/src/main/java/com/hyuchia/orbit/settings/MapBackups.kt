package com.hyuchia.orbit.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Export all and Import maps, run on the app's scope: leaving Settings
 * neither cuts a zip short nor stops maps coming in. One runs at a time,
 * and what it came to waits here until Settings has said it, so a problem
 * is never lost to a screen that closed.
 */
class MapBackups(private val scope: CoroutineScope) {
    enum class Work { Exporting, Importing }

    /** What a backup came to: what Settings says, and whether that's a problem. Each is its own, even with the same words. */
    class Outcome(val said: String, val problem: Boolean)

    private val running = MutableStateFlow<Work?>(null)
    private val outcomes = MutableStateFlow<Outcome?>(null)

    /** What's under way, if anything. */
    val work: StateFlow<Work?> = running.asStateFlow()

    /** What the last backup came to, until [said]. */
    val outcome: StateFlow<Outcome?> = outcomes.asStateFlow()

    /** Starts `run`, unless another backup is under way: then it says false, and leaves that one be. */
    fun start(work: Work, run: suspend () -> Outcome): Boolean {
        if (!running.compareAndSet(null, work)) {
            return false
        }

        scope.launch {
            try {
                outcomes.value = run()
            } finally {
                running.value = null
            }
        }

        return true
    }

    /** Settings has said `outcome`, which goes; one that came after it stays to be said. */
    fun said(outcome: Outcome) {
        outcomes.compareAndSet(outcome, null)
    }
}
