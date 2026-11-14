package com.hyuchia.orbit.journeys

import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Orbit.WELCOME
import com.hyuchia.orbit.journeys.Orbit.WELCOME_CARD
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.described
import com.hyuchia.orbit.journeys.Orbit.drawn
import com.hyuchia.orbit.journeys.Orbit.findOnHome
import com.hyuchia.orbit.journeys.Orbit.focusedField
import com.hyuchia.orbit.journeys.Orbit.home
import com.hyuchia.orbit.journeys.Orbit.label
import com.hyuchia.orbit.journeys.Orbit.onTheMap
import com.hyuchia.orbit.journeys.Orbit.openMap
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Orbit.stopInTheBackground
import com.hyuchia.orbit.journeys.Phone.context
import com.hyuchia.orbit.journeys.Phone.device
import com.hyuchia.orbit.journeys.Phone.gone
import com.hyuchia.orbit.journeys.Phone.shell
import com.hyuchia.orbit.journeys.Phone.shows
import com.hyuchia.orbit.journeys.Phone.tapWhenStill
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What reaches Orbit from outside it, on the build installed, each from a fresh install: a file another app opens
 * with Orbit, text and a file another app shares with it, each a new map Orbit opens; text shared to the idea in
 * focus on the map that's open; another version of a map that's here, asked about again after Android stopped
 * Orbit; and an idea caught from the Quick Settings tile into a map without opening it, typed there before Android
 * stopped Orbit too, the tile bringing it back. The journeys' own app is
 * the other app: its files go to Orbit as content addresses, each lent for its intent, as any app's do.
 */
@RunWith(AndroidJUnit4::class)
class OtherAppJourneys {
    @get:Rule
    val captures = CapturesFailures {
        shell("cmd statusbar collapse")
        shell("cmd statusbar remove-tile $TILE")
    }

    @Before
    fun startFresh() {
        clear()
        start()
        home()
    }

    /** A file of the journeys' own, for Orbit to read, at a content address of their provider. */
    private fun handed(name: String, text: String): Uri {
        val file = File(context.cacheDir, "handed/$name").apply {
            parentFile?.mkdirs()
            writeText(text)
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** A zip of a map as Orbit shares one: its text, and its identity, `uuid`, which tells Orbit it's a map it may have. */
    private fun handedZip(name: String, markdown: String, uuid: String): Uri {
        val file = File(context.cacheDir, "handed/$name").apply { parentFile?.mkdirs() }

        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("map.md"))
            zip.write(markdown.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("orbit.json"))
            zip.write("""{"uuid":"$uuid"}""".toByteArray())
            zip.closeEntry()
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /**
     * Hands Orbit an intent as another app does, to its main screen, or the entry `screen` names: Orbit has to offer
     * it for it, as the share sheet and Open with list what an app's manifest says it takes, and then it goes there.
     */
    private fun send(intent: Intent, screen: String = "$ORBIT.MainActivity") {
        val offered = context.packageManager.queryIntentActivities(Intent(intent).setPackage(ORBIT), 0).map { it.activityInfo.name }
        assertTrue("Orbit isn't offered for ${intent.action} of ${intent.type}: $offered", screen in offered)
        context.startActivity(intent.setClassName(ORBIT, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test
    fun aMarkdownFileOpenedWithOrbitIsANewMapItOpens() {
        val file = handed("lisbon-trip.md", "# Lisbon trip\n\n## Alfama\n\n## Belém\n")
        send(Intent(Intent.ACTION_VIEW).setDataAndType(file, "text/markdown").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))

        drawn("Lisbon trip")
        onTheMap("Alfama")
        // On Home with the rest, found with its search.
        described("Back to your maps").click()
        findOnHome("Lisbon trip")
    }

    @Test
    fun aTextFileAnAppCallsPlainTextOpensAsAMapToo() {
        val file = handed("errands.txt", "Errands\n  Bakery\n  Post office\n")
        send(Intent(Intent.ACTION_VIEW).setDataAndType(file, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))

        drawn("Errands")
        onTheMap("Post office")
    }

    @Test
    fun textSharedWithOrbitIsANewMapWithItsLinesAsIdeas() {
        send(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Packing")
                .putExtra(Intent.EXTRA_TEXT, "Passport\nTickets\n  Train\n  Plane\nCharger"),
        )

        drawn("Packing")
        onTheMap("Passport")
        onTheMap("Train")
    }

    @Test
    fun styledTextSharedWithOrbitComesWhole() {
        // As a notes app or a browser shares a selection: text with its styles, not a plain string.
        val styled = SpannableString("Market\n  Bread\n  Cheese").apply { setSpan(StyleSpan(Typeface.BOLD), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        send(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, styled))

        drawn("Market")
        onTheMap("Cheese")
    }

    @Test
    fun aFileSharedWithOrbitIsANewMap() {
        val outline = handed(
            "picnic.opml",
            """<?xml version="1.0"?><opml version="2.0"><head><title>Picnic</title></head><body><outline text="Picnic"><outline text="Blanket"/><outline text="Lemonade"/></outline></body></opml>""",
        )
        send(
            Intent(Intent.ACTION_SEND)
                .setType("text/x-opml")
                .putExtra(Intent.EXTRA_STREAM, outline)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri(null, outline) },
        )

        drawn("Picnic")
        onTheMap("Lemonade")
    }

    @Test
    fun anotherVersionOfAMapHereIsAskedAboutAgainAfterAndroidStoppedOrbit() {
        val porto = "6f1d1e2a-7c4b-4c1e-9b8f-2d6a0e3c5b71"
        send(Intent(Intent.ACTION_VIEW).setDataAndType(handedZip("porto.zip", "# Porto\n\n## Ribeira\n", porto), "application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        drawn("Porto")

        // The same map again, changed on another device: Orbit asks whether to update the one here.
        val changed = handedZip("porto-changed.zip", "# Porto\n\n## Ribeira\n\n## Livraria Lello\n", porto)
        send(Intent(Intent.ACTION_VIEW).setDataAndType(changed, "application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        label("Another version of “Porto”")

        // Android stops Orbit before it's answered, and brings its screen back: the question is still there.
        stopInTheBackground()
        start()
        label("Another version of “Porto”")
        tapWhenStill(By.clazz("android.widget.TextView").text("Update").pkg(ORBIT), "the question's Update")
        gone(By.text("Another version of “Porto”").pkg(ORBIT), "the question")

        drawn("Porto")
        onTheMap("Livraria Lello")
    }

    @Test
    fun textSharedToAttachToAnOrbitIdeaJoinsTheIdeaInFocus() {
        openMap(WELCOME_CARD)
        drawn(WELCOME)

        // A browser shares a page, its title and address, to "Attach to an Orbit idea": no file, only text.
        send(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Fado houses")
                .putExtra(Intent.EXTRA_TEXT, "https://example.com/fado"),
            screen = "$ORBIT.AttachFiles",
        )

        onTheMap("Fado houses")
    }

    @Test
    fun anIdeaCaughtFromQuickSettingsLandsInTheMap() {
        shell("cmd statusbar add-tile $TILE")
        device.pressHome()

        // Quick Settings pulled down, and Orbit's tile, first there, tapped as people tap it: once it takes a tap, as it
        // shows once Android has bound its service and it listens. A tap that reached it sooner was dropped, on a busy
        // Android 10 ("Managed to get click on non-listening state").
        device.openQuickSettings()
        tapWhenStill(By.desc("Add an idea").clickable(true).pkg(SYSTEM_UI), "Orbit's tile in Quick Settings, taking taps")

        // The sheet to catch ideas opens over whatever is on screen, the map Orbit offers first chosen.
        label("Add to a map")
        shows(By.clazz("android.widget.EditText").text(WELCOME_CARD).pkg(ORBIT), "the map to add to")
        focusedField().text = "Fado night"
        tapWhenStill(By.clazz("android.widget.TextView").text("Add").pkg(ORBIT), "the sheet's Add")
        gone(By.text("Add to a map").pkg(ORBIT), "the sheet")

        start()
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        onTheMap("Fado night")
    }

    @Test
    fun anIdeaTypedInQuickCaptureIsStillThereWhenTheTileOpensItAgain() {
        shell("cmd statusbar add-tile $TILE")
        device.pressHome()
        device.openQuickSettings()
        tapWhenStill(By.desc("Add an idea").clickable(true).pkg(SYSTEM_UI), "Orbit's tile in Quick Settings, taking taps")
        label("Add to a map")
        focusedField().text = "Fado night"

        // Away to the home screen before adding it, and Android stops Orbit there: the tile brings back the sheet as it was.
        stopInTheBackground()
        device.openQuickSettings()
        tapWhenStill(By.desc("Add an idea").clickable(true).pkg(SYSTEM_UI), "Orbit's tile in Quick Settings, taking taps again")
        label("Add to a map")
        shows(By.clazz("android.widget.EditText").text(WELCOME_CARD).pkg(ORBIT), "the map to add to")
        shows(By.clazz("android.widget.EditText").text("Fado night").pkg(ORBIT), "the idea typed before")
        tapWhenStill(By.clazz("android.widget.TextView").text("Add").pkg(ORBIT), "the sheet's Add")
        gone(By.text("Add to a map").pkg(ORBIT), "the sheet")

        start()
        openMap(WELCOME_CARD)
        drawn(WELCOME)
        onTheMap("Fado night")
    }

    private companion object {
        /** Orbit's Quick Settings tile. */
        const val TILE = "$ORBIT/$ORBIT.capture.CaptureTileService"

        /** Android's own screens, Quick Settings among them. */
        const val SYSTEM_UI = "com.android.systemui"
    }
}
