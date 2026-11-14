package com.hyuchia.orbit

import android.content.ContentResolver
import android.net.Uri
import com.hyuchia.orbit.data.AcceptedArrival
import com.hyuchia.orbit.data.ChangedSinceAnsweredException
import com.hyuchia.orbit.data.ImportProgress
import com.hyuchia.orbit.data.ImportResult
import com.hyuchia.orbit.data.Importer
import com.hyuchia.orbit.data.MapRepository
import com.hyuchia.orbit.data.VersionReason
import com.hyuchia.orbit.data.WaitingArrivals
import com.hyuchia.orbit.data.textHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What [Intake] did, waiting for a screen to take it: each is taken once ([Intake.take]). */
sealed interface IntakeOutcome {
    /** What a file opened with Orbit, or something shared to it, brought: import `request` ([ImportRequests]), when it was kept. */
    class Imported(val result: ImportResult, val request: String? = null) : IntakeOutcome

    /** An answered arrival is in: its map opens. */
    class Opened(val id: String) : IntakeOutcome

    /** An answered arrival couldn't be taken in, for `reason`: it's said, then `question` is asked again. */
    class AskAgain(val question: OtherVersion, val reason: String) : IntakeOutcome
}

/**
 * What comes into Orbit, done in the app's scope rather than a screen's:
 * neither a screen made again, as for a new language, nor one that closes
 * stops it, and what it comes to waits for the screen there is then, or
 * the next one ([outcomes]).
 *
 * - What shortcuts and other apps ask ([request]) waits for a screen to
 *   take it, each request once.
 * - Files opened with Orbit, and text and files shared to it for a new
 *   map, are imported here ([importFile], [importText], [newMapWithFiles]),
 *   each kept on disk until a screen takes what it brought ([imports]), so
 *   a screen Android brings back after stopping Orbit midway imports it
 *   again, or shows what it brought ([resumeImports]).
 * - Maps here already in other versions, answered Update or Keep both
 *   ([accept]), keep their answer beside their files first, and are taken
 *   in one at a time, in the order they were answered; one Orbit stopped
 *   before it was in is taken in as Orbit starts again ([resume]). One
 *   that can't be is asked about again, its files kept, never let go.
 */
class Intake(
    private val maps: MapRepository,
    private val importer: Importer,
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val words: Words,

    /** The imports asked of Orbit, kept until a screen takes what they brought. */
    private val imports: ImportRequests,

    /** Held while an answered arrival is taken in; a test holds it to keep them from being taken in meanwhile. */
    private val queue: Mutex = Mutex(),
) {
    private val waiting: WaitingArrivals get() = maps.waitingArrivals

    private val requests = Channel<AppRequest>(Channel.UNLIMITED)

    /** What shortcuts and other apps ask of Orbit, for the screen there is, or the next one: each request reaches one screen. */
    val requested: Flow<AppRequest> = requests.receiveAsFlow()

    private val done = MutableStateFlow<List<IntakeOutcome>>(emptyList())

    /** What's done, oldest first, until a screen takes it. */
    val outcomes: StateFlow<List<IntakeOutcome>> = done.asStateFlow()

    /** Answers, kept on disk one at a time in the order given ([accept]). */
    private val answers = Channel<Answer>(Channel.UNLIMITED)

    /** Answered arrivals, taken in one at a time in the order answered. */
    private val answered = Channel<Pair<AcceptedArrival, Boolean>>(Channel.UNLIMITED)

    /**
     * The text each map will have once the Updates answered for it before
     * are in, by map: an Update answered meanwhile is for that text, not for
     * the one the map has until they're in.
     */
    private val coming = ConcurrentHashMap<String, String>()

    /**
     * By map, an Update that couldn't be taken in, as on a full storage,
     * and not for its map having changed: the hash of its text, and of the
     * text it was for. One answered after it, for its text, is for that one
     * instead, which the map still has.
     */
    private val notIn = ConcurrentHashMap<String, Pair<String, String?>>()

    private class Answer(val question: OtherVersion, val keepBoth: Boolean, val written: CompletableDeferred<Unit>)

    init {
        scope.launch {
            for (answer in answers) {
                keep(answer)?.let { answered.send(it to answer.question.opens) }
            }
        }

        scope.launch {
            for ((accepted, opens) in answered) {
                takeIn(accepted, opens)
            }
        }
    }

    /** The imports running in this run of Orbit, by id, which no screen lets go of. */
    private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A shortcut or another app asks Orbit for `request`: the screen there is takes it, or the next one. */
    fun request(request: AppRequest) {
        requests.trySend(request)
    }

    /** A screen took `outcome`: no other one will, nor will one Android brings back ask for it again. */
    fun take(outcome: IntakeOutcome) {
        done.update { list -> list.filterNot { it === outcome } }
        val request = (outcome as? IntakeOutcome.Imported)?.request ?: return
        scope.launch(Dispatchers.IO) { imports.forget(request) }
    }

    /** A file opened with Orbit, or shared to it on its own: a map, maps, or a question about one here already. Gives the import's id. */
    fun importFile(uri: Uri): String = import(ImportRequests.Request(ImportRequests.FILE, addresses = listOf(uri.toString())))

    /** Text shared to Orbit, with its `subject`: a new map. Gives the import's id. */
    fun importText(text: String, subject: String?): String = import(ImportRequests.Request(ImportRequests.TEXT, text = text, subject = subject))

    /** Files shared to Orbit while no map is open to take them: a new map, holding them. Gives the import's id. */
    fun newMapWithFiles(uris: List<Uri>): String = import(ImportRequests.Request(ImportRequests.FILES, addresses = uris.map { it.toString() }))

    /**
     * What a screen Android brought back was waiting for, the imports
     * `listed`: each that's done shows what it brought (its map opens),
     * and each Orbit stopped in the middle of is imported again, its files
     * read with the permission that screen still holds. The imports no
     * screen lists, and none runs, go.
     */
    fun resumeImports(listed: List<String>) {
        scope.launch(Dispatchers.IO) {
            imports.forgetAllBut(listed.toSet() + running)

            for (id in listed.filterNot { it in running }) {
                val request = imports.read(id) ?: continue

                if (!request.done) {
                    run(id, request)
                    continue
                }

                // What else it brought was said, or waits as a question every screen asks (see run).
                val map = request.map?.takeIf { maps.exists(it) }

                if (map != null) {
                    post(IntakeOutcome.Imported(ImportResult.Imported(map, maps.summary(map)?.title.orEmpty()), id))
                } else {
                    imports.forget(id)
                }
            }
        }
    }

    private fun import(request: ImportRequests.Request): String {
        val id = UUID.randomUUID().toString()
        running += id

        scope.launch {
            withContext(Dispatchers.IO) { imports.start(id, request) }
            run(id, request)
        }

        return id
    }

    /**
     * Runs import `id`, and keeps what it brought: the map it made, and a
     * map here already in another version, kept for every screen to ask
     * about, so the one Android brings back asks it even if Orbit stops
     * before any screen did.
     */
    private fun run(id: String, request: ImportRequests.Request) {
        running += id

        scope.launch {
            try {
                // Each map it makes is kept with it as soon as it's there: run again after Orbit stopped, it makes only the rest.
                val progress = ImportProgress(request.made, making = { map -> imports.made(id, map) }, unmaking = { map -> imports.unmade(id, map) })

                val result = when (request.kind) {
                    ImportRequests.FILE -> importer.importFile(request.uris.first(), resolver, progress)
                    ImportRequests.FILES -> importer.newMapWithFiles(request.uris, resolver, progress)
                    else -> importer.importShared(request.text.orEmpty(), request.subject, progress)
                }

                if (result is ImportResult.AlreadyHere) {
                    waiting.keep(result, opens = true, ask = true)
                }

                withContext(Dispatchers.IO) { imports.finish(id, (result as? ImportResult.Imported)?.id) }
                post(IntakeOutcome.Imported(result, id))
            } finally {
                running -= id
            }
        }
    }

    /**
     * `question` was answered Update, or Keep both (`keepBoth`): its answer
     * is kept beside its files, which the returned job says once it's done,
     * and then it's taken in, after the ones answered before it. Update
     * writes only over the map's text as it is now.
     */
    fun accept(question: OtherVersion, keepBoth: Boolean): Deferred<Unit> {
        val written = CompletableDeferred<Unit>()
        waiting.takeIn(question.arrival)
        answers.trySend(Answer(question, keepBoth, written))
        return written
    }

    /** Keeps `answer` beside its files, Update for the text its map will have once those answered before are in ([coming]). */
    private suspend fun keep(answer: Answer): AcceptedArrival? {
        val arrival = answer.question.arrival

        return try {
            val base = if (answer.keepBoth) null else coming[arrival.existing]?.let(::textHash) ?: baseOf(arrival.existing)
            val accepted = waiting.accept(arrival, answer.keepBoth, base, answer.question.opens)

            // Only once it's kept: one that isn't never has the next Update of its map wait for its text.
            if (!answer.keepBoth) {
                coming[arrival.existing] = arrival.bundle.markdown
            }

            accepted
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: Exception) {
            // Not kept, it's not taken in: it's asked again, its files where they are.
            Logs.warn(TAG, "Couldn't keep an answer", failed)
            askAgain(AcceptedArrival(arrival, answer.keepBoth, base = null, copy = "", opens = answer.question.opens), failed)
            null
        } finally {
            answer.written.complete(Unit)
        }
    }

    /**
     * Takes in what was answered but isn't in, as Orbit stopped before it
     * was, once the maps are read: none of their maps opens, as nothing
     * asked for them since.
     */
    fun resume() {
        scope.launch {
            maps.maps.filterNotNull().first()

            for (accepted in waiting.answered()) {
                if (!accepted.keepBoth) {
                    coming[accepted.arrival.existing] = accepted.arrival.bundle.markdown
                }

                answered.send(accepted to false)
            }
        }
    }

    /** The hash of map `id`'s text as it is now ([textHash]), or null when it can't be read. */
    private suspend fun baseOf(id: String): String? = try {
        textHash(maps.read(id))
    } catch (unreadable: IOException) {
        null
    }

    private suspend fun takeIn(accepted: AcceptedArrival, opens: Boolean) = queue.withLock {
        val arrival = accepted.arrival

        // Answered for the text of an Update before it that couldn't be taken in: it's for the text that one was for.
        val base = accepted.base?.let { base -> notIn[arrival.existing]?.takeIf { it.first == base }?.second ?: base }

        try {
            val replacing = arrival.existing.takeUnless { accepted.keepBoth }
            val because = VersionReason.FromFile(arrival.name)
            val id = maps.receive(arrival.bundle, replacing, because, base, accepted.copy) { identity -> waiting.addsAs(arrival, identity) }

            if (!accepted.keepBoth) {
                notIn.remove(arrival.existing)
            }

            if (opens) {
                post(IntakeOutcome.Opened(id))
            }

            // What it was unpacked to goes once it's in: its files moved into the map.
            waiting.letGo(arrival)
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: Exception) {
            Logs.warn(TAG, "Couldn't take in an answered map", failed)

            if (!accepted.keepBoth && failed !is ChangedSinceAnsweredException) {
                notIn[arrival.existing] = textHash(arrival.bundle.markdown) to base
            }

            askAgain(accepted, failed)
        } finally {
            // In or not, the map's text is its own again for the next Update answered for it.
            if (!accepted.keepBoth) {
                coming.remove(arrival.existing, arrival.bundle.markdown)
            }
        }
    }

    /** `accepted` couldn't be kept or taken in, as `failed` says: it's asked again, once why is said, or why is said alone when its files went. */
    private suspend fun askAgain(accepted: AcceptedArrival, failed: Exception) {
        val reason = (failed as? IOException)?.message ?: words.string(R.string.common_could_not_keep_that_map)
        val again = waiting.askAgain(accepted)

        if (again != null) {
            post(IntakeOutcome.AskAgain(OtherVersion(again, accepted.opens), reason))
        } else {
            post(IntakeOutcome.Imported(ImportResult.Failed(reason)))
        }
    }

    private fun post(outcome: IntakeOutcome) {
        done.update { it + outcome }
    }

    private companion object {
        const val TAG = "Intake"
    }
}
