package com.hyuchia.orbit.map

import android.os.SystemClock
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.FolderSync
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.media.SoundPlayback
import com.hyuchia.orbit.media.VideoMaker
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.reminders.IdeaToShow
import com.hyuchia.orbit.study.StudyWrites
import com.hyuchia.orbit.ui.TimeWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What a map's view model needs from the rest of the app, besides the maps
 * and the settings. The app's [com.hyuchia.orbit.AppContainer] is the real
 * one; a test makes its own, leaving out what its map never reaches for.
 */
interface MapApp {
    /** The app's strings, for what's said away from the screen's own: an untitled idea's name, why a version was kept, the drops' messages. */
    val words: Words

    /** Private maps' lock: whether this map is behind it, and locking them all now from its menu. */
    val privateLock: PrivateLock

    /** Work that outlives the map's view model: the page's last word, and giving the page back, as the map closes. */
    val applicationScope: CoroutineScope

    /** A page to draw the map on: one when the map opens, and another after a page's process stopped. */
    fun takePage(): MapPage

    /** Takes back a page from [takePage] once the map is done with it. */
    fun givePage(page: MapPage)

    /** Plays the map's sounds, finding each one's file with `resolve`. */
    fun soundPlayback(scope: CoroutineScope, resolve: (String) -> Any?): SoundPlayback

    /** How times read in the app's language, for the message a restored version says. */
    fun timeWords(): TimeWords

    /** The phone's clipboard: ideas copied from the map, and text or a picture pasted onto it. */
    val clipboard: IdeasClipboard

    /** Pictures of the map, drawn for exports and shares. */
    val pictures: MapPictures

    /** Maps from files let go on the map, through its drops. */
    val importer: Importer

    /** The folder maps are kept in, asked to sync a study session's answers soon, for the other devices there. */
    val folderSync: FolderSync

    /** The answers given in study mode on their way to the maps' details, which outlive a map's screen. */
    val studyWrites: StudyWrites

    /** Where a map deleted from its own screen goes, for Home to offer it back with Undo. */
    val deletedMaps: SendChannel<TrashedMap>

    /** An idea a reminder opened its map at, for the map's screen to show once it's read: it takes it, as only it does. */
    val ideaToShow: MutableStateFlow<IdeaToShow?>

    /** Makes big videos smaller as they come in, where the phone can; a test's makes none. */
    val videoMaker: VideoMaker get() = VideoMaker.NONE

    /** Milliseconds since the phone started ([SystemClock.elapsedRealtime]), for a presentation's times; a test's clock is its own. */
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
