package com.hyuchia.orbit

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.reminders.EXTRA_IDEA_DUE
import com.hyuchia.orbit.reminders.EXTRA_IDEA_NAME
import com.hyuchia.orbit.reminders.EXTRA_IDEA_PATH
import com.hyuchia.orbit.reminders.IdeaToShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What MainActivity hears from the intents that start it or come to it: its shortcuts and widgets, files and shares. */
@RunWith(AndroidJUnit4::class)
class AppRequestsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = Uri.parse("content://com.example.files/document/Trip.md")

    @Test
    fun orbitsOwnShortcutsOpenAMapOrStartANewOne() {
        assertEquals(AppRequest.NewMap, appRequestOf(context, Intent(ACTION_NEW_MAP)))

        val mine = Intent(ACTION_OPEN_MAP).putExtra(EXTRA_MAP_ID, "trip").putExtra(EXTRA_OPEN_MARK, OpenMark.of(context))
        assertEquals(AppRequest.OpenMap("trip", ownMark = true), appRequestOf(context, mine))
    }

    @Test
    fun aRemindersTapOpensItsMapAtItsIdea() {
        val tapped = Intent(ACTION_OPEN_MAP)
            .putExtra(EXTRA_MAP_ID, "trip")
            .putExtra(EXTRA_OPEN_MARK, OpenMark.of(context))
            .putExtra(EXTRA_IDEA_NAME, "Book the flights")
            .putExtra(EXTRA_IDEA_DUE, "2026-11-18 18:30")
            .putExtra(EXTRA_IDEA_PATH, intArrayOf(0, 2))

        assertEquals(AppRequest.OpenMap("trip", ownMark = true, IdeaToShow("trip", "Book the flights", "2026-11-18 18:30", listOf(0, 2))), appRequestOf(context, tapped))
        // Only Orbit's own intents open a map at an idea: another app's opens the map, if it may, and no more.
        tapped.removeExtra(EXTRA_OPEN_MARK)
        assertEquals(AppRequest.OpenMap("trip", ownMark = false), appRequestOf(context, tapped))
    }

    @Test
    fun anotherAppAskingToOpenAMapIsntTakenForOrbit() {
        // The same action from another app has no mark, or the wrong one: the map opens only while private maps show.
        assertEquals(AppRequest.OpenMap("trip", ownMark = false), appRequestOf(context, Intent(ACTION_OPEN_MAP).putExtra(EXTRA_MAP_ID, "trip")))
        assertEquals(
            AppRequest.OpenMap("trip", ownMark = false),
            appRequestOf(context, Intent(ACTION_OPEN_MAP).putExtra(EXTRA_MAP_ID, "trip").putExtra(EXTRA_OPEN_MARK, "0123456789abcdef0123456789abcdef")),
        )
        // Without a map, there's nothing to open.
        assertNull(appRequestOf(context, Intent(ACTION_OPEN_MAP)))
    }

    @Test
    fun aFileOpenedWithOrbitOrSharedToItIsImported() {
        assertEquals(AppRequest.ImportFile(file), appRequestOf(context, Intent(Intent.ACTION_VIEW, file)))
        assertEquals(AppRequest.ImportFile(file), appRequestOf(context, Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, file)))
        // A share with a file and text is the file.
        assertEquals(
            AppRequest.ImportFile(file),
            appRequestOf(context, Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, file).putExtra(Intent.EXTRA_TEXT, "Trip")),
        )
    }

    @Test
    fun sharedTextIsImportedWithItsSubject() {
        val shared = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "- Lisbon\n- Porto").putExtra(Intent.EXTRA_SUBJECT, "Trip")
        assertEquals(AppRequest.ImportText("- Lisbon\n- Porto", "Trip"), appRequestOf(context, shared))
        assertEquals(AppRequest.ImportText("Lisbon", null), appRequestOf(context, Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "Lisbon")))
    }

    @Test
    fun styledTextSharedFromAnotherAppComesWhole() {
        // A notes app or a browser shares text with its styles, which a String can't hold: its words come all the same.
        val styled = SpannableString("Trip to Lisbon\n- Belém\n- Alfama").apply { setSpan(StyleSpan(Typeface.BOLD), 0, 4, 0) }
        val subject = SpannableString("Weekend").apply { setSpan(StyleSpan(Typeface.ITALIC), 0, 7, 0) }
        val shared = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, styled).putExtra(Intent.EXTRA_SUBJECT, subject)
        assertEquals(AppRequest.ImportText("Trip to Lisbon\n- Belém\n- Alfama", "Weekend"), appRequestOf(context, shared))
    }

    @Test
    fun orbitIsOfferedForTextFilesAndForMapsAnAppCallsPlainText() {
        fun offered(name: String, type: String): Boolean {
            val opening = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.example.files/document/$name"), type)
            return context.packageManager.queryIntentActivities(opening, 0).any { it.activityInfo.name == MainActivity::class.java.name }
        }

        assertTrue(offered("Groceries.txt", "text/plain"))
        assertTrue(offered("Trip.md", "text/plain"))
        assertTrue(offered("Trip.md", "text/markdown"))
        // What isn't a map nor text isn't Orbit's to open.
        assertFalse(offered("Report.pdf", "application/pdf"))
    }

    @Test
    fun textAloneSharedToAttachToAnIdeaJoinsItAsText() {
        // Android offers "Attach to an Orbit idea" for any share, a browser's page included: text, and its subject.
        val attach = Intent(Intent.ACTION_SEND).setClassName(context, "com.hyuchia.orbit.AttachFiles")
        val page = Intent(attach).putExtra(Intent.EXTRA_TEXT, "https://example.com/lisbon").putExtra(Intent.EXTRA_SUBJECT, "Lisbon in three days")

        assertEquals(AppRequest.AttachText("https://example.com/lisbon", "Lisbon in three days"), appRequestOf(context, page))
        assertEquals(AppRequest.AttachText("Call the hotel", null), appRequestOf(context, Intent(attach).putExtra(Intent.EXTRA_TEXT, "Call the hotel")))
        // A file shared there is the file, with text or not.
        assertEquals(AppRequest.AttachFiles(listOf(file)), appRequestOf(context, Intent(attach).putExtra(Intent.EXTRA_STREAM, file).putExtra(Intent.EXTRA_TEXT, "Trip")))
        // Neither, or only blanks: nothing.
        assertNull(appRequestOf(context, attach))
        assertNull(appRequestOf(context, Intent(attach).putExtra(Intent.EXTRA_TEXT, "  ")))
    }

    @Test
    fun anythingElseAsksNothing() {
        assertNull(appRequestOf(context, null))
        assertNull(appRequestOf(context, Intent()))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_MAIN)))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_VIEW)))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_SEND)))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_SEND_MULTIPLE).putExtra(Intent.EXTRA_TEXT, "Lisbon")))
    }
}
