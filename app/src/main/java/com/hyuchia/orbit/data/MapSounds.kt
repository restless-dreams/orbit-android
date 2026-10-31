package com.hyuchia.orbit.data

import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The sounds a map's media folder keeps, by extension. A voice memo is AAC
 * in an `.mp4`, a video's type, which every release of Orbit carries in its
 * zips, backups and synced folder; an `.m4a` from the phone is kept the same
 * way, since it's the same file. The others keep their own extension: this
 * version and later ones carry them, earlier ones keep them on the phone but
 * leave them out of zips and backups.
 */
internal val SOUND_TYPES = mapOf(
    "mp3" to "audio/mpeg",
    "m4a" to "audio/mp4",
    "aac" to "audio/aac",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "opus" to "audio/ogg",
    "wav" to "audio/wav",
    "flac" to "audio/flac",
    "weba" to "audio/webm",
)

/** Whether a name in a map's media folder is a sound's that isn't also a video's (a voice memo's `.mp4` is both). */
fun isSoundName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in SOUND_TYPES

/** A sound's media type, for a page or a player: a voice memo's `.mp4` is audio alone. */
fun soundType(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    return SOUND_TYPES[extension] ?: if (extension == "mp4" || extension == "m4v") "audio/mp4" else "application/octet-stream"
}

/** What a voice memo recorded in Orbit is named: `memo-` and the moment, in the map's `media/`. */
internal const val MEMO_PREFIX = "memo-"

/** What a sound from the phone is named in the map's `media/`: `sound-` and the moment. */
internal const val SOUND_PREFIX = "sound-"

/**
 * Whether a file in a map's `media/` is a sound: a sound file, or an `.mp4`
 * Orbit named as one, a voice memo or an `.m4a` from the phone. A sound
 * that's only an `.mp4` some other app named is a video by its name, but
 * plays as a sound all the same, its idea's `audio` says so.
 */
fun isSoundFile(name: String): Boolean = isSoundName(name) || (name.endsWith(".mp4") && (name.startsWith(MEMO_PREFIX) || name.startsWith(SOUND_PREFIX)))

/**
 * The files in a map's `media/` its text plays as sounds, in the order the
 * map gives them: its ideas' `audio` (`sound`, `voice` and `recording` say
 * the same) in their `<!-- orbit … -->` attributes, on its line
 * (`audio: media/…`, or `audio: { src: media/… }`) or with its `src` on
 * the lines under it. That's what makes a file a sound, whatever its name:
 * a voice memo is an `.mp4`. A sound on the web is no file of the map's,
 * and words in a note are only words.
 */
fun soundNamesIn(markdown: String): List<String> {
    val names = LinkedHashSet<String>()

    for (attributes in OrbitAttributes.findAll(markdown)) {
        val lines = attributes.groupValues[1].lines()

        lines.forEachIndexed { index, line ->
            for (key in SoundKey.findAll(line)) {
                val value = key.groupValues[1]
                val inline = SoundFile.find(value)

                if (inline != null) {
                    names += inline.groupValues[1]
                    continue
                }

                if (value.isNotBlank()) {
                    continue
                }

                // Its fields on the lines under it, further in: its `src` among them.
                val indent = line.indexOfFirst { !it.isWhitespace() }

                for (next in lines.drop(index + 1)) {
                    if (next.isBlank() || next.indexOfFirst { !it.isWhitespace() } <= indent) {
                        break
                    }

                    val source = SoundSource.find(next) ?: continue
                    names += source.groupValues[1]
                    break
                }
            }
        }
    }

    return names.toList()
}

/** An idea's attributes in orbit-md: `<!-- orbit key: value -->`, or a block of them. */
private val OrbitAttributes = Regex("""<!--\s*orbit\b(.*?)-->""", RegexOption.DOT_MATCHES_ALL)

/** An idea's sound, and what follows it on its line. The spaces after its colon are read once: a long run of them costs one pass, even on a line that turns out not to say one. */
private val SoundKey = Regex("""(?:^|[\s,{])(?:audio|sound|voice|recording)\s*:\s*+(.*)$""")

/** A file of the map's as a sound's value: on its own, or as the `src` of an object, other fields maybe before it. */
private val SoundFile = Regex("""^\{?\s*(?:[A-Za-z]+\s*:[^,}]*,\s*)*(?:src\s*:\s*)?["']?(?:\./)?$MEDIA_DIRECTORY/(${MediaNames.PATTERN})""")

/** A sound's `src` on a line of its own. */
private val SoundSource = Regex("""^\s*src\s*:\s*["']?(?:\./)?$MEDIA_DIRECTORY/(${MediaNames.PATTERN})""")

/**
 * The most the sounds inside a web page of a map may come to, as they go
 * ([soundsFitting]): a voice memo is about a megabyte a minute, and a page
 * with more than this is slow to open. Sounds past it stay out, in the
 * map's order.
 */
internal const val PAGE_SOUNDS_LIMIT = 25L * 1024 * 1024

/** A sound that goes inside a web page of its map: its file, and how it leaves the phone ([OutgoingMedia]). */
internal class PageSound(val file: File, val outgoing: OutgoingFile)

/**
 * The map's sounds that go inside its web page, as they leave the phone for
 * other people ([OutgoingMedia]: a voice memo without its dates, an MP3
 * without its tags): the files its ideas play as sounds ([soundNamesIn]),
 * in the order the map gives them, as long as they come to at most `limit`
 * bytes. One past what's left of it stays out, and a smaller one after it
 * may still go; so does one Orbit can't read through, unless it's one of
 * `asTheyAre`, which the person sharing chose to share as they are.
 */
internal fun soundsFitting(markdown: String, mapFolder: File, limit: Long = PAGE_SOUNDS_LIMIT, asTheyAre: Set<String> = emptySet()): List<PageSound> {
    var room = limit
    val media = File(mapFolder, MEDIA_DIRECTORY)

    return soundNamesIn(markdown).mapNotNull { name ->
        val file = File(media, name).takeIf { it.isFile && isMediaName(name) } ?: return@mapNotNull null
        val outgoing = OutgoingMedia.of(file, asTheyAre)?.takeIf { it.size <= room } ?: return@mapNotNull null
        room -= outgoing.size
        PageSound(file, outgoing)
    }
}

/** Whether a sound is a voice memo recorded in Orbit, by the name Orbit gave it. */
fun isVoiceMemo(src: String): Boolean = src.substringAfterLast('/').startsWith(MEMO_PREFIX)

/**
 * The extension a sound from the phone is kept with: by what its first bytes,
 * `head`, say it is, else by its media type, else by its name when the type
 * says nothing. The bytes come first because Android's own types call an
 * `.m4a` "audio/mpeg". An `.m4a` (MP4 audio) is kept as `.mp4`, the same
 * bytes, so every release carries it. Null for a sound Orbit can't keep in a
 * map's folder, such as AMR, which browsers can't play, and for a file whose
 * type says it's something else, such as a video.
 */
fun soundExtension(mimeType: String?, displayName: String?, head: ByteArray? = null): String? {
    val type = mimeType?.substringBefore(';')?.trim()?.lowercase()
    val unsaid = type == null || type == "application/octet-stream" || type == "audio/*"

    if (!unsaid && !type.startsWith("audio/") && type != "application/ogg") {
        return null
    }

    val said = saidExtension(type, unsaid, displayName)

    return when (val heard = head?.let(::soundKind)) {
        null -> said
        "amr", "3gp" -> null
        // A tag says nothing of what follows it: MP3, mostly, or AAC.
        "id3" -> if (said == "aac") "aac" else "mp3"
        else -> heard
    }
}

/** The extension a sound's type, or its name when the type is `unsaid`, gives it. */
private fun saidExtension(type: String?, unsaid: Boolean, displayName: String?): String? {
    val byType = when (type) {
        "audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg-3" -> "mp3"
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/mp4a-latm" -> "mp4"
        "audio/aac", "audio/aacp", "audio/x-aac" -> "aac"
        "audio/ogg", "application/ogg", "audio/vorbis" -> if (displayName?.endsWith(".opus", ignoreCase = true) == true) "opus" else "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "wav"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/webm" -> "weba"
        else -> null
    }

    if (byType != null) {
        return byType
    }

    val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()

    return when {
        !unsaid || extension.isEmpty() -> null
        extension == "m4a" -> "mp4"
        extension in SOUND_TYPES -> extension
        else -> null
    }
}

/**
 * What a sound's first bytes say it is: the extension Orbit keeps it with,
 * "amr" or "3gp" for kinds browsers can't play, "id3" for a tag that comes
 * before the sound, or null when they don't say.
 */
internal fun soundKind(head: ByteArray): String? {
    fun text(at: Int, length: Int) = if (head.size >= at + length) String(head, at, length, Charsets.ISO_8859_1) else ""

    fun byte(at: Int) = if (head.size > at) head[at].toInt() and 0xFF else -1

    return when {
        text(4, 4) == "ftyp" -> if (text(8, 2) == "3g") "3gp" else "mp4"
        text(0, 4) == "OggS" -> if (text(28, 8) == "OpusHead") "opus" else "ogg"
        text(0, 4) == "fLaC" -> "flac"
        text(0, 4) == "RIFF" && text(8, 4) == "WAVE" -> "wav"
        byte(0) == 0x1A && byte(1) == 0x45 && byte(2) == 0xDF && byte(3) == 0xA3 -> "weba"
        text(0, 5) == "#!AMR" -> "amr"
        text(0, 3) == "ID3" -> "id3"
        // A frame's sync: AAC's ADTS has layer 0, MP3's frames don't.
        byte(0) == 0xFF && (byte(1) and 0xF6) == 0xF0 -> "aac"
        byte(0) == 0xFF && (byte(1) and 0xE0) == 0xE0 && (byte(1) and 0x06) != 0 -> "mp3"
        else -> null
    }
}

/** How many of a sound's first bytes [soundKind] reads: as far as an Ogg page's Opus header. */
internal const val SOUND_HEAD = 36

/** The first `count` bytes of a stream, or fewer when it's shorter, which then reads on from where it was. */
internal fun BufferedInputStream.peek(count: Int): ByteArray {
    mark(count)
    val head = ByteArray(count)
    val read = readUpTo(head)
    reset()
    return head.copyOf(read)
}

/** A name for a sound from the phone without its extension, as its caption: "Interview with Ana" for `Interview with Ana.mp3`. Null for names that say nothing. */
fun soundCaption(displayName: String?): String? {
    val name = displayName?.substringBeforeLast('.')?.replace('_', ' ')?.trim().orEmpty()
    // Names a recorder or a camera made up say nothing to people: "AUD-20261021-WA0004", "Recording_12".
    val madeUp = Regex("""(?i)^(aud|audio|rec|recording|voice|ptt|sound|track)[\s\-_]*[\d\s\-_]*(wa\d+)?$""")
    return name.takeIf { it.isNotEmpty() && !madeUp.matches(it) }
}

/** Why a sound of `size` bytes can't join a map whose pictures, videos and sounds come to `mapMedia`, in `words`, or null when it can. */
fun soundRefusal(size: Long, mapMedia: Long, words: Words): String? = when {
    size > VIDEO_SIZE_LIMIT -> words.string(R.string.data_sound_too_big, megabytes(size, words), megabytes(VIDEO_SIZE_LIMIT, words))
    mapMedia + size > MAP_MEDIA_LIMIT -> words.string(R.string.data_sound_past_map_room, megabytes(mapMedia, words), megabytes(MAP_MEDIA_LIMIT, words))
    else -> null
}

/**
 * Copies a sound into `output`, telling `onProgress` how far along it is,
 * from 0 to 1, when its `size` is known (0 or less when it isn't). A sound
 * that turns out bigger than a video may be, whatever size it said it was, is
 * refused, saying why in `words`. `keepGoing` throws to stop the copy.
 */
internal fun copySound(input: InputStream, output: OutputStream, size: Long, words: Words, onProgress: (Float) -> Unit, keepGoing: () -> Unit = {}) {
    val buffer = ByteArray(SOUND_COPY_BUFFER)
    var copied = 0L
    var reported = -1

    while (true) {
        keepGoing()
        val read = input.read(buffer)

        if (read < 0) {
            break
        }

        copied += read

        if (copied > VIDEO_SIZE_LIMIT) {
            throw MediaRefusedException(words.string(R.string.data_sound_past_limit, megabytes(VIDEO_SIZE_LIMIT, words)))
        }

        output.write(buffer, 0, read)

        if (size > 0) {
            val percent = (copied * 100 / size).toInt().coerceAtMost(100)

            if (percent != reported) {
                reported = percent
                onProgress(percent / 100f)
            }
        }
    }
}

private const val SOUND_COPY_BUFFER = 256 * 1024
