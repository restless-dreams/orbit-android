package com.hyuchia.orbit

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun anythingElseAsksNothing() {
        assertNull(appRequestOf(context, null))
        assertNull(appRequestOf(context, Intent()))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_MAIN)))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_VIEW)))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_SEND)))
        assertNull(appRequestOf(context, Intent(Intent.ACTION_SEND_MULTIPLE).putExtra(Intent.EXTRA_TEXT, "Lisbon")))
    }
}
