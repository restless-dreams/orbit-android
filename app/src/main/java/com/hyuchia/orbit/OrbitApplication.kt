package com.hyuchia.orbit

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.work.Configuration as WorkConfiguration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import com.hyuchia.orbit.capture.QuickCapture
import com.hyuchia.orbit.data.AssetStarterMaps
import com.hyuchia.orbit.data.CrashLog
import com.hyuchia.orbit.data.DocumentFolder
import com.hyuchia.orbit.data.FolderSync
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapEdits
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.MapTextEditor
import com.hyuchia.orbit.data.OUTGOING_COPIES
import com.hyuchia.orbit.data.OutgoingCopies
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.ThisDevice
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.data.Updates
import com.hyuchia.orbit.map.MapApp
import com.hyuchia.orbit.map.MapClipboard
import com.hyuchia.orbit.media.SketchPictures
import com.hyuchia.orbit.media.SoundPlayback
import com.hyuchia.orbit.media.StillAnimations
import com.hyuchia.orbit.media.TransformerVideoMaker
import com.hyuchia.orbit.media.VideoMaker
import com.hyuchia.orbit.nearby.NearbyHost
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.page.OrbitBuild
import com.hyuchia.orbit.page.OrbitPage
import com.hyuchia.orbit.page.PagePool
import com.hyuchia.orbit.privacy.PrivateCopies
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.privacy.PrivateMaps
import com.hyuchia.orbit.privacy.callOffPicturesAsPrivateMapsLock
import com.hyuchia.orbit.reminders.AndroidReminderAlarms
import com.hyuchia.orbit.reminders.AndroidReminderNotices
import com.hyuchia.orbit.reminders.IdeaToShow
import com.hyuchia.orbit.reminders.Reminders
import com.hyuchia.orbit.settings.MapBackups
import com.hyuchia.orbit.study.AnswersUnsaved
import com.hyuchia.orbit.study.StudyWrites
import com.hyuchia.orbit.ui.ShortcutMatching
import com.hyuchia.orbit.ui.TimeWords
import com.hyuchia.orbit.ui.matchShortcuts
import com.hyuchia.orbit.ui.shortcutLabels
import com.hyuchia.orbit.widget.WidgetRedrawing
import com.hyuchia.orbit.widget.offerWidgetPreview
import com.hyuchia.orbit.widget.updateMapWidgets
import com.hyuchia.orbit.widget.widgetPreviewOffers
import com.hyuchia.orbit.widget.widgetRedraws
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.DateTimeException
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/** What the app's screens share: the maps, the settings, and the pages that draw maps. */
class AppContainer(context: Context, /** Reports of crashes, kept on the phone. */ val crashes: CrashLog) : MapApp {
    private val mapsDirectory = File(context.filesDir, "maps")

    /** The app itself, for what plays away from a screen's own: a map's sounds go on as its panels come and go. */
    val application: Context = context.applicationContext

    /** The app's strings in the language it's in, for what it says away from a screen: in the maps' files, and in messages about them. */
    override val words: Words = ResourceWords(context.applicationContext)

    val settings = SettingsRepository(context)

    val maps = MapRepository(
        mapsDirectory,
        File(context.filesDir, "trash"),
        AssetStarterMaps(context.applicationContext),
        words,
        room = { file -> room(context, file) },
        installed = installedAt(context),
        clockTrusted = ::clockMatchesNetwork,
        syncFolder = { settings.settings.first().syncFolder },
    )
    val pages = PagePool(context, mapsDirectory)
    override val pictures = MapPictures(pages, context.cacheDir, OrbitBuild.bundled(context)?.commit.orEmpty())
    // A locked private map is found by its identity alone as a zip of every map comes in: a zip of guessed text tells nothing of it.
    override val importer = Importer(maps, pages, words, locked = { map -> privateMaps.hides(map) })

    override val videoMaker: VideoMaker = TransformerVideoMaker(context.applicationContext)

    /** Maps renamed, tagged and copied from Home, as Orbit reads them, on the spare page. */
    val edits = MapEdits(maps, MapTextEditor.onPages(pages), words)

    /** Ideas added to maps without opening them: from the tile, a widget, or another app. */
    val capture = QuickCapture(pages, maps, settings)

    /** Copying ideas, and pasting text and pictures onto maps. */
    override val clipboard = MapClipboard(context.applicationContext)

    /** The tag Home shows only the maps of, if one is picked, while the app runs; maps made meanwhile take it. */
    val shownTag = MutableStateFlow<String?>(null)

    /**
     * For work that outlives a screen, such as saving the last edit as a map
     * closes. A failure there is logged, never the app's end: a full storage
     * refusing that save mustn't close Orbit too.
     */
    override val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, failure -> Logs.warn("Orbit", "Background work failed", failure) },
    )

    /** Export all and Import maps in Settings, which go on when Settings closes. */
    val backups = MapBackups(applicationScope)

    /** This device for others on the local network, while Orbit is open: maps sent here, maps shown to computers. */
    val nearby = NearbyHost(context.applicationContext, maps, applicationScope)

    /** This device, as it describes itself to the others that keep their maps in the same folder. */
    private val thisDevice = ThisDevice(context.applicationContext) { nearby.nameFor(settings.settings.first().nearbyName) }

    /** The folder the maps are kept in as markdown files, when the user chose one. */
    override val folderSync = FolderSync(maps, settings, applicationScope, File(context.filesDir, "folder-notes.json"), words, MapTextEditor.onPages(pages), device = thisDevice::describe) { address ->
        DocumentFolder(context.contentResolver, address.toUri(), words)
    }

    /** Maps deleted from their own screen, for the home screen to offer back with Undo once it shows. */
    override val deletedMaps = Channel<TrashedMap>(Channel.CONFLATED)

    /** Study mode's answers on their way to the maps' details: one the storage can't take is tried again while Orbit runs. */
    override val studyWrites = StudyWrites(applicationScope, maps::changeStudy) { if (maps.writable.value) AnswersUnsaved.Storage else AnswersUnsaved.Newer }

    /** New versions, for the copies no store updates: the APK from the website and GitHub. */
    val updates = Updates(context, settings)

    /** Maps hidden on Home, in widgets, shortcuts and Recents, and opened with the phone's screen lock. */
    val privateMaps = PrivateMaps(context.applicationContext, maps, settings, applicationScope)

    override val privateLock: PrivateLock get() = privateMaps.lock

    /** Files or text shared to Orbit for the map that's open, which its screen adds to its idea in focus once the map's ready. */
    val sharedFiles = PendingShares()

    /** What comes into Orbit, done in the app's scope rather than a screen's (see [Intake]). */
    val intake = Intake(maps, importer, context.contentResolver, applicationScope, words, ImportRequests(File(context.filesDir, "requests")))

    /** Copies of private maps' files opened in other apps, deleted as private maps lock. */
    val privateCopies = PrivateCopies(
        applicationScope,
        privateMaps.lock.newlyLocked,
        File(context.cacheDir, "exports"),
        servedAsTheyAre = OutgoingCopies.asTheyAreIn(File(context.cacheDir, OUTGOING_COPIES)),
    )

    /** Ideas' dates, reminding on this device as they come. */
    val reminders = Reminders(File(context.filesDir, "reminders.json"), AndroidReminderAlarms(context.applicationContext), AndroidReminderNotices(context.applicationContext, words))

    /** An idea a reminder opens its map at, until the map's screen shows it. */
    override val ideaToShow = MutableStateFlow<IdeaToShow?>(null)

    override fun takePage(): MapPage = pages.take()

    // Only pages from takePage come back, and the pool made each of them: an OrbitPage.
    override fun givePage(page: MapPage) = pages.give(page as OrbitPage)

    override fun soundPlayback(scope: CoroutineScope, resolve: (String) -> Any?): SoundPlayback = SoundPlayback(application, scope, resolve)

    override fun timeWords(): TimeWords = TimeWords.of(application)

    init {
        // What was answered but not taken in, as Orbit stopped, is taken in now.
        intake.resume()

        // A private map shown to computers stops being served as it locks.
        nearby.hidden = { id -> maps.summary(id)?.let { privateMaps.view.value.hides(it) } == true }

        // Pictures of private maps being drawn as they lock are called off, on the main thread, as the pages' work is.
        CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main).callOffPicturesAsPrivateMapsLock(privateMaps.lock, maps::summary, pages::revokeJobs)
    }
}

/**
 * Files shared to Orbit for map `mapId`, open as they came, or `text`
 * shared to an idea with no file: they join its idea in focus, the files
 * among its files, the text as ideas under it.
 */
data class SharedFiles(val mapId: String, val uris: List<Uri>, val text: String? = null)

/**
 * Files and text shared to Orbit for maps that are open ([SharedFiles]),
 * each waiting until its map's screen has the map ready to take it, however
 * soon it came: before the screen began to listen, as Android brings Orbit
 * back with a share, or while the map's page is still loading. Each is
 * taken once, and goes with its map when it closes.
 */
class PendingShares {
    private val pending = MutableStateFlow<List<SharedFiles>>(emptyList())
    private val offered = MutableStateFlow(0L)

    /** What waits, oldest first. */
    val waiting: StateFlow<List<SharedFiles>> = pending.asStateFlow()

    /**
     * How many shares were ever offered, which changes with each, even one
     * like a share taken a moment before: a screen that hears it takes what
     * waits for its map, however the changes of [waiting] reached it.
     */
    val offers: StateFlow<Long> = offered.asStateFlow()

    /** `shared` waits for its map. */
    fun offer(shared: SharedFiles) {
        pending.update { it + shared }
        offered.update { it + 1 }
    }

    /** What waits for map `id`, oldest first, which waits no more. */
    fun take(id: String): List<SharedFiles> {
        var taken = emptyList<SharedFiles>()

        pending.update { list ->
            taken = list.filter { it.mapId == id }
            list.filterNot { it.mapId == id }
        }

        return taken
    }

    /** What waits for maps that aren't among `open` any more goes. */
    fun keepFor(open: Set<String>) {
        pending.update { list -> list.filter { it.mapId in open } }
    }
}

class OrbitApplication : Application(), SingletonImageLoader.Factory, WorkConfiguration.Provider {
    lateinit var container: AppContainer
        private set

    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
    private var locales = LocaleList.getEmptyLocaleList()
    private var fontScale = 1f

    /** The home screen's widgets, drawn again as the maps change, and as Orbit comes back after a drawing failed. */
    private val widgetRedrawing = WidgetRedrawing(draw = { updateMapWidgets(this) })

    /** The launcher's shortcuts to maps, matched to the maps as they change, and as Orbit comes back after the launcher refused. */
    private val shortcutMatching = ShortcutMatching { labels -> matchShortcuts(this, labels) }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // First, so a crash while the rest starts is kept too.
        val crashes = CrashLog(File(filesDir, "crashes"), about(), hidden = ::mapNames)
        crashes.install()

        // Media3 logs its errors, which can name a map's folder or a video's or sound's site: not in release builds,
        // whichever of its players (a video, a sound, a live photo's motion) plays first.
        if (!BuildConfig.DEBUG) {
            Log.setLogLevel(Log.LOG_LEVEL_OFF)
        }

        container = AppContainer(this, crashes)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        locales = resources.configuration.locales
        fontScale = resources.configuration.fontScale
        keepWidgetsCurrent()
        keepShortcutsCurrent()
        keepRemindersCurrent()
        container.folderSync.start(ProcessLifecycleOwner.get().lifecycle)
        container.privateMaps.start(ProcessLifecycleOwner.get().lifecycle)

        // Back on screen, where Android takes an app's updates again: widgets and shortcuts left behind catch up.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.applicationScope.launch { widgetRedrawing.cameBack() }
                    container.applicationScope.launch { shortcutMatching.cameBack() }
                }
            },
        )
    }

    /**
     * Coil's, which draws every picture in the app: animated GIFs and WebPs
     * play with Android's own decoder, unless Android's animations are off
     * ([StillAnimations]). Pictures from the web come through OkHttp, which
     * coil-network-okhttp adds on its own.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // Orbit's own sketches, drawn as their canvas draws them: Coil reads no other SVG.
                add(SketchPictures())
                add(StillAnimations())
                add(AnimatedImageDecoder.Factory())
            }
            .build()

    /**
     * WorkManager, which Glance's widgets run on, starts when a widget first
     * needs it rather than with Orbit (see the manifest). A database it can't
     * open, as on a full disk, is logged rather than stopping Orbit: the maps
     * still open, and the widgets wait.
     */
    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setInitializationExceptionHandler { failure -> Logs.warn("Orbit", "WorkManager couldn't start", failure) }
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // Out of sight, the spare page that makes the next map open at once isn't worth its memory.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            container.pages.release()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Widgets draw their maps in the home screen's colors and describe them in the app's language:
        // night and day need pictures of their own, a new language needs new words, and larger text may leave
        // a row's progress without them.
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

        if (night != nightMode || newConfig.locales != locales || newConfig.fontScale != fontScale) {
            nightMode = night
            locales = newConfig.locales
            fontScale = newConfig.fontScale
            container.applicationScope.launch { widgetRedrawing.redraw() }
        }
    }

    /** The app, the phone and its Android, for crash reports. */
    private fun about(): String =
        "Orbit ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE} build\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) on ${Build.MANUFACTURER} ${Build.MODEL}"

    /** What crash reports never show: every map's name and title, the trash's too. */
    private fun mapNames(): Collection<String> {
        val names = mutableSetOf<String>()
        File(filesDir, "maps").list()?.let(names::addAll)

        if (::container.isInitialized) {
            container.maps.maps.value?.forEach { names += it.id; names += it.title }
            container.maps.trash.value.forEach { names += it.id; names += it.title }
        }

        return names
    }

    /**
     * Reads the maps' reminders again whenever a map's text changes, wherever
     * the change came from, and as reminders are turned on or off. Without the
     * maps read (Orbit started for an alarm, say), the reminders kept from the
     * last reading ring. A map made private has its reminders that show say
     * nothing of it at once, as its widgets do.
     */
    private fun keepRemindersCurrent() {
        container.applicationScope.launch {
            // The private maps as the reminders were last read: one made private since is read at once.
            var privateRead: Set<String>? = null

            combine(container.maps.maps.filterNotNull(), container.settings.settings.map { it.reminders }.distinctUntilChanged()) { maps, on -> maps to on }
                .collectLatest { (maps, on) ->
                    val private = maps.filter { it.metadata.isPrivate }.mapTo(HashSet()) { it.id }

                    // Once the maps are still: a change meanwhile starts this over.
                    if (privateRead?.containsAll(private) != false) {
                        delay(REMINDERS_SETTLE)
                    }

                    container.reminders.follow(maps, { id -> runCatching { container.maps.read(id) }.getOrNull() }, on)
                    privateRead = private
                }
        }
    }

    /**
     * Keeps the launcher's shortcuts to maps in line with the maps, whatever
     * changed them: renamed on Home, deleted or restored, by Orbit or in the
     * folder maps are kept in, made private or given a cover ([matchShortcuts]);
     * what the launcher refused goes again as Orbit comes back on screen.
     */
    private fun keepShortcutsCurrent() {
        container.applicationScope.launch {
            shortcutMatching.follow(container.maps.maps.filterNotNull().map { shortcutLabels(resources, it) })
        }
    }

    /**
     * Draws the maps on the home screen again once a change settles: an edit,
     * ideas caught, a map renamed or deleted; at once as a map becomes
     * private; and when the wallpaper's colors are turned on or off, which
     * the pictures are drawn in. A drawing that fails is tried again, and
     * once more as Orbit comes back on screen ([WidgetRedrawing]). Once the
     * maps are read, and again as which are private changes, the launcher's
     * list of widgets gets a preview of them ([widgetPreviewOffers]).
     */
    private fun keepWidgetsCurrent() {
        container.applicationScope.launch {
            widgetPreviewOffers(container.maps.maps).collectLatest { offerWidgetPreview(this@OrbitApplication) }
        }

        container.applicationScope.launch {
            widgetRedrawing.follow(widgetRedraws(container.maps.maps, WIDGET_SETTLE))
        }

        container.applicationScope.launch {
            container.settings.settings
                .map { it.dynamicColor }
                .distinctUntilChanged()
                .drop(1)
                .collect { widgetRedrawing.redraw() }
        }
    }
}

/** How long the maps stay still before the widgets are drawn again, so typing doesn't redraw them on every letter. */
private val WIDGET_SETTLE = 2.seconds

/** How long the maps stay still before their reminders are read again: a date set, not each letter typed. */
private val REMINDERS_SETTLE = 1.seconds

val Context.container: AppContainer get() = (applicationContext as OrbitApplication).container

/** How many bytes the system can make room for where `file` is, clearing caches if it must; as many as it likes when it can't say. */
/** When Orbit was installed on this phone, or 0 when Android doesn't say. */
private fun installedAt(context: Context): Long = try {
    context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
} catch (unknown: PackageManager.NameNotFoundException) {
    0
}

/**
 * Whether the phone's clock agrees with the network's time, to a day: on
 * Android 13 and later, which know the network's time. Without it, the clock
 * is all there is, and it's trusted.
 */
private fun clockMatchesNetwork(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }

    val network = try {
        SystemClock.currentNetworkTimeClock().millis()
    } catch (unknown: DateTimeException) {
        return true
    }

    return abs(network - System.currentTimeMillis()) < CLOCK_TOLERANCE
}

/** How far the phone's clock may be from the network's time and still age the history and the trash. */
private const val CLOCK_TOLERANCE = 24 * 60 * 60 * 1000L

private fun room(context: Context, file: File): Long = try {
    val storage = context.getSystemService(StorageManager::class.java)
    storage.getAllocatableBytes(storage.getUuidForPath(file))
} catch (unknown: IOException) {
    Long.MAX_VALUE
}
