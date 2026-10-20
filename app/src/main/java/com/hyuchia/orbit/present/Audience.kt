package com.hyuchia.orbit.present

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.container
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.OrbitPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /** The presentation is over, or the screen went away. */
    fun close()
}

private val noNotice: StateFlow<ScreenNotice?> = MutableStateFlow(null)

/**
 * A second screen attached to the phone, by cable or wirelessly: Android
 * shows a [Presentation] on it, holding a page with the audience's view.
 * Nothing on it can be touched, so its videos play with sound at once, and
 * it says how each one is going.
 */
class ScreenAudience(context: Context, display: Display) : Audience {
    private val screen = Presentation(context, display)
    private val page: OrbitPage = context.container.pages.audiencePage(screen.context)

    override val name: String = display.name

    init {
        screen.setContentView(page.webView)

        try {
            screen.show()
        } catch (gone: WindowManager.InvalidDisplayException) {
            Logs.warn(TAG, "The second screen went away before the presentation showed", gone)
        }
    }

    override fun send(command: AudienceCommand) = page.send(command)

    override fun listen(listener: (MapMessage.MediaState) -> Unit) {
        page.listener = { message ->
            if (message is MapMessage.MediaState) {
                listener(message)
            }
        }
    }

    override fun close() {
        screen.dismiss()
        page.destroy()
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
 * come and go.
 */
@Composable
fun rememberAudience(mapFolder: File, onAudience: (Audience?) -> Unit): Audience? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secondScreen = rememberSecondScreen()
    val tv = tvCasting.rememberTv()
    var audience by remember { mutableStateOf<Audience?>(null) }

    DisposableEffect(secondScreen, tv) {
        val display = secondScreen?.let { context.getSystemService(DisplayManager::class.java).getDisplay(it) }

        val made = when {
            tv != null -> tv.audience(mapFolder, scope)
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
