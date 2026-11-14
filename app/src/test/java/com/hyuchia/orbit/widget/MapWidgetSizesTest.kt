package com.hyuchia.orbit.widget

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.data.LockedCover
import com.hyuchia.orbit.data.MapMetadata
import com.hyuchia.orbit.data.MapOutline
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.map.Rollup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.util.ReflectionHelpers

/** The map widget as the launcher draws it at each of its six sizes: what shows, and where + fits. */
@RunWith(AndroidJUnit4::class)
class MapWidgetSizesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val trip = MapSummary(
        "trip",
        MapOutline("Trip to Lisbon", branches = 3, ideas = 12, progress = Rollup(total = 4, done = 1.0, finished = 1)),
        modified = 0,
        MapMetadata(),
        searchText = "",
        contentKey = "",
    )

    /** Draws the widget for `map` at `size`, then checks what shows there. `hasText` finds text that holds it; `hasTextEqualTo`, text that is it. */
    private fun at(size: DpSize, map: MapSummary?, check: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(size)
            provideComposable { MapWidgetContent(map, picture = null) }
            check()
        }

    private val add = hasContentDescriptionEqualTo("Add an idea to Trip to Lisbon")
    private val done = hasText("1 of 4 done")

    @Test
    fun twoRowsShowTheNameAndPlusAboveThePictureAndTheProgressBelow() {
        for (size in listOf(TwoRows, TwoLongRows)) {
            at(size, trip) {
                onNode(hasText("Trip to Lisbon")).assertExists()
                onNode(add).assertExists()
                onNode(done).assertExists()
            }
        }
    }

    @Test
    fun aLongRowHasRoomForPlusAndAShortOneDoesnt() {
        at(LongRow, trip) {
            onNode(hasText("Trip to Lisbon")).assertExists()
            onNode(done).assertExists()
            onNode(add).assertExists()
        }

        at(ShortRow, trip) {
            onNode(hasText("Trip to Lisbon")).assertExists()
            onNode(done).assertExists()
            onNode(add).assertDoesNotExist()
        }
    }

    @Test
    fun aCellShowsThePictureAndTheNameWithPlusOnlyWhenItsRoomy() {
        at(RoomyCell, trip) {
            onNode(hasText("Trip to Lisbon")).assertExists()
            onNode(add).assertExists()
            onNode(done).assertDoesNotExist()
        }

        at(OneCell, trip) {
            onNode(hasText("Trip to Lisbon")).assertExists()
            onNode(add).assertDoesNotExist()
        }
    }

    @Test
    fun aPrivateMapShowsOnlyItsLockAtEverySize() {
        val private = trip.copy(metadata = MapMetadata(isPrivate = true))

        for (size in listOf(OneCell, RoomyCell, ShortRow, LongRow, TwoRows, TwoLongRows)) {
            at(size, private) {
                onNode(hasText("Trip to Lisbon")).assertDoesNotExist()
                onNode(add).assertDoesNotExist()
                onNode(done).assertDoesNotExist()
            }
        }

        // Where there's room for words, it's "Private map"; in one cell, the lock says it to screen readers.
        at(TwoRows, private) { onNode(hasTextEqualTo("Private map")).assertExists() }
        at(ShortRow, private) { onNode(hasTextEqualTo("Private map")).assertExists() }
        at(OneCell, private) { onNode(hasContentDescriptionEqualTo("Private map")).assertExists() }
    }

    @Test
    fun aCoverItsOwnerChoseTakesPrivateMapsPlace() {
        val covered = trip.copy(metadata = MapMetadata(isPrivate = true, cover = LockedCover(icon = "📓", name = "Journal")))

        at(TwoRows, covered) {
            onNode(hasTextEqualTo("Journal")).assertExists()
            onNode(hasTextEqualTo("Private map")).assertDoesNotExist()
        }
    }

    @Test
    fun withoutAMapItSaysWhereMapsWillShow() {
        at(RoomyCell, null) { onNode(hasText("Your maps will show here. Open Orbit to start one.")).assertExists() }
    }

    /** Draws widgets as Android `sdk` does, with the phone's text at `fontScale`. */
    private fun on(sdk: Int, fontScale: Float, draw: () -> Unit) {
        val configuration = Resources.getSystem().configuration
        val scale = configuration.fontScale
        val version = Build.VERSION.SDK_INT

        try {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
            configuration.fontScale = fontScale
            draw()
        } finally {
            configuration.fontScale = scale
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", version)
        }
    }

    @Test
    fun aRowWithLargeTextStillSaysItsProgressOnAndroid10And11() {
        // Before Android 12 the bar is a picture of it: with no room for the words, screen readers hear them from it.
        val bar = hasContentDescriptionEqualTo("1 of 4 done")

        for (sdk in listOf(Build.VERSION_CODES.Q, Build.VERSION_CODES.R)) {
            on(sdk, fontScale = 2f) {
                for (size in listOf(ShortRow, LongRow)) {
                    at(size, trip) {
                        onNode(hasTextEqualTo("1 of 4 done")).assertDoesNotExist()
                        onNode(bar).assertExists()
                    }
                }
            }

            // With the words beside it, they're heard once.
            on(sdk, fontScale = 1f) {
                at(ShortRow, trip) {
                    onNode(hasTextEqualTo("1 of 4 done")).assertExists()
                    onNode(bar).assertDoesNotExist()
                }
            }
        }
    }
}
