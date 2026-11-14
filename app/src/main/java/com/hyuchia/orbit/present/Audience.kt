package com.hyuchia.orbit.present

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AsTheyAreFiles
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.OrbitPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * A screen an audience watches while a map is presented: a second screen
 * beside the phone, or a TV the map is cast to. It shows the map and its
 * captions, and follows the presenter's steps; the phone shows notes.
 */
interface Audience {
    /** What the presenter sees the screen called: "HDMI screen", "Living room TV"; null for a TV with no name of its own. */
    val name: String?

    /** Whether the screen can play the video files kept in the map's folder on the phone: a TV can't reach them. */
    val playsMapVideos: Boolean get() = true

    /**
     * Whether the map's pictures go to the screen as files, as they do to a
     * TV, rather than only drawn on it: the presenter is asked first about
     * those that can't go clean ([com.hyuchia.orbit.data.Uncleaned]).
     */
    val carriesPictures: Boolean get() = false

    fun send(command: AudienceCommand)

    /** Hears how the video on the screen is going, from screens that say: a second screen and a TV. Browsers don't. */
    fun listen(listener: (MapMessage.MediaState) -> Unit) = Unit

    /**
     * Where the picture or video shown is, as the phone reckons it now that
     * a screen said how its video goes: for screens that join halfway, as
     * browsers do, to start where the room's big screen is. Times are on
     * [android.os.SystemClock.elapsedRealtime].
     */
    fun reckon(media: ShownMedia?) = Unit

    /**
     * What the presenter should hear about the screen: for a TV, when its
     * receiver is older than this map or this app (see [ReceiverWatch]).
     * Second screens and browsers run the app's own pages, so they never are.
     */
    val notice: StateFlow<ScreenNotice?> get() = noNotice

    /**
     * What went wrong on the screen, which loading the presentation there
     * again mends ([loadAgain]): a second screen's page stopped or didn't
     * load, or a TV missed something it was sent. Null while all's well, and
     * always for browsers, whose pages find the phone again by themselves.
     */
    val trouble: StateFlow<ScreenTrouble?> get() = noTrouble

    /** Gets the screen ready to show the presentation afresh, after [trouble]: the presentation then catches it up. */
    fun loadAgain() = Unit

    /** The presentation is over, or the screen went away. */
    fun close()
}

private val noNotice: StateFlow<ScreenNotice?> = MutableStateFlow(null)

private val noTrouble: StateFlow<ScreenTrouble?> = MutableStateFlow(null)

/** What went wrong on a screen an audience watches, for the presenter, and what mends it ([Audience.loadAgain]). */
enum class ScreenTrouble(@StringRes val message: Int, @StringRes val action: Int) {
    /** Android stopped the second screen's page, as it does to free memory: the screen shows nothing. */
    Stopped(R.string.present_screen_stopped, R.string.map_load_it_again),

    /** The second screen's page didn't load. */
    DidNotLoad(R.string.present_screen_did_not_load, R.string.map_load_it_again),

    /** Something sent to the TV didn't reach it, so it may not show what the presenter does. */
    Missed(R.string.present_tv_missed, R.string.present_send_it_again),
}

/**
 * A second screen attached to the phone, by cable or wirelessly: Android
 * shows a [Presentation] on it, holding a page with the audience's view.
 * Nothing on it can be touched, so its videos play with sound at once, and
 * it says how each one is going. When its page stops, as Android frees
 * memory, or doesn't load, the presenter hears it ([trouble]), and loading
 * it again puts a new page in its place, which `makePage` makes in the
 * screen's context.
 */
class ScreenAudience(
    context: Context,
    display: Display,
    private val makePage: (Context) -> OrbitPage = context.container.pages::audiencePage,
) : Audience {
    private val screen = Presentation(context, display)
    private var page: OrbitPage = makePage(screen.context)
    private val troubled = MutableStateFlow<ScreenTrouble?>(null)
    private var mediaListener: ((MapMessage.MediaState) -> Unit)? = null

    override val name: String = display.name

    override val trouble: StateFlow<ScreenTrouble?> = troubled.asStateFlow()

    init {
        hear(page)
        screen.setContentView(page.webView)

        try {
            screen.show()
        } catch (gone: WindowManager.InvalidDisplayException) {
            Logs.warn(TAG, "The second screen went away before the presentation showed", gone)
        }
    }

    override fun send(command: AudienceCommand) = page.send(command)

    override fun listen(listener: (MapMessage.MediaState) -> Unit) {
        mediaListener = listener
    }

    /** A new page in place of the one that stopped or didn't load: it waits for the presentation to catch it up. */
    override fun loadAgain() {
        val gone = page
        page = makePage(screen.context)
        hear(page)
        screen.setContentView(page.webView)
        gone.destroy()
        troubled.value = null
    }

    override fun close() {
        screen.dismiss()
        page.destroy()
    }

    /** Hears what `page` says: how its video goes, for the presentation, and that it stopped or didn't load, for the presenter. */
    private fun hear(page: OrbitPage) {
        page.listener = { message ->
            when (message) {
                is MapMessage.MediaState -> mediaListener?.invoke(message)
                MapMessage.Stopped -> troubled.value = ScreenTrouble.Stopped
                MapMessage.DidNotLoad -> troubled.value = ScreenTrouble.DidNotLoad
                else -> Unit
            }
        }
    }

    private companion object {
        const val TAG = "ScreenAudience"
    }
}

/**
 * The second screen the phone can present on, by id: one for presentations,
 * such as a TV on HDMI or a wireless display. Null when there's none.
 */
@Composable
fun rememberSecondScreen(): Int? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(DisplayManager::class.java) }
    fun current(): Int? = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()?.displayId
    var screen by remember { mutableStateOf(current()) }

    DisposableEffect(manager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                screen = current()
            }

            override fun onDisplayRemoved(displayId: Int) {
                screen = current()
            }

            override fun onDisplayChanged(displayId: Int) {
                screen = current()
            }
        }

        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterDisplayListener(listener) }
    }

    return screen
}

/**
 * The audience for a presentation of the map in `mapFolder`: a TV the phone
 * is casting to, else a second screen, else none. Made and closed as screens
 * come and go. A TV gets the map's pictures that can't go clean only when
 * they're among `asTheyAre`, as the presenter chose, as they were then.
 */
@Composable
fun rememberAudience(mapFolder: File, asTheyAre: () -> AsTheyAreFiles, onAudience: (Audience?) -> Unit): Audience? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secondScreen = rememberSecondScreen()
    val tv = tvCasting.rememberTv()
    var audience by remember { mutableStateOf<Audience?>(null) }

    DisposableEffect(secondScreen, tv) {
        val display = secondScreen?.let { context.getSystemService(DisplayManager::class.java).getDisplay(it) }

        val made = when {
            tv != null -> tv.audience(mapFolder, scope, asTheyAre)
            display != null -> ScreenAudience(context, display)
            else -> null
        }

        audience = made
        onAudience(made)

        onDispose {
            onAudience(null)
            made?.close()
            audience = null
        }
    }

    return audience
}
