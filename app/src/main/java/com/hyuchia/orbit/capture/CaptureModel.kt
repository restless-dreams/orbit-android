package com.hyuchia.orbit.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyuchia.orbit.data.MapSummary
import kotlinx.coroutines.launch

/**
 * What quick capture's sheet is adding, and how it went, kept through a
 * sheet made again, as when the phone turns: an add once begun goes on
 * until it's done, and the sheet made after it shows it, then says how it
 * went. One at a time, and once the ideas are in, they're in: Add adds
 * nothing more, so they never go twice, and the sheet closes, saying so.
 * Dismissing the sheet stops an add that's still going, as ever.
 * `capture` adds `ideas` ideas, `text`, to a map, and says how it went
 * ([captureInto]).
 */
internal class CaptureModel(private val capture: suspend (target: MapSummary, ideas: Int, text: String) -> CaptureOutcome) : ViewModel() {
    /** Ideas are being added: Add waits, saying so. */
    var adding by mutableStateOf(false)
        private set

    /** What went wrong adding them, under the field until they're tried again: they're still there. */
    var problem by mutableStateOf<String?>(null)

    /** What adding them says once they're in: the sheet says it, and closes. */
    var added by mutableStateOf<String?>(null)
        private set

    /** Adds `text`, `ideas` ideas, to `target`, unless ideas are being added already, or are in. */
    fun add(target: MapSummary, ideas: Int, text: String) {
        if (adding || added != null) {
            return
        }

        adding = true
        problem = null

        viewModelScope.launch {
            when (val outcome = capture(target, ideas, text)) {
                is CaptureOutcome.Added -> added = outcome.said
                is CaptureOutcome.NotAdded -> {
                    problem = outcome.problem
                    adding = false
                }
            }
        }
    }
}
