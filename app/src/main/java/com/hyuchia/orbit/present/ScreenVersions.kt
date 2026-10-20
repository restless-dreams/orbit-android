package com.hyuchia.orbit.present

import androidx.annotation.StringRes
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.page.AudienceCommand
import com.hyuchia.orbit.page.MapMessage
import com.hyuchia.orbit.page.protocolJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/*
 * Which version of their messages the phone and a TV's receiver speak
 * (web/receiver-protocol.ts). The receiver is one page that every version
 * of the app casts to, so it only ever adds to what it understands. The
 * phone says hello first; a receiver from before versions were said
 * doesn't answer, and follows every command this app sends.
 */

/** The protocol this app speaks to a TV: 2 says hello first and hears the receiver's answer. */
const val AUDIENCE_PROTOCOL = 2

/** The oldest receiver this app can present on. Every receiver published so far follows every command it sends. */
const val OLDEST_RECEIVER_PROTOCOL = 1

/** How long a TV has to answer the phone's hello before it counts as a receiver from before versions were said, in ms. */
const val RECEIVER_ANSWER_WAIT = 3_000L

/** What the phone says to a TV first: the protocol it speaks, and its own version for the receiver's console. */
@Serializable
data class SenderHello(val protocol: Int, val app: String, val type: String = "hello")

/** What a TV's receiver answers: its protocol, the newest map format (`schemaVersion`) its Orbit reads, and its Orbit. */
@Serializable
data class ReceiverAnnouncement(val protocol: Int, val format: Int, val orbit: String? = null)

/** A receiver that never answered: one published before receivers said their version, with an Orbit that reads format 1. */
val SILENT_RECEIVER = ReceiverAnnouncement(protocol = 1, format = 1)

/** What the presenter hears about the TV they present on. */
enum class ScreenNotice(@StringRes val message: Int) {
    /** The TV's receiver is older than any this app can present on. */
    TooOld(R.string.present_tv_too_old),

    /** The map is written for a newer format than the TV's Orbit reads, which draws it as well as it can. */
    OlderMaps(R.string.present_tv_older_maps),
}

/** What the presenter should hear about a TV whose receiver said `receiver`, for a map of format `mapFormat`: null for nothing. */
fun screenNotice(receiver: ReceiverAnnouncement, mapFormat: Int): ScreenNotice? = when {
    receiver.protocol < OLDEST_RECEIVER_PROTOCOL -> ScreenNotice.TooOld
    mapFormat > receiver.format -> ScreenNotice.OlderMaps
    else -> null
}

/**
 * The protocol a command first came in: a TV whose receiver is older
 * doesn't get it. A new command has to say its own here.
 */
val AudienceCommand.protocol: Int
    get() = when (this) {
        is AudienceCommand.Show,
        is AudienceCommand.Step,
        is AudienceCommand.Captions,
        is AudienceCommand.Media,
        is AudienceCommand.Playback,
        is AudienceCommand.Seek,
        AudienceCommand.CloseMedia,
        AudienceCommand.End,
        -> 1
    }

/**
 * What the phone knows of the TV it presents on: the receiver's answer to
 * its hello (or, once it waited [RECEIVER_ANSWER_WAIT], a receiver from
 * before versions were said), and the format of the map it shows. From
 * those it decides what the TV gets and what the presenter hears.
 */
class ReceiverWatch {
    @Volatile
    private var receiver: ReceiverAnnouncement? = null

    @Volatile
    private var mapFormat = 1

    private val shown = MutableStateFlow<ScreenNotice?>(null)

    /** What the presenter should hear about the TV, once the phone knows which receiver it runs. */
    val notice: StateFlow<ScreenNotice?> = shown

    /** The receiver, as far as the phone knows: one that hasn't answered yet counts as a silent one. */
    val known: ReceiverAnnouncement get() = receiver ?: SILENT_RECEIVER

    /**
     * A message from the TV: the receiver's answer, which the phone keeps,
     * or how the TV's video goes, which it returns. Anything else, from a
     * newer receiver say, is left alone.
     */
    fun heard(message: String): MapMessage.MediaState? {
        val json = runCatching { protocolJson.parseToJsonElement(message).jsonObject }.getOrNull() ?: return null

        if ((json["type"] as? JsonPrimitive)?.content == "receiver") {
            runCatching { protocolJson.decodeFromJsonElement(ReceiverAnnouncement.serializer(), json) }.getOrNull()?.let {
                receiver = it
                update()
            }

            return null
        }

        return runCatching { protocolJson.decodeFromJsonElement(MapMessage.serializer(), json) }.getOrNull() as? MapMessage.MediaState
    }

    /** No answer came in time: a receiver from before versions were said. An answer later still counts. */
    fun silent() {
        if (receiver == null) {
            receiver = SILENT_RECEIVER
            update()
        }
    }

    /** The TV is about to show `markdown`. */
    fun showing(markdown: String) {
        mapFormat = MapText.formatVersion(markdown)
        update()
    }

    /** Whether the TV's receiver follows `command`: one from a newer protocol is left out rather than sent. */
    fun follows(command: AudienceCommand): Boolean = command.protocol <= known.protocol

    private fun update() {
        shown.value = receiver?.let { screenNotice(it, mapFormat) }
    }
}
