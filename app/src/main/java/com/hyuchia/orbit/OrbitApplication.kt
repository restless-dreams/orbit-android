package com.hyuchia.orbit

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.annotation.OptIn
import androidx.core.net.toUri
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
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.ThisDevice
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.data.Updates
import com.hyuchia.orbit.map.MapApp
import com.hyuchia.orbit.map.MapClipboard
import com.hyuchia.orbit.media.SoundPlayback
import com.hyuchia.orbit.media.StillAnimations
import com.hyuchia.orbit.nearby.NearbyHost
import com.hyuchia.orbit.page.MapPage
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.page.OrbitBuild
import com.hyuchia.orbit.page.OrbitPage
import com.hyuchia.orbit.page.PagePool
import com.hyuchia.orbit.privacy.PrivateLock
import com.hyuchia.orbit.privacy.PrivateMaps
import com.hyuchia.orbit.settings.MapBackups
import com.hyuchia.orbit.ui.TimeWords
import com.hyuchia.orbit.widget.offerWidgetPreview
import com.hyuchia.orbit.widget.updateMapWidgets
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    val maps = MapRepository(
        mapsDirectory,
        File(context.filesDir, "trash"),
        AssetStarterMaps(context.applicationContext),
        words,
        room = { file -> room(context, file) },
        installed = installedAt(context),
        clockTrusted = ::clockMatchesNetwork,
    )
    val settings = SettingsRepository(context)
    val pages = PagePool(context, mapsDirectory)
    override val pictures = MapPictures(pages, context.cacheDir, OrbitBuild.bundled(context)?.commit.orEmpty())
    override val importer = Importer(maps, pages, words)

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

    /** New versions, for the copies no store updates: the APK from the website and GitHub. */
    val updates = Updates(context, settings)

    /** Maps hidden on Home, in widgets, shortcuts and Recents, and opened with the phone's screen lock. */
    val privateMaps = PrivateMaps(context.applicationContext, maps, settings, applicationScope)

    override val privateLock: PrivateLock get() = privateMaps.lock

    override fun takePage(): MapPage = pages.take()

    // Only pages from takePage come back, and the pool made each of them: an OrbitPage.
    override fun givePage(page: MapPage) = pages.give(page as OrbitPage)

    override fun soundPlayback(scope: CoroutineScope, resolve: (String) -> Any?): SoundPlayback = SoundPlayback(application, scope, resolve)

    override fun timeWords(): TimeWords = TimeWords.of(application)

    init {
        // A private map shown to computers stops being served as it locks.
        nearby.hidden = { id -> maps.summary(id)?.let { privateMaps.view.value.hides(it) } == true }
    }
}

class OrbitApplication : Application(), SingletonImageLoader.Factory, WorkConfiguration.Provider {
    lateinit var container: AppContainer
        private set

    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
    private var locales = LocaleList.getEmptyLocaleList()
    private var fontScale = 1f

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
        container.folderSync.start(ProcessLifecycleOwner.get().lifecycle)
        container.privateMaps.start(ProcessLifecycleOwner.get().lifecycle)
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
            container.applicationScope.launch { updateMapWidgets(this@OrbitApplication) }
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
     * Draws the maps on the home screen again once a change settles: an edit,
     * ideas caught, a map renamed or deleted; and when the wallpaper's colors
     * are turned on or off, which the pictures are drawn in. Once the maps
     * are read, the launcher's list of widgets gets a preview of them.
     */
    @OptIn(FlowPreview::class)
    private fun keepWidgetsCurrent() {
        container.applicationScope.launch {
            container.maps.maps.filterNotNull().first()
            runCatching { offerWidgetPreview(this@OrbitApplication) }
        }

        container.applicationScope.launch {
            container.maps.maps
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .debounce(WIDGET_SETTLE)
                .collect { updateMapWidgets(this@OrbitApplication) }
        }

        container.applicationScope.launch {
            container.settings.settings
                .map { it.dynamicColor }
                .distinctUntilChanged()
                .drop(1)
                .collect { updateMapWidgets(this@OrbitApplication) }
        }
    }
}

/** How long the maps stay still before the widgets are drawn again, so typing doesn't redraw them on every letter. */
private val WIDGET_SETTLE = 2.seconds

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
