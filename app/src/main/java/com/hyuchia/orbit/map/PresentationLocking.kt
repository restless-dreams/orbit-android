package com.hyuchia.orbit.map

/** What presenting a private map does as private maps lock and unlock. */
internal enum class LockingStep {
    Nothing,

    /** The presentation stops: its audience, in browsers, on a TV or a second screen, stops seeing the map. */
    Stop,

    /** Unlocked after a presentation stopped: presenting again is offered, rather than resumed unasked. */
    OfferAgain,
}

/**
 * A private map's presentation, as private maps lock and unlock. Locking
 * stops it, a rehearsal too, wherever its audience watches; unlocked, a
 * presentation that stopped is offered again. A rehearsal isn't: its
 * summary is there instead.
 */
internal class PresentationLocking {
    private var stopped = false

    /** The map is `locked` now or not, while a presentation goes on (`presenting`), or a rehearsal (`rehearsing`). */
    fun changed(locked: Boolean, presenting: Boolean, rehearsing: Boolean = false): LockingStep = when {
        locked && (presenting || rehearsing) -> {
            stopped = presenting && !rehearsing
            LockingStep.Stop
        }
        !locked && stopped -> {
            stopped = false
            LockingStep.OfferAgain
        }
        else -> LockingStep.Nothing
    }
}
