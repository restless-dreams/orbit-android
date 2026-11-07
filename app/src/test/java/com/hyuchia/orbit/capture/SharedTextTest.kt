package com.hyuchia.orbit.capture

import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** What "Add to an Orbit map" in another app's share sheet catches as ideas. */
@RunWith(AndroidJUnit4::class)
class SharedTextTest {
    @Test
    fun styledTextSharedFromAnotherAppComesWhole() {
        // A notes app or a browser shares text with its styles, which a String can't hold: its words come all the same.
        val styled = SpannableString("Call the venue\nBook the band").apply { setSpan(StyleSpan(Typeface.BOLD), 0, 4, 0) }
        val subject = SpannableString("Party").apply { setSpan(StyleSpan(Typeface.ITALIC), 0, 5, 0) }
        assertEquals("Party\nCall the venue\nBook the band", sharedText(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, styled).putExtra(Intent.EXTRA_SUBJECT, subject)))
    }

    @Test
    fun plainTextComesAsItWasAndASubjectThatSaysTheSameComesOnce() {
        assertEquals("Call the venue", sharedText(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "Call the venue")))
        assertEquals("Call the venue", sharedText(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "Call the venue").putExtra(Intent.EXTRA_SUBJECT, "Call the venue")))
        assertEquals("", sharedText(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, "  ")))
    }
}
