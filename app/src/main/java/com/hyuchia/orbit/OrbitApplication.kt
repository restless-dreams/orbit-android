package com.hyuchia.orbit

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.storage.StorageManager
import androidx.core.net.toUri
import androidx.lifecycle.ProcessLifecycleOwner
import com.hyuchia.orbit.capture.QuickCapture
import com.hyuchia.orbit.data.AssetStarterMaps
import com.hyuchia.orbit.data.CrashLog
import com.hyuchia.orbit.data.DocumentFolder
import com.hyuchia.orbit.data.FolderSync
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.SettingsRepository
import com.hyuchia.orbit.data.TrashedMap
import com.hyuchia.orbit.data.Updates
import com.hyuchia.orbit.nearby.NearbyHost
import com.hyuchia.orbit.page.MapPictures
import com.hyuchia.orbit.page.OrbitBuild
import com.hyuchia.orbit.page.PagePool
import com.hyuchia.orbit.widget.offerWidgetPreview
import com.hyuchia.orbit.widget.updateMapWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** What the app's screens share: the maps, the settings, and the pages that draw maps. */
class AppContainer(context: Context, /** Reports of crashes, kept on the phone. */ val crashes: CrashLog) {
    private val mapsDirectory = File(context.filesDir, "maps")

    /** The app's strings in the language it's in, for what it says away from a screen: in the maps' files, and in messages about them. */
    val words: Words = ResourceWords(context.applicationContext)

    val maps = MapRepository(mapsDirectory, File(context.filesDir, "trash"), AssetStarterMaps(context.applicationContext), words, room = { file -> room(context, file) })
    val settings = SettingsRepository(context)
    val pages = PagePool(context, mapsDirectory)
    val pictures = MapPictures(pages, context.cacheDir, OrbitBuild.bundled(context)?.commit.orEmpty())
    val importer = Importer(maps, pages, words)

    /** Ideas added to maps without opening them: from the tile, a widget, or another app. */
    val capture = QuickCapture(pages, maps, settings)

    /** For work that outlives a screen, such as saving the last edit as a map closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** This device for others on the local network, while Orbit is open: maps sent here, maps shown to computers. */
    val nearby = NearbyHost(context.applicationContext, maps, applicationScope)

    /** The folder the maps are kept in as markdown files, when the user chose one. */
    val folderSync = FolderSync(maps, settings, applicationScope, File(context.filesDir, "folder-notes.json"), words) { address ->
        DocumentFolder(context.contentResolver, address.toUri(), words)
    }

    /** Maps deleted from their own screen, for the home screen to offer back with Undo once it shows. */
    val deletedMaps = Channel<TrashedMap>(Channel.CONFLATED)

    /** New versions, for the copies no store updates: the APK from the website and GitHub. */
    val updates = Updates(context, settings)
}

class OrbitApplication : Application() {
    lateinit var container: AppContainer
        private set

    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
    private var locales = LocaleList.getEmptyLocaleList()

    override fun onCreate() {
        super.onCreate()
        // First, so a crash while the rest starts is kept too.
        val crashes = CrashLog(File(filesDir, "crashes"), about(), hidden = ::mapNames)
        crashes.install()
        container = AppContainer(this, crashes)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        locales = resources.configuration.locales
        keepWidgetsCurrent()
        container.folderSync.start(ProcessLifecycleOwner.get().lifecycle)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Widgets draw their maps in the home screen's colors and describe them in the app's language:
        // night and day need pictures of their own, and a new language needs new words.
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

        if (night != nightMode || newConfig.locales != locales) {
            nightMode = night
            locales = newConfig.locales
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
     * ideas caught, a map renamed or deleted. Once the maps are read, the
     * launcher's list of widgets gets a preview of them.
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
    }
}

/** How long the maps stay still before the widgets are drawn again, so typing doesn't redraw them on every letter. */
private val WIDGET_SETTLE = 2.seconds

val Context.container: AppContainer get() = (applicationContext as OrbitApplication).container

/** How many bytes the system can make room for where `file` is, clearing caches if it must; as many as it likes when it can't say. */
private fun room(context: Context, file: File): Long = try {
    val storage = context.getSystemService(StorageManager::class.java)
    storage.getAllocatableBytes(storage.getUuidForPath(file))
} catch (unknown: IOException) {
    Long.MAX_VALUE
}
