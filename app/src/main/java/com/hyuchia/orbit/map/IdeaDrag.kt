package com.hyuchia.orbit.map

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.PersistableBundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.MapNode
import java.util.UUID
import kotlin.math.roundToInt

/**
 * How deep an outline nests ([ideaOutline]): an idea deeper than this is
 * listed at this depth, under the last idea there. So a branch a hundred
 * thousand ideas deep is an outline about the size of its ideas' names,
 * not of its depth squared. The same as `OUTLINE_DEEPEST` in web/bridge.ts.
 */
const val OUTLINE_DEEPEST = 32

/**
 * An idea and the ideas under it as plain text, for other apps: its name,
 * then a list, nested as they are, up to [OUTLINE_DEEPEST] levels. The same
 * outline copying an idea gives (`outline` in web/bridge.ts).
 */
fun ideaOutline(nodes: List<MapNode>, id: String): String {
    val children = nodes.groupBy { it.parentId }
    val node = nodes.firstOrNull { it.id == id } ?: return ""
    val lines = mutableListOf(outlineName(node))
    // A map can go a hundred thousand ideas deep: a list kept by hand, rather than a call for each level.
    val waiting = ArrayDeque(children[node.id].orEmpty().sortedBy { it.index }.map { it to 0 })
    val indents = Array(OUTLINE_DEEPEST + 1) { "  ".repeat(it) }

    while (waiting.isNotEmpty()) {
        val (child, depth) = waiting.removeFirst()
        lines += "${indents[minOf(depth, OUTLINE_DEEPEST)]}- ${outlineName(child)}"
        children[child.id].orEmpty().sortedByDescending { it.index }.forEach { waiting.addFirst(it to depth + 1) }
    }

    return lines.joinToString("\n")
}

private fun outlineName(node: MapNode): String = listOfNotNull(node.icon, node.text.takeIf { it.isNotEmpty() }).joinToString(" ")

/**
 * A press held still lifts what it's on, as an idea lifts on the map: let
 * go without moving, it's [onLetGo]; moved on, it's [onDragOut], which starts
 * a drag other apps can take and takes the press with it. A press that moves
 * before it's held, as a list scrolls, is left to the list, and so is one
 * that isn't [ours], such as one on a button inside, whose hold shows its
 * hint.
 */
suspend fun PointerInputScope.liftThenDrag(onLift: (Boolean) -> Unit, onLetGo: () -> Unit, onDragOut: () -> Boolean, ours: () -> Boolean = { true }) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        if (!ours()) {
            return@awaitEachGesture
        }

        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        onLift(true)
        held.consume()

        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == held.id } ?: break

                if (!change.pressed) {
                    change.consume()
                    onLetGo()
                    break
                }

                change.consume()

                if ((change.position - held.position).getDistance() > viewConfiguration.touchSlop) {
                    onDragOut()
                    break
                }
            }
        } finally {
            onLift(false)
        }
    }
}

/**
 * Starts dragging idea `id` out to other apps, from `view` while a finger,
 * pen or mouse button is down on it: they get its outline as text, and a
 * drop back on its own map moves it there ([OutgoingIdea]). The shadow under
 * the finger is the idea as a planet, in its color. Returns whether the drag
 * started.
 */
fun startIdeaDrag(view: View, mapId: String, nodes: List<MapNode>, id: String, untitled: String, color: Int): Boolean {
    val node = nodes.firstOrNull { it.id == id } ?: return false
    val label = node.label(untitled)

    // Android hands a drag's local state only to the window it started in, and the outline's sheet is a window of its own:
    // the idea's map and id go with its text too, for the map to tell its own idea back. The description says only that it's one.
    val description = ClipDescription(label, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)).apply {
        extras = PersistableBundle().apply { putBoolean(EXTRA_ORBIT_IDEA, true) }
    }

    val ownIdea = Intent().putExtra(EXTRA_MAP, mapId).putExtra(EXTRA_IDEA, id).putExtra(EXTRA_MARK, dragMark)
    val clip = ClipData(description, ClipData.Item(ideaOutline(nodes, id), ownIdea, null))
    val shadow = IdeaShadow(view.context, label, color, ideasUnder(nodes, id))

    return view.startDragAndDrop(clip, shadow, OutgoingIdea(mapId, id), View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_OPAQUE)
}

/** Whether a drag's description says it's an idea dragged out of Orbit. */
fun isOrbitIdea(description: ClipDescription?): Boolean = description?.extras?.getBoolean(EXTRA_ORBIT_IDEA) == true

/**
 * The idea of Orbit's a drag let go carries, from its first item; null for
 * anything else, such as another app's drag that says it's one but doesn't
 * carry this run's mark.
 */
fun outgoingIdea(clip: ClipData?): OutgoingIdea? {
    val intent = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.intent ?: return null

    return try {
        val mapId = intent.getStringExtra(EXTRA_MAP)
        val id = intent.getStringExtra(EXTRA_IDEA)
        if (mapId != null && id != null && intent.getStringExtra(EXTRA_MARK) == dragMark) OutgoingIdea(mapId, id) else null
    } catch (unreadable: RuntimeException) {
        // Another app's drag can carry anything in its item: what Orbit can't read there, it leaves.
        null
    }
}

private const val EXTRA_ORBIT_IDEA = "com.hyuchia.orbit.idea"
private const val EXTRA_MAP = "com.hyuchia.orbit.drag.map"
private const val EXTRA_IDEA = "com.hyuchia.orbit.drag.idea"
private const val EXTRA_MARK = "com.hyuchia.orbit.drag.mark"

/** This run of Orbit's mark on the ideas it drags: a drag ends long before the run does. */
private val dragMark: String = UUID.randomUUID().toString()

/**
 * An idea under the finger as it's dragged out of Orbit: a planet in its
 * color, lit from the top left as Orbit lights them, its name inside in the
 * app's font, and "+N" for the ideas that go with it.
 */
class IdeaShadow(context: Context, private val label: String, private val planet: Int, private val ideas: Int) : View.DragShadowBuilder() {
    private val density = context.resources.displayMetrics.density
    private val diameter = PLANET_DIAMETER * density
    private val margin = SHADOW_MARGIN * density
    private val size = (diameter + margin * 2).roundToInt()
    private val font = runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull() ?: Typeface.DEFAULT

    /** Light text on dark planets and dark text on light ones, as Orbit picks its labels'. */
    private val ink = if (ColorUtils.calculateLuminance(planet) > LIGHT_PLANET) Color.argb(222, 20, 20, 24) else Color.WHITE

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(size, size)
        outShadowTouchPoint.set(size / 2, size / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val center = size / 2f
        val radius = diameter / 2f

        // In a Paint's own scope, `color` is the paint's: the planet's color is `planet`.
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = planet
            setShadowLayer(10 * density, 0f, 4 * density, Color.argb(90, 0, 0, 0))
        }

        canvas.drawCircle(center, center, radius, shade)

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center - radius * 0.38f,
                center - radius * 0.42f,
                radius * 1.45f,
                intArrayOf(ColorUtils.blendARGB(planet, Color.WHITE, 0.28f), planet, ColorUtils.blendARGB(planet, Color.BLACK, 0.22f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        canvas.drawCircle(center, center, radius, body)

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(font, LABEL_WEIGHT, false)
            textSize = LABEL_SIZE * density
            color = ink
        }

        // Each line centered, so a name that wraps stays round, like the planet.
        val width = (diameter * 0.74f).roundToInt()
        val layout = StaticLayout.Builder.obtain(label, 0, label.length, text, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 0.95f)
            .build()

        canvas.withTranslation(center - width / 2f, center - layout.height / 2f) {
            layout.draw(this)
        }

        if (ideas > 0) {
            drawCount(canvas, center, radius)
        }
    }

    /** "+N" on the planet's lower rim: the ideas that go with it. */
    private fun drawCount(canvas: Canvas, center: Float, radius: Float) {
        val count = "+$ideas"

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(font, LABEL_WEIGHT, false)
            textSize = COUNT_SIZE * density
            // The planet's color, deep enough to read on the white pill.
            color = ColorUtils.blendARGB(planet, Color.BLACK, 0.6f)
        }

        val height = COUNT_HEIGHT * density
        val width = text.measureText(count) + height * 0.9f
        val top = center + radius * 0.78f - height / 2f
        val pill = RectF(center - width / 2f, top, center + width / 2f, top + height)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(pill, height / 2f, height / 2f, fill)
        val baseline = pill.centerY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(count, pill.centerX() - text.measureText(count) / 2f, baseline, text)
    }

    private companion object {
        /** The planet's size under the finger (dp): a big planet, as one lifted on the map grows. */
        const val PLANET_DIAMETER = 96f

        /** Room around it for its shadow (dp). */
        const val SHADOW_MARGIN = 16f

        /** As big against the planet as Orbit's names are against theirs. */
        const val LABEL_SIZE = 16f

        const val LABEL_WEIGHT = 600
        const val COUNT_SIZE = 11f
        const val COUNT_HEIGHT = 20f

        /** Planets lighter than this take dark text. */
        const val LIGHT_PLANET = 0.45
    }
}
