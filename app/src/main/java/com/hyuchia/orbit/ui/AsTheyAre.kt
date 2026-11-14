package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.AsTheyAreFiles
import com.hyuchia.orbit.data.FileChanged
import com.hyuchia.orbit.data.FileStamp
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.rememberWords

/**
 * Which of the map's files that can't go clean ([Uncleaned]) a way out
 * shares as they are: those asked about so far, and those the person chose
 * to, each as it was when they were asked ([FileStamp]), so an answer is
 * about that very file, never another put in its place under its name. The
 * answers hold while the way out lasts, kept with it: a presentation's
 * outlive its screen. Questions are the screen's ([AsTheyAre]), and nothing
 * of a screen is kept here.
 */
@Stable
class AsTheyAreChoice internal constructor() {
    /** The files asked about so far, as they were then. */
    internal var asked by mutableStateOf(emptySet<FileStamp>())
        private set

    /** The files the person chose to share as they are, as they were then. */
    internal var chosen by mutableStateOf(emptySet<FileStamp>())
        private set

    /** The answers so far, for a way out to go by. */
    val files: AsTheyAreFiles get() = AsTheyAreFiles(asked, chosen)

    internal fun answer(files: Uncleaned, share: Boolean) {
        asked = asked + files.stamps.values

        if (share) {
            chosen = chosen + files.stamps.values
        }
    }
}

/**
 * Whether a way out shares as they are the map's files that can't go clean
 * ([Uncleaned]). It asks at the phone, as the way out starts, the first time
 * it would carry any, and the answer holds while the way out lasts
 * (`choice`), for each file as it was then. Until the person says
 * otherwise, those files stay on this device, and a browser is never asked.
 * A file that changed after they answered is asked about again, as it is
 * now ([askAgain]). A question goes with the screen that asked it, and what
 * it would start with it: a screen made again asks afresh, rather than
 * start what the one before was for.
 */
@Stable
class AsTheyAre internal constructor(private val choice: AsTheyAreChoice = AsTheyAreChoice()) {
    /** What the person answered so far, for a way out to go by ([AsTheyAreFiles]). */
    val chosen: AsTheyAreFiles
        get() = choice.files

    /** The question waiting for an answer, and what starts once it has one. */
    internal var question by mutableStateOf<Question?>(null)
        private set

    /**
     * Starts a way out that would carry `files`, with the answers about them:
     * asks first about any not asked about yet, as they are now. Dismissing
     * the question starts nothing.
     */
    fun then(files: Uncleaned, start: (asTheyAre: AsTheyAreFiles) -> Unit) = ask(files, changed = false, start)

    /**
     * Starts again a way out that stopped as one of the files it carries
     * changed after the person answered ([FileChanged]), with `files` as
     * they are now: asks first about those not asked about as they are now,
     * saying they changed. Nothing to ask, it starts again at once.
     */
    fun askAgain(files: Uncleaned, start: (asTheyAre: AsTheyAreFiles) -> Unit) = ask(files, changed = true, start)

    private fun ask(files: Uncleaned, changed: Boolean, start: (asTheyAre: AsTheyAreFiles) -> Unit) {
        val new = files.only(files.stamps.filterValues { it !in choice.asked }.keys)

        if (new.isEmpty) {
            start(chosen)
        } else {
            // It says they changed only when the person did answer about one of them as it was: not on the first question, as one a tap
            // before the screen knew of the files can bring.
            val answered = changed && new.stamps.values.any { now -> choice.asked.any { it.path == now.path } }
            question = Question(new, answered) { start(chosen) }
        }
    }

    /** Drops a question no one answered, as the way out it was for went away. */
    fun forget() {
        question = null
    }

    internal fun answer(question: Question, share: Boolean) {
        this.question = null
        choice.answer(question.files, share)
        question.start()
    }

    /** What's asked about: `files`, and whether they `changed` after the person answered about them before. */
    internal class Question(val files: Uncleaned, val changed: Boolean, val start: () -> Unit)
}

/** An [AsTheyAre] for a way out, whose questions show here: with `choice`, kept elsewhere when the way out outlives the screen, as a presentation's does. */
@Composable
fun rememberAsTheyAre(choice: AsTheyAreChoice = remember { AsTheyAreChoice() }): AsTheyAre {
    val asTheyAre = remember(choice) { AsTheyAre(choice) }

    asTheyAre.question?.let { question ->
        AsTheyAreDialog(question.files, changed = question.changed, onChoice = { share -> asTheyAre.answer(question, share) }, onDismiss = asTheyAre::forget)
    }

    return asTheyAre
}

/**
 * Asks whether files Orbit couldn't clean go as they are, with what they'd
 * say: where and when they were taken or recorded, and on what, or for
 * ideas' files, who made them, when and where. It names them, by kind and
 * by file, ideas' files by the names they came with, in the viewer's
 * words, and says when it asks again as they `changed` after the person
 * answered. Leaving them out is the choice it starts on.
 */
@Composable
fun AsTheyAreDialog(files: Uncleaned, changed: Boolean = false, onChoice: (asTheyAre: Boolean) -> Unit, onDismiss: () -> Unit) {
    val words = rememberWords()
    val (list, count) = MediaWords.uncleanedList(files, words) ?: return

    val text = when (MediaWords.how(files)) {
        MediaWords.Made.Taken -> R.plurals.sharing_as_they_are_taken
        MediaWords.Made.Recorded -> R.plurals.sharing_as_they_are_recorded
        MediaWords.Made.TakenOrRecorded -> R.plurals.sharing_as_they_are_taken_or_recorded
        MediaWords.Made.Unknown -> R.plurals.sharing_as_they_are_hidden
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(words.plural(R.plurals.sharing_as_they_are_title, count, list))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (changed) {
                    AskedAgain(count)
                }

                Text(words.plural(text, count))
                Text(files.names.map(files::label).sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        // Enter leaves them out: what they say goes only when someone chooses it.
        confirmButton = { TextButton(onClick = { onChoice(false) }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(words.plural(R.plurals.sharing_leave_them_out, count)) } },
        dismissButton = { TextButton(onClick = { onChoice(true) }) { Text(words.plural(R.plurals.sharing_share_them_as_they_are, count)) } },
    )
}

/**
 * The lines under a way out for the files of `files` that can't go clean:
 * those of `chosen` going as they are, with what they say, and the rest
 * staying on this device, each with its icon (`device`, this device's).
 */
fun uncleanedLines(files: Uncleaned, chosen: Set<String>, words: Words, device: ImageVector): List<UncleanedLine> = listOfNotNull(
    MediaWords.uncleaned(files.only(chosen), words, asTheyAre = true)?.let { UncleanedLine(it, goes = true, Icons.Outlined.LocationOn) },
    MediaWords.uncleaned(files.only(files.names - chosen), words, asTheyAre = false)?.let { UncleanedLine(it, goes = false, device) },
)

/** A line that names files that can't go clean: what it says, whether they go, and its icon. */
class UncleanedLine(val text: String, val goes: Boolean, val icon: ImageVector)

/** Says the file or files asked about changed after the person answered about them, so Orbit asks again: as a question's first line. */
@Composable
fun AskedAgain(count: Int) {
    Text(rememberWords().plural(R.plurals.sharing_as_they_are_changed, count), color = MaterialTheme.colorScheme.onSurface)
}
