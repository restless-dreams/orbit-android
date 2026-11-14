package com.hyuchia.orbit.map

import android.content.Context
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.io.IOException

/** The phone's player, failing to get a take ready when the test says so, as it does for a file it can't read. */
@Implements(MediaPlayer::class)
class UnpreparedMediaPlayer : ShadowMediaPlayer() {
    @Implementation
    override fun prepare() {
        if (fails) {
            throw IOException("The take can't be read")
        }

        super.prepare()
    }

    companion object {
        @JvmStatic
        var fails = false
    }
}

/** A take the phone can't read leaves no player behind: the one made for it is let go. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [UnpreparedMediaPlayer::class])
class TakePlayerFailureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val take = File(context.cacheDir, "recordings/take-1.mp4")

    /** Each player made, as Robolectric sees it. */
    private val made = mutableListOf<ShadowMediaPlayer>()

    @Before
    fun recorded() {
        take.parentFile!!.mkdirs()
        take.writeBytes(ByteArray(4_096))
        UnpreparedMediaPlayer.fails = false
        ShadowMediaPlayer.setCreateListener { _, shadow -> made += shadow }
    }

    @After
    fun cleanUp() {
        ShadowMediaPlayer.setCreateListener(null)
        ShadowMediaPlayer.resetStaticState()
        take.parentFile!!.deleteRecursively()
    }

    @Test
    fun `a take that can't be got ready lets its player go`() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(take.path), ShadowMediaPlayer.MediaInfo(3_000, 0))
        UnpreparedMediaPlayer.fails = true
        val player = TakePlayer(context, TestScope())

        player.load(take)

        assertFalse(player.loaded)
        assertEquals(ShadowMediaPlayer.State.END, made.single().state)
    }

    @Test
    fun `a take the player can't open lets its player go`() {
        ShadowMediaPlayer.addException(DataSource.toDataSource(take.path), IOException("No such take"))
        val player = TakePlayer(context, TestScope())

        player.load(take)

        assertFalse(player.loaded)
        assertEquals(ShadowMediaPlayer.State.END, made.single().state)
    }

    @Test
    fun `a take loaded after one that couldn't be keeps only its own player`() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(take.path), ShadowMediaPlayer.MediaInfo(3_000, 0))
        UnpreparedMediaPlayer.fails = true
        val player = TakePlayer(context, TestScope())
        player.load(take)

        UnpreparedMediaPlayer.fails = false
        player.load(take)

        assertTrue(player.loaded)
        assertEquals(listOf(ShadowMediaPlayer.State.END, ShadowMediaPlayer.State.PREPARED), made.map { it.state })
        player.release()
    }
}
