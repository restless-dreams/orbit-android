package com.hyuchia.orbit.present

import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage

/**
 * How long after the presenter's last press a screen's word on the video
 * waits to count: one it sent before it heard the press would undo it.
 */
internal const val REPORT_SETTLE_MILLISECONDS = 1_500L

/**
 * An idea's picture or video, shown to the audience big over the map, and
 * where the video is as the phone reckons it: `at` seconds in when the
 * clock read `since`, and on from there while it plays, up to its end once
 * its `duration` is known. Times are milliseconds on one clock, such as
 * [android.os.SystemClock.elapsedRealtime]; `asked` is when the presenter
 * last changed it.
 */
data class ShownMedia(
    val id: String,
    val kind: MediaKind,
    val playing: Boolean,
    val at: Double,
    val since: Long,
    val duration: Double? = null,
    val ended: Boolean = false,
    val asked: Long = since,
) {
    val isVideo: Boolean get() = kind == MediaKind.Video

    /** Seconds into the video at `now`. */
    fun position(now: Long): Double {
        val played = if (playing) (now - since).coerceAtLeast(0L) / 1_000.0 else 0.0
        val position = at + played
        return duration?.let { minOf(position, it) } ?: position
    }

    /** Whether it's at its end at `now`. */
    fun atEnd(now: Long): Boolean {
        val length = duration
        return ended || (playing && length != null && at + (now - since).coerceAtLeast(0L) / 1_000.0 >= length)
    }

    /** Whether it plays at `now`: started, and not at its end yet. */
    fun playingAt(now: Long): Boolean = playing && !atEnd(now)

    /** What shows it on a screen as it is at `now`, as for one that joins halfway. */
    fun command(now: Long): AudienceCommand.Media = AudienceCommand.Media(id, kind, playingAt(now), position(now))

    /** The presenter plays it, or pauses it, at `now`: from where it is, or from the start once it's at its end. */
    fun played(playing: Boolean, now: Long): ShownMedia {
        val from = if (playing && atEnd(now)) 0.0 else position(now)
        return copy(playing = playing, at = from, since = now, ended = false, asked = now)
    }

    /** The presenter moves it to `seconds` at `now`, playing or paused as it was. */
    fun moved(seconds: Double, now: Long): ShownMedia = copy(at = seconds.coerceAtLeast(0.0), since = now, ended = false, asked = now)

    /**
     * What a screen says of the video at `now` corrects the reckoning: where
     * it is, whether it plays, and how long it is. Until the screen has had
     * time to hear the presenter's last press, only its length counts.
     */
    fun reported(report: MapMessage.MediaState, now: Long): ShownMedia {
        if (report.id != id || !isVideo) {
            return this
        }

        val length = report.duration?.takeIf { it > 0.0 && it.isFinite() } ?: duration

        if (now - asked < REPORT_SETTLE_MILLISECONDS || !report.time.isFinite()) {
            return copy(duration = length)
        }

        return copy(playing = report.playing && !report.ended, at = report.time.coerceAtLeast(0.0), since = now, duration = length, ended = report.ended)
    }
}

/**
 * What shows over the map once `command` is sent at `now`, from this: the
 * phone reckons where a video is from what it tells the screens, as a
 * screen that only hears it would. A step, a new map and the end go back
 * to the map.
 */
fun ShownMedia?.after(command: AudienceCommand, now: Long): ShownMedia? = when (command) {
    is AudienceCommand.Media -> ShownMedia(command.id, command.kind, command.playing, command.at, since = now)
    is AudienceCommand.Playback -> this?.played(command.playing, now)
    is AudienceCommand.Seek -> this?.moved(command.at, now)
    is AudienceCommand.Captions -> this
    is AudienceCommand.Show, is AudienceCommand.Step, AudienceCommand.CloseMedia, AudienceCommand.End -> null
}
