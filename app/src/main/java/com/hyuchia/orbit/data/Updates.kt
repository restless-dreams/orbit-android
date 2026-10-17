package com.hyuchia.orbit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hyuchia.orbit.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** A version of Orbit published for download, and the page it's downloaded from. */
data class Release(val version: String, val page: String)

/** What the last look for a new version found. */
data class UpdateState(
    /** The latest release published, or null when none was found. */
    val latest: Release? = null,

    /** When Orbit last looked, in milliseconds since 1970, or 0 if it never has. */
    val checkedAt: Long = 0,

    /** The last look couldn't reach the releases, or couldn't read them. */
    val failed: Boolean = false,

    /** The version whose notice was put away with "Not now". */
    val dismissed: String? = null,
)

/**
 * New versions of Orbit, for the copies no store updates: the APK from the
 * website and GitHub. At most once a day, and only while Settings allows it,
 * Orbit asks [address] for the latest release and keeps what it found. The
 * request carries nothing about the phone or its maps, not even Android's
 * own user agent. An empty address turns it all off: the play build, which
 * Google Play updates, and the F-Droid and AppGallery builds.
 */
class Updates(
    private val store: DataStore<Preferences>,
    private val enabled: suspend () -> Boolean,
    private val address: String = BuildConfig.UPDATES_URL,
    private val current: String = BuildConfig.VERSION_NAME,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val fetch: (String) -> String? = ::latestReleaseText,
) {
    /** Orbit's own: kept beside the settings, but out of backups, since it's nobody's data. */
    constructor(context: Context, settings: SettingsRepository) : this(
        store = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { File(context.noBackupFilesDir, "updates.preferences_pb") },
        ),
        enabled = { settings.settings.first().checkForUpdates },
    )

    /** Whether this build looks for new versions at all. */
    val available: Boolean get() = address.isNotEmpty()

    val state: Flow<UpdateState> = store.data
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map(::read)

    /** The release to tell people about: newer than this copy, and not put away with "Not now". */
    fun announced(state: UpdateState): Release? = newer(state)?.takeIf { it.version != state.dismissed }

    /** The latest release when it's newer than this copy. */
    fun newer(state: UpdateState): Release? = state.latest?.takeIf { isNewer(it.version, current) }

    /** Looks again when a day has passed since the last look, if this build looks at all and Settings allows it. */
    suspend fun checkIfDue() {
        if (!available || !enabled()) {
            return
        }

        if (isDue(state.first().checkedAt, clock())) {
            check()
        }
    }

    /** Looks now, whatever the time: from About. */
    suspend fun check(): UpdateState {
        val checkedAt = clock()

        val look: Result<Release?> = try {
            val text = withContext(io) { fetch(address) }
            val release = text?.let(::parseRelease)

            when {
                // No release yet (GitHub answers 404): nothing to look forward to.
                text == null -> Result.success(null)
                release == null -> Result.failure(IOException("Not a release Orbit offers"))
                else -> Result.success(release)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // No connection, GitHub busy or refusing: the last release found stays, and the next look is tomorrow's.
            Result.failure(failure)
        }

        store.edit { preferences ->
            preferences[Keys.checkedAt] = checkedAt
            preferences[Keys.failed] = look.isFailure

            look.onSuccess { release ->
                preferences[Keys.latestVersion] = release?.version.orEmpty()
                preferences[Keys.latestPage] = release?.page.orEmpty()
            }
        }

        return state.first()
    }

    /** Puts away the notice about [version], until a newer one comes out. */
    suspend fun dismiss(version: String) {
        store.edit { preferences -> preferences[Keys.dismissed] = version }
    }

    private fun read(preferences: Preferences): UpdateState {
        val version = preferences[Keys.latestVersion].orEmpty()
        val page = preferences[Keys.latestPage].orEmpty()

        return UpdateState(
            latest = if (version.isNotEmpty() && page.isNotEmpty()) Release(version, page) else null,
            checkedAt = preferences[Keys.checkedAt] ?: 0,
            failed = preferences[Keys.failed] ?: false,
            dismissed = preferences[Keys.dismissed]?.ifEmpty { null },
        )
    }

    private object Keys {
        val checkedAt = longPreferencesKey("checked_at")
        val failed = booleanPreferencesKey("failed")
        val latestVersion = stringPreferencesKey("latest_version")
        val latestPage = stringPreferencesKey("latest_page")
        val dismissed = stringPreferencesKey("dismissed")
    }
}

/** How long Orbit waits between looks. */
private val INTERVAL = 1.days

/** How long a look may take to connect, and then to answer. */
private val TIMEOUT = 15.seconds

/** GitHub describes a release in a few kilobytes: more than this isn't one. */
private const val RELEASE_TEXT_LIMIT = 1024 * 1024

/** Whether it's time to look again: a day after the last look, or whenever the clock went back past it. */
internal fun isDue(checkedAt: Long, now: Long): Boolean = now < checkedAt || now - checkedAt >= INTERVAL.inWholeMilliseconds

/** A version's numbers (0.1.10 is 0, 1 and 10), or null when it isn't a plain version like 0.1.1. */
internal fun versionNumbers(version: String): List<Int>? {
    val parts = version.split('.')

    if (parts.size !in 1..4) {
        return null
    }

    return parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 && part.all(Char::isDigit) } ?: return null }
}

/**
 * Whether [candidate], a plain version, comes after [current]: 0.1.10 comes
 * after 0.1.9, 0.2 after 0.1.9, and 0.2.0 after its own pre-releases, such as
 * 0.2.0-alpha.1, so a copy from a pre-release hears when the release is out.
 */
internal fun isNewer(candidate: String, current: String): Boolean {
    val next = versionNumbers(candidate) ?: return false
    val released = current.substringBefore('-')
    val now = versionNumbers(released) ?: return false

    for (index in 0 until maxOf(next.size, now.size)) {
        val difference = next.getOrElse(index) { 0 } - now.getOrElse(index) { 0 }

        if (difference != 0) {
            return difference > 0
        }
    }

    // The same numbers: newer only than a pre-release of them.
    return released != current
}

/** What Orbit reads of GitHub's description of a release. */
@Serializable
private class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val page: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

private val releaseJson = Json { ignoreUnknownKeys = true }

/**
 * The release GitHub describes in [text], or null when it isn't one Orbit
 * offers: a draft, a pre-release, a tag that isn't a version (v0.1.1 and
 * 0.1.1 are), or a page that isn't on https.
 */
internal fun parseRelease(text: String): Release? {
    val release = try {
        releaseJson.decodeFromString<GitHubRelease>(text)
    } catch (unreadable: IllegalArgumentException) {
        return null
    }

    val version = release.tag.removePrefix("v")

    if (release.draft || release.prerelease || versionNumbers(version) == null || !release.page.startsWith("https://")) {
        return null
    }

    return Release(version, release.page)
}

/** GitHub's description of the latest release at [address], or null when there's none yet. Blocks: call it off the main thread. */
private fun latestReleaseText(address: String): String? {
    val connection = URL(address).openConnection() as HttpURLConnection

    try {
        connection.connectTimeout = TIMEOUT.inWholeMilliseconds.toInt()
        connection.readTimeout = TIMEOUT.inWholeMilliseconds.toInt()
        // Only Orbit's name: Android's own user agent would tell GitHub the phone's model and Android version.
        connection.setRequestProperty("User-Agent", "Orbit")
        connection.setRequestProperty("Accept", "application/vnd.github+json")

        return when (val status = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> connection.inputStream.use { it.readWithin(RELEASE_TEXT_LIMIT) }.decodeToString()
            HttpURLConnection.HTTP_NOT_FOUND -> null
            else -> throw IOException("The releases answered $status")
        }
    } finally {
        connection.disconnect()
    }
}

/** The whole stream, when it ends within `limit` bytes. */
private fun InputStream.readWithin(limit: Int): ByteArray {
    val bytes = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)

    while (true) {
        val read = read(chunk)

        if (read < 0) {
            return bytes.toByteArray()
        }

        if (bytes.size() + read > limit) {
            throw IOException("More than $limit bytes")
        }

        bytes.write(chunk, 0, read)
    }
}
