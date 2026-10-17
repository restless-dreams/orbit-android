package com.hyuchia.orbit.media

import com.hyuchia.orbit.page.Media
import java.net.URI
import java.net.URISyntaxException

/** YouTube's hosts, as Orbit reads video links. */
private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com", "youtu.be")

/** A YouTube video's id, as Orbit takes them. */
private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{6,}")

/** A YouTube or Vimeo address written without its scheme, such as `youtu.be/…`. */
private val BARE_HOST = Regex("^(?:(?:www|m|music|player)\\.)?(?:youtube\\.com|youtube-nocookie\\.com|youtu\\.be|vimeo\\.com)/", RegexOption.IGNORE_CASE)

/** A web address, by its parts, for http and https links (with or without their scheme, for YouTube and Vimeo); null for anything else. */
internal fun webAddress(url: String): URI? {
    val text = url.trim()

    val absolute = when {
        text.startsWith("//") -> "https:$text"
        BARE_HOST.containsMatchIn(text) -> "https://$text"
        else -> text
    }

    return try {
        URI(absolute).takeIf { (it.scheme.equals("https", ignoreCase = true) || it.scheme.equals("http", ignoreCase = true)) && it.host != null }
    } catch (malformed: URISyntaxException) {
        null
    }
}

/** A query parameter of a web address, decoded. */
internal fun URI.parameter(name: String): String? =
    rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.let { java.net.URLDecoder.decode(it, "UTF-8") }

/** A YouTube video's id, from any of its links: watch pages, youtu.be, embeds, Shorts and lives. */
fun youtubeId(url: String): String? {
    val address = webAddress(url) ?: return null
    val host = address.host.lowercase()

    if (host !in YOUTUBE_HOSTS) {
        return null
    }

    val segments = address.path.orEmpty().split('/').filter { it.isNotEmpty() }

    val id = when {
        host == "youtu.be" -> segments.firstOrNull()
        segments.firstOrNull() == "watch" -> address.parameter("v")
        segments.firstOrNull() in setOf("embed", "shorts", "live", "v") -> segments.getOrNull(1)
        else -> null
    }

    return id?.takeIf { YOUTUBE_ID.matches(it) }
}

/** The picture a video shows before it plays: its own poster, or for a YouTube video, YouTube's. */
fun posterOf(video: Media): String? =
    video.thumbnail?.takeIf { it.isNotBlank() }
        ?: youtubeId(video.src)?.takeIf { video.provider != "vimeo" && video.provider != "native" }?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }

/** Video files Orbit plays itself from the web, by the end of their address. */
private val VIDEO_FILE = Regex("\\.(mp4|m4v|webm|mov)$", RegexOption.IGNORE_CASE)

/** Vimeo's hosts. */
private val VIMEO_HOSTS = setOf("vimeo.com", "www.vimeo.com", "player.vimeo.com")

/** Where a video plays from, and so which player plays it. */
sealed interface VideoSource {
    /** A video file in the map's folder. */
    data class InMap(val file: java.io.File) : VideoSource

    /** A video file on the web, at a secure address. */
    data class OnTheWeb(val url: String) : VideoSource

    /** A YouTube video, from `start` seconds in when its link says so. */
    data class YouTube(val id: String, val start: Int? = null) : VideoSource

    /** A Vimeo video, with the hash an unlisted one needs. */
    data class Vimeo(val id: String, val hash: String? = null) : VideoSource

    /** A page or a video Orbit doesn't play itself: it opens in the app for it, or the browser. */
    data class Elsewhere(val url: String) : VideoSource
}

/**
 * Where `video` plays from: `resolve` says what its address is, a file in
 * the map's folder or a web address, as for pictures. Null when there's
 * nothing to play, as for a file that isn't in the map's folder.
 */
fun videoSource(video: Media, resolve: (String) -> Any?): VideoSource? {
    val resolved = resolve(video.src)

    if (resolved is java.io.File) {
        return VideoSource.InMap(resolved)
    }

    val address = webAddress(video.src) ?: return null
    val host = address.host.lowercase()

    if (video.provider != "vimeo" && video.provider != "native") {
        youtubeId(video.src)?.let { return VideoSource.YouTube(it, startSeconds(address)) }
    }

    if (video.provider != "youtube" && video.provider != "native" && host in VIMEO_HOSTS) {
        vimeoVideo(address)?.let { (id, hash) -> return VideoSource.Vimeo(id, hash) }
    }

    val secure = address.scheme.equals("https", ignoreCase = true)
    val file = video.provider == "native" || VIDEO_FILE.containsMatchIn(address.path.orEmpty())
    return if (secure && file) VideoSource.OnTheWeb(address.toString()) else VideoSource.Elsewhere(address.toString())
}

/** A Vimeo video's id, and the hash an unlisted one needs, from its page (channels, groups and showcases too) or its player. */
internal fun vimeoVideo(address: URI): Pair<String, String?>? {
    val segments = address.path.orEmpty().split('/').filter { it.isNotEmpty() }
    val hash = { value: String? -> value?.takeIf { it.matches(Regex("[0-9a-fA-F]{6,}")) } }
    val number = Regex("\\d+")

    if (address.host.equals("player.vimeo.com", ignoreCase = true)) {
        val id = segments.getOrNull(1)?.takeIf { segments.firstOrNull() == "video" && number.matches(it) } ?: return null
        return id to hash(address.parameter("h"))
    }

    if (segments.firstOrNull()?.let(number::matches) == true) {
        return segments[0] to hash(segments.getOrNull(1))
    }

    val index = when {
        segments.firstOrNull() == "video" -> 1
        segments.firstOrNull() == "channels" -> 2
        segments.firstOrNull() == "groups" && segments.getOrNull(2) == "videos" -> 3
        (segments.firstOrNull() == "album" || segments.firstOrNull() == "showcase") && segments.getOrNull(2) == "video" -> 3
        else -> return null
    }

    val id = segments.getOrNull(index)?.takeIf(number::matches) ?: return null
    return id to hash(segments.getOrNull(index + 1))
}

/** Where a YouTube link starts the video, in seconds: `t=90`, `t=1m30s` or `start=90`. */
internal fun startSeconds(address: URI): Int? {
    val value = address.parameter("t") ?: address.parameter("start") ?: return null
    value.toIntOrNull()?.let { return it.takeIf { seconds -> seconds > 0 } }
    val parts = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$").matchEntire(value) ?: return null
    val (hours, minutes, seconds) = parts.destructured
    val total = (hours.toIntOrNull() ?: 0) * 3_600 + (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
    return total.takeIf { it > 0 }
}

/** A YouTube video's page, which YouTube's app opens when the phone has it. */
fun watchUrl(source: VideoSource.YouTube): String = "https://www.youtube.com/watch?v=${source.id}" + source.start?.let { "&t=${it}s" }.orEmpty()

/** A Vimeo video's page, which Vimeo's app opens when the phone has it. */
fun watchUrl(source: VideoSource.Vimeo): String = "https://vimeo.com/${source.id}" + source.hash?.let { "/$it" }.orEmpty()

/**
 * The address YouTube and Vimeo see the player's page at: the app's id as a
 * web address, as YouTube asks of apps that embed its player. YouTube's
 * player refuses to play ("Error 153") for a page that doesn't say where it
 * is, and this one says so in the Referer of every request it makes.
 */
fun playerOrigin(applicationId: String): String = "https://$applicationId"

/** The hosts the players' own frames come from: nothing else loads inside the player's page. */
private val PLAYER_HOSTS = setOf("www.youtube-nocookie.com", "youtube-nocookie.com", "www.youtube.com", "youtube.com", "player.vimeo.com", "vimeo.com")

fun isPlayerHost(host: String?): Boolean = host?.lowercase() in PLAYER_HOSTS

/**
 * The provider's own player for a video, playing at once: YouTube's in its
 * privacy-enhanced mode, which keeps no cookies, and Vimeo's told not to
 * track. YouTube's takes commands from `origin`, the page around it.
 */
fun embedUrl(source: VideoSource, origin: String): String? = when (source) {
    is VideoSource.YouTube -> "https://www.youtube-nocookie.com/embed/${source.id}?autoplay=1&playsinline=1&rel=0&enablejsapi=1" +
        "&origin=${java.net.URLEncoder.encode(origin, "UTF-8")}" + source.start?.let { "&start=$it" }.orEmpty()
    is VideoSource.Vimeo -> "https://player.vimeo.com/video/${source.id}?autoplay=1&dnt=1" + source.hash?.let { "&h=$it" }.orEmpty()
    else -> null
}

/**
 * What the player's page does for the app: `window.orbitPlayer` pauses,
 * plays or pauses, seeks and mutes the provider's player through its own
 * messages. YouTube's player says how it's doing once asked to
 * (`listening`), and the page keeps track; Vimeo's answers each question,
 * and the page acts on the answer.
 */
private val YOUTUBE_SCRIPT = """
const origin = 'https://www.youtube-nocookie.com';
const heard = { playing: true, time: 0, muted: false };
const command = (name, values) => player.contentWindow.postMessage(JSON.stringify({ event: 'command', func: name, args: values || [] }), origin);
const playing = (state) => state === 1 || state === 3;
window.addEventListener('message', (event) => {
  if (event.origin !== origin || typeof event.data !== 'string') {
    return;
  }
  let data;
  try {
    data = JSON.parse(event.data);
  } catch (error) {
    return;
  }
  const info = data.info;
  if (data.event === 'onStateChange' && typeof info === 'number') {
    heard.playing = playing(info);
  }
  if ((data.event === 'infoDelivery' || data.event === 'initialDelivery') && info && typeof info === 'object') {
    if (typeof info.playerState === 'number') {
      heard.playing = playing(info.playerState);
    }
    if (typeof info.currentTime === 'number') {
      heard.time = info.currentTime;
    }
    if (typeof info.muted === 'boolean') {
      heard.muted = info.muted;
    }
  }
});
player.addEventListener('load', () => player.contentWindow.postMessage(JSON.stringify({ event: 'listening', id: 1, channel: 'widget' }), origin));
window.orbitPlayer = {
  pause: () => command('pauseVideo'),
  toggle: () => {
    command(heard.playing ? 'pauseVideo' : 'playVideo');
    heard.playing = !heard.playing;
  },
  seek: (by) => {
    heard.time = Math.max(0, heard.time + by);
    command('seekTo', [heard.time, true]);
  },
  mute: () => {
    command(heard.muted ? 'unMute' : 'mute');
    heard.muted = !heard.muted;
  },
};
"""

private val VIMEO_SCRIPT = """
const origin = 'https://player.vimeo.com';
const call = (method, value) => player.contentWindow.postMessage(JSON.stringify(value === undefined ? { method } : { method, value }), origin);
const waiting = { seek: 0, toggle: false, mute: false };
window.addEventListener('message', (event) => {
  if (event.origin !== origin) {
    return;
  }
  let data = event.data;
  if (typeof data === 'string') {
    try {
      data = JSON.parse(data);
    } catch (error) {
      return;
    }
  }
  if (!data || typeof data !== 'object') {
    return;
  }
  if (data.method === 'getCurrentTime' && typeof data.value === 'number' && waiting.seek) {
    call('setCurrentTime', Math.max(0, data.value + waiting.seek));
    waiting.seek = 0;
  }
  if (data.method === 'getPaused' && waiting.toggle) {
    call(data.value ? 'play' : 'pause');
    waiting.toggle = false;
  }
  if (data.method === 'getMuted' && waiting.mute) {
    call('setMuted', !data.value);
    waiting.mute = false;
  }
});
window.orbitPlayer = {
  pause: () => call('pause'),
  toggle: () => {
    waiting.toggle = true;
    call('getPaused');
  },
  seek: (by) => {
    waiting.seek += by;
    call('getCurrentTime');
  },
  mute: () => {
    waiting.mute = true;
    call('getMuted');
  },
};
"""

/**
 * The page the provider's player plays in: the player filling it, on black,
 * and nothing else it can load. Its script gives the player the keyboard as
 * it loads, and takes the app's keys and pauses (`window.orbitPlayer`).
 */
fun playerPage(source: VideoSource, title: String, origin: String): String? {
    val embed = embedUrl(source, origin) ?: return null

    val (frameOrigin, script) = when (source) {
        is VideoSource.Vimeo -> "https://player.vimeo.com" to VIMEO_SCRIPT
        else -> "https://www.youtube-nocookie.com" to YOUTUBE_SCRIPT
    }

    val escapedTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; frame-src $frameOrigin; script-src 'unsafe-inline'; style-src 'unsafe-inline'">
<meta name="referrer" content="strict-origin-when-cross-origin">
<title>$escapedTitle</title>
<style>html, body { margin: 0; height: 100%; background: #000; overflow: hidden; } iframe { position: fixed; inset: 0; width: 100%; height: 100%; border: 0; }</style>
</head>
<body>
<iframe id="player" src="$embed" title="$escapedTitle" allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowfullscreen></iframe>
<script>
const player = document.getElementById('player');
player.addEventListener('load', () => player.focus());
$script
</script>
</body>
</html>
"""
}
