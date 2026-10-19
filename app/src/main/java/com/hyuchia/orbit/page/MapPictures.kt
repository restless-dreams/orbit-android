package com.hyuchia.orbit.page

import android.util.Base64
import com.hyuchia.orbit.data.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.security.MessageDigest

/** Thumbnails are drawn at this size in CSS pixels, twice over for sharp screens. */
const val THUMBNAIL_WIDTH = 480
const val THUMBNAIL_HEIGHT = 300

/** What a picture of a map is drawn from, and how. */
data class PictureRequest(
    /** The map to draw; null draws the map on the page as it is now. */
    val markdown: String? = null,

    val format: String = "png",
    val width: Int? = null,
    val height: Int? = null,
    val scale: Double = 2.0,
    val theme: AppTheme? = null,
    val transparent: Boolean = false,

    /** Another style to draw the map in; empty draws it in the app's colors. */
    val style: String? = null,

    val viewMode: String? = null,
    val focus: String? = null,
    val baseUrl: String? = null,
)

/**
 * Pictures of maps, drawn by Orbit: thumbnails for the home screen, and pictures to share.
 * `orbit` names the build of Orbit that draws them (its commit), so an update that draws
 * maps differently draws their thumbnails again.
 */
class MapPictures(private val pool: PagePool, cache: File, private val orbit: String) {
    private val thumbnails = File(cache, "thumbnails")
    private val drawing = Mutex()

    /** The thumbnail for a map as its text and the app's colors are now, if it was drawn before. */
    fun cachedThumbnail(id: String, contentKey: String, style: String?, theme: AppTheme): File? =
        thumbnailFile(id, contentKey, style, theme).takeIf { it.isFile }

    /** The thumbnail for a map, drawn now if needed. Null when the page couldn't draw it. */
    suspend fun thumbnail(id: String, contentKey: String, style: String?, theme: AppTheme, markdown: suspend () -> String): File? {
        val file = thumbnailFile(id, contentKey, style, theme)

        if (file.isFile) {
            return file
        }

        return drawing.withLock {
            if (file.isFile) {
                return@withLock file
            }

            // A map that's gone, or whose file can't be read, has no thumbnail.
            val text = try {
                markdown()
            } catch (unreadable: java.io.IOException) {
                return@withLock null
            }

            val bytes = try {
                draw(
                    PictureRequest(
                        markdown = text,
                        format = "webp",
                        width = THUMBNAIL_WIDTH,
                        height = THUMBNAIL_HEIGHT,
                        scale = 2.0,
                        theme = theme,
                        transparent = true,
                        baseUrl = mapBaseUrl(id),
                    ),
                )
            } catch (failed: PageException) {
                return@withLock null
            }

            withContext(Dispatchers.IO) {
                thumbnails.mkdirs()
                thumbnails.listFiles { old -> old.name.startsWith("$id~") }?.forEach { it.delete() }
                // Whole or not at all: a picture cut short by the app stopping would be shown as it is.
                writeAtomically(file, bytes)
            }

            file
        }
    }

    /** Draws a map from its text on the spare page. */
    suspend fun draw(request: PictureRequest): ByteArray = pool.work { page -> draw(page, request) }

    /** Draws a picture on `page`: of the map on it, or of `request.markdown`. */
    suspend fun draw(page: OrbitPage, request: PictureRequest): ByteArray {
        val answer = withContext(Dispatchers.Main) {
            page.request(String.serializer()) { job ->
                MapCommand.Snapshot(
                    job = job,
                    format = request.format,
                    markdown = request.markdown,
                    width = request.width,
                    height = request.height,
                    scale = request.scale,
                    theme = request.theme,
                    transparent = request.transparent,
                    style = request.style,
                    viewMode = request.viewMode,
                    focus = request.focus,
                    baseUrl = request.baseUrl,
                )
            }
        }

        return withContext(Dispatchers.Default) {
            if (request.format == "svg") {
                answer.toByteArray()
            } else {
                Base64.decode(answer.substringAfter("base64,"), Base64.DEFAULT)
            }
        }
    }

    fun forget(id: String) {
        thumbnails.listFiles { old -> old.name.startsWith("$id~") }?.forEach { it.delete() }
    }

    private fun thumbnailFile(id: String, contentKey: String, style: String?, theme: AppTheme): File {
        // A map in its own style looks the same in any app colors.
        val looks = style ?: digest("${theme.dark}${theme.tokens.toSortedMap()}")
        return File(thumbnails, "$id~$contentKey~${digest("$orbit~$looks")}.webp")
    }
}

private fun digest(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
