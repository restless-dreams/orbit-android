package com.hyuchia.orbit.settings

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.ImportStoppedException
import com.hyuchia.orbit.data.ImportedMaps
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.data.RefusedZipException
import com.hyuchia.orbit.data.UnreadableMapFileException
import com.hyuchia.orbit.privacy.coverTitle
import com.hyuchia.orbit.privacy.mentionedAlreadyHere
import com.hyuchia.orbit.privacy.mentionedFilledIn
import com.hyuchia.orbit.ui.systemReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Export all and Import maps, run on the app's scope: leaving Settings
 * neither cuts a zip short nor stops maps coming in. One runs at a time,
 * and what it came to waits here until Settings has said it, so a problem
 * is never lost to a screen that closed.
 */
class MapBackups(private val scope: CoroutineScope) {
    enum class Work { Exporting, Importing }

    /**
     * What a backup came to, and whether that's a problem. Settings can say it long after the backup ended, once
     * private maps have locked, or a map has been made private, so it's worded as they are then ([said]): `worded`
     * as it's said, told whether private maps hide, when it then tells nothing of them. Each is its own, even with
     * the same words.
     */
    class Outcome(val problem: Boolean, private val worded: (concealing: Boolean) -> String) {
        /** Says `words`, or `concealed` while private maps hide. */
        constructor(words: String, problem: Boolean, concealed: String = words) : this(problem, { concealing -> if (concealing) concealed else words })

        /** What Settings says as it shows it: while private maps hide (`concealing`), nothing of theirs. */
        fun said(concealing: Boolean): String = worded(concealing)
    }

    private val running = MutableStateFlow<Work?>(null)
    private val outcomes = MutableStateFlow<Outcome?>(null)

    /** What's under way, if anything. */
    val work: StateFlow<Work?> = running.asStateFlow()

    /** What the last backup came to, until [said]. */
    val outcome: StateFlow<Outcome?> = outcomes.asStateFlow()

    /** Starts `run`, unless another backup is under way: then it says false, and leaves that one be. */
    fun start(work: Work, run: suspend () -> Outcome): Boolean {
        if (!running.compareAndSet(null, work)) {
            return false
        }

        scope.launch {
            try {
                outcomes.value = run()
            } finally {
                running.value = null
            }
        }

        return true
    }

    /** Settings has said `outcome`, which goes; one that came after it stays to be said. */
    fun said(outcome: Outcome) {
        outcomes.compareAndSet(outcome, null)
    }
}

/** What writing every map into a document came to ([exportInto]). */
internal sealed interface Exported {
    /** The zip is there, with `count` maps. */
    data class Maps(val count: Int) : Exported

    /** It couldn't be written there: another place may do. */
    data object NotWritten : Exported

    /** The app that keeps the document doesn't let Orbit write it. */
    data object NotAllowed : Exported

    /** A file of map `map` (its id, when it's known) couldn't be read, so no zip was written that would leave it out. */
    data class Unreadable(val map: String?) : Exported
}

/**
 * Writes the zip `export` writes into the document at `uri`, one the
 * person chose in the save screen: a new one, or one already there, such
 * as an earlier backup, to write over. `check` looks first that every map's
 * files can be read ([com.hyuchia.orbit.data.MapRepository.checkExportAll]),
 * before the document is opened, which empties it. Half a backup isn't one:
 * once the document is opened for the zip, whatever stops it (a write, or
 * the app that keeps the file finishing it) deletes it, or, when its app
 * won't, leaves it empty. One never opened was never written, so it stays
 * as it was, unless it's empty, as the save screen makes a new one: that
 * one goes.
 */
internal suspend fun exportInto(resolver: ContentResolver, uri: Uri, check: suspend () -> Unit = {}, export: suspend (OutputStream) -> Int): Exported {
    var opened = false

    val exported = try {
        check()

        val count = withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { output ->
                opened = true
                export(output)
            }
        }

        count?.let { Exported.Maps(it) } ?: Exported.NotWritten
    } catch (unreadable: UnreadableMapFileException) {
        Exported.Unreadable(unreadable.map)
    } catch (failed: IOException) {
        Exported.NotWritten
    } catch (failed: SecurityException) {
        Exported.NotAllowed
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failed: RuntimeException) {
        // Whatever else the app that keeps the file throws back.
        Exported.NotWritten
    }

    if (exported !is Exported.Maps) {
        withContext(Dispatchers.IO) {
            if (opened) {
                deleteOrEmpty(resolver, uri)
            } else {
                deleteIfEmpty(resolver, uri)
            }
        }
    }

    return exported
}

/**
 * What Export all came to, `exported`, worded for Settings to say as
 * private maps are when it does ([MapBackups.Outcome.said]), not as they
 * were as it ended. `summary` finds a map by its id, as it's said: one made
 * private since is named as private maps are.
 */
internal fun exportOutcome(exported: Exported, summary: (String) -> MapSummary?, resources: Resources): MapBackups.Outcome = when (exported) {
    is Exported.Maps -> MapBackups.Outcome(
        resources.getQuantityString(R.plurals.settings_exported_maps, exported.count, exported.count),
        problem = false,
        concealed = resources.getString(R.string.private_exported_every_map),
    )
    Exported.NotWritten -> MapBackups.Outcome(resources.getString(R.string.settings_could_not_write_file), problem = true)
    Exported.NotAllowed -> MapBackups.Outcome(resources.getString(R.string.settings_not_allowed_to_write), problem = true)
    is Exported.Unreadable -> MapBackups.Outcome(problem = true) { concealing -> unreadableExport(exported.map?.let(summary), concealing, resources) }
}

/**
 * What Import maps came to, `imported`, worded for Settings to say as
 * private maps are when it does ([MapBackups.Outcome.said]), not as they
 * were as it began: while they hide, the maps that were here already, and
 * the files that came into them or stayed in the zip, count none of the
 * private ones. Files the maps here were missing, those that stayed in the
 * zip past what a map carries, the zip's folders without a map and files of
 * kinds Orbit doesn't know are said after what came in. One `stopped`
 * partway ([ImportStoppedException]) says so first, then only what came,
 * and that importing the file again brings the rest.
 */
internal fun importOutcome(imported: ImportedMaps, words: Words, resources: Resources, stopped: Throwable? = null): MapBackups.Outcome {
    val language = resources.configuration.locales[0].toLanguageTag()

    fun worded(concealing: Boolean): String {
        val alreadyHere = mentionedAlreadyHere(imported.alreadyHere, imported.privateAlreadyHere, concealing)

        val said = when {
            // Stopped partway, nothing's said of the file as a whole: only what came.
            stopped != null && imported.added == 0 -> null
            imported.added == 0 && imported.alreadyHere == 0 -> resources.getString(R.string.settings_no_maps_in_file)
            imported.added == 0 -> resources.getString(R.string.settings_every_map_in_file_already_here)
            alreadyHere == 0 -> resources.getQuantityString(R.plurals.common_added_maps, imported.added, imported.added)
            alreadyHere == 1 -> resources.getQuantityString(R.plurals.common_added_maps_with_one_already_here, imported.added, imported.added)
            else -> resources.getQuantityString(R.plurals.common_added_maps_with_several_already_here, imported.added, imported.added, alreadyHere)
        }

        val filledIn = mentionedFilledIn(imported.filledIn, imported.privateFilledIn, concealing)
        val pastRoom = mentionedFilledIn(imported.pastRoom, imported.privatePastRoom, concealing)
        val notes = Importer.zipNotes(filledIn, imported.skipped, imported.leftOut, words, pastRoom, imported.stayedPrivate)
        val stop = stopped?.let { Importer.stoppedNote(Importer.stopReason(it, language), words) }
        val again = stopped?.let { words.string(R.string.common_import_again_for_the_rest) }
        return (listOfNotNull(stop, said) + notes + listOfNotNull(again)).joinToString(" ")
    }

    return MapBackups.Outcome(
        worded(concealing = false),
        problem = stopped != null || (imported.added == 0 && imported.alreadyHere == 0),
        concealed = worded(concealing = true),
    )
}

/**
 * Import maps, from the document at `uri` the person chose: a map from
 * another app (OPML, FreeMind and Freeplane, XMind) comes in as Open a file
 * brings it (`fromOtherApp`, null for any other file); anything else is read
 * as a zip of every map (`importAll`). What it came to is worded for
 * Settings to say ([importOutcome]).
 */
internal suspend fun importInto(
    resolver: ContentResolver,
    uri: Uri,
    words: Words,
    resources: Resources,
    fromOtherApp: suspend () -> ImportResult?,
    importAll: suspend (InputStream) -> ImportedMaps,
): MapBackups.Outcome {
    fromOtherApp()?.let { return otherAppOutcome(it, resources) }

    return try {
        val imported = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { importAll(it) } } ?: throw IOException()
        // Worded as private maps are when Settings says it, which can be long after the import began.
        importOutcome(imported, words, resources)
    } catch (stopped: ImportStoppedException) {
        // What came before it stopped is here: it says exactly what.
        importOutcome(stopped.soFar, words, resources, stopped = stopped.cause ?: stopped)
    } catch (refused: RefusedZipException) {
        MapBackups.Outcome(refused.message.orEmpty(), problem = true)
    } catch (failed: IOException) {
        // What Android or the zip says went wrong is in English: another language's sentence goes without it.
        val reason = resources.systemReason(failed)

        val problem = when {
            failed.message == null -> resources.getString(R.string.settings_could_not_read_file_not_zip)
            reason != null -> resources.getString(R.string.settings_could_not_read_file, reason)
            else -> resources.getString(R.string.data_file_unreadable)
        }

        MapBackups.Outcome(problem, problem = true)
    } catch (failed: SecurityException) {
        MapBackups.Outcome(resources.getString(R.string.common_not_allowed_to_read_that_file), problem = true)
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (failed: RuntimeException) {
        // Whatever else the app that gave it throws back, or a zip whose names aren't text.
        MapBackups.Outcome(resources.getString(R.string.data_file_unreadable), problem = true)
    }
}

/** What a map from another app brought through Import maps: how many maps, with what didn't come over as it was, or why none came. */
private fun otherAppOutcome(result: ImportResult, resources: Resources): MapBackups.Outcome = when (result) {
    is ImportResult.Imported -> MapBackups.Outcome((listOf(resources.getQuantityString(R.plurals.common_added_maps, 1, 1)) + result.notes).joinToString(" "), problem = false)
    is ImportResult.ImportedMany -> MapBackups.Outcome(
        (listOf(resources.getQuantityString(R.plurals.common_added_maps, result.added, result.added)) + result.notes).joinToString(" "),
        problem = false,
    )
    is ImportResult.Failed -> MapBackups.Outcome(result.reason, problem = true)
    else -> MapBackups.Outcome(resources.getString(R.string.data_file_unreadable), problem = true)
}

/**
 * What Export all says when a file of `map` couldn't be read: the map's
 * title, or a private map's cover, as it shows locked; and nothing that
 * tells of a private map while they hide (`concealing`).
 */
internal fun unreadableExport(map: MapSummary?, concealing: Boolean, resources: Resources): String {
    val name = when {
        map == null -> null
        !map.metadata.isPrivate -> map.title
        concealing -> null
        else -> resources.coverTitle(map.metadata.cover)
    }

    return name?.let { resources.getString(R.string.settings_export_file_unreadable_in, it) } ?: resources.getString(R.string.settings_export_file_unreadable)
}

/** Deletes the document at `uri`, half a zip; when its app won't, it's opened once more to leave it empty, so nothing takes it for a whole one. */
private fun deleteOrEmpty(resolver: ContentResolver, uri: Uri) {
    if (runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)) {
        return
    }

    runCatching { resolver.openOutputStream(uri, "wt")?.close() }
}

/** Deletes the document at `uri` when its app says it's empty; one whose size can't be told stays. */
private fun deleteIfEmpty(resolver: ContentResolver, uri: Uri) {
    val size = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { row ->
            if (row.moveToFirst() && !row.isNull(0)) row.getLong(0) else null
        }
    }.getOrNull()

    if (size == 0L) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }
}
