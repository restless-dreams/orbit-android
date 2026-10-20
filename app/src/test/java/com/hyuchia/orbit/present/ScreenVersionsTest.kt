package com.hyuchia.orbit.present

import com.hyuchia.orbit.media.MediaKind
import com.hyuchia.orbit.page.AppTheme
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.protocolJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenVersionsTest {
    private val everyCommand: List<AudienceCommand> = listOf(
        AudienceCommand.Show("# Talk", AppTheme(dark = false, tokens = emptyMap())),
        AudienceCommand.Step("talk", 0, 1),
        AudienceCommand.Captions(true),
        AudienceCommand.Media("tram", MediaKind.Video),
        AudienceCommand.Playback(true),
        AudienceCommand.Seek(4.0),
        AudienceCommand.CloseMedia,
        AudienceCommand.End,
    )

    private val announcement = """{"type":"receiver","protocol":2,"format":1,"orbit":"f62a16c"}"""

    @Test
    fun thePhoneSaysHelloWithItsProtocolAndVersion() {
        assertEquals(
            """{"protocol":2,"app":"0.3.0","type":"hello"}""",
            protocolJson.encodeToString(SenderHello.serializer(), SenderHello(AUDIENCE_PROTOCOL, "0.3.0")),
        )
    }

    @Test
    fun everyCommandSoFarIsOneEveryReceiverFollows() {
        for (command in everyCommand) {
            assertEquals(1, command.protocol)
            assertTrue(command.protocol <= AUDIENCE_PROTOCOL)
        }
    }

    @Test
    fun aReceiverFromBeforeVersionsWereSaidFollowsEverythingThisAppSends() {
        val watch = ReceiverWatch()

        for (command in everyCommand) {
            assertTrue(watch.follows(command))
        }

        assertEquals(SILENT_RECEIVER, watch.known)
    }

    @Test
    fun thePresenterHearsNothingUntilTheReceiverAnswersOrTheWaitIsOver() {
        val watch = ReceiverWatch()
        watch.showing("---\nschemaVersion: 2\n---\n# Talk\n")
        assertNull(watch.notice.value)
        watch.silent()
        assertEquals(ScreenNotice.OlderMaps, watch.notice.value)
    }

    @Test
    fun theReceiversAnswerIsKeptAndNotTakenForAVideoReport() {
        val watch = ReceiverWatch()
        assertNull(watch.heard(announcement))
        assertEquals(ReceiverAnnouncement(protocol = 2, format = 1, orbit = "f62a16c"), watch.known)
        watch.showing("# Talk\n")
        assertNull(watch.notice.value)
        // An answer that came late still counts over the silent receiver it was taken for.
        watch.silent()
        assertEquals(2, watch.known.protocol)
    }

    @Test
    fun aNewerReceiverSaysMoreThanThisAppKnowsAndIsStillRead() {
        val watch = ReceiverWatch()
        watch.heard("""{"type":"receiver","protocol":5,"format":3,"orbit":"abc1234","features":["sparkles"]}""")
        assertEquals(ReceiverAnnouncement(protocol = 5, format = 3, orbit = "abc1234"), watch.known)
        watch.showing("---\nschemaVersion: 3\n---\n# Talk\n")
        assertNull(watch.notice.value)
    }

    @Test
    fun aMapNewerThanTheTVsOrbitReadsIsShownAndThePresenterTold() {
        val watch = ReceiverWatch()
        watch.heard(announcement)
        watch.showing("nodes:\n  - text: Talk\nschemaVersion: 2\n")
        assertEquals(ScreenNotice.OlderMaps, watch.notice.value)
        assertTrue(watch.follows(everyCommand.first()))
        watch.showing("# Talk\n")
        assertNull(watch.notice.value)
    }

    @Test
    fun aReceiverOlderThanAnyThisAppPresentsOnIsSaid() {
        assertEquals(ScreenNotice.TooOld, screenNotice(ReceiverAnnouncement(protocol = OLDEST_RECEIVER_PROTOCOL - 1, format = 1), 1))
        assertNull(screenNotice(ReceiverAnnouncement(protocol = OLDEST_RECEIVER_PROTOCOL, format = 1), 1))
        assertEquals(ScreenNotice.OlderMaps, screenNotice(ReceiverAnnouncement(protocol = OLDEST_RECEIVER_PROTOCOL, format = 1), 2))
    }

    @Test
    fun howTheTVsVideoGoesStillReachesThePresenter() {
        val watch = ReceiverWatch()
        val report = watch.heard("""{"type":"mediaState","id":"tram","playing":true,"time":12.5,"duration":60.0,"ended":false,"muted":false}""")
        assertEquals(MapMessage.MediaState("tram", playing = true, time = 12.5, duration = 60.0), report)
    }

    @Test
    fun whatThePhoneCantReadIsLeftAlone() {
        val watch = ReceiverWatch()
        assertNull(watch.heard("not json"))
        assertNull(watch.heard("""{"type":"something new","at":1}"""))
        assertNull(watch.heard("""{"type":"receiver","protocol":"two"}"""))
        assertEquals(SILENT_RECEIVER, watch.known)
    }
}
