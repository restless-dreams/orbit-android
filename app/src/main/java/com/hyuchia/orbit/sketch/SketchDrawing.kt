package com.hyuchia.orbit.sketch

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

/** The outline's steps as a path to fill, `scale` pixels to a sketch unit. */
fun StrokeShape.toPath(scale: Float): Path = Path().also { path ->
    path.fillType = PathFillType.NonZero

    for (step in steps) {
        when (step) {
            is PathStep.Move -> path.moveTo(step.x * scale, step.y * scale)
            is PathStep.Line -> path.lineTo(step.x * scale, step.y * scale)
            is PathStep.Quad -> path.quadraticTo(step.controlX * scale, step.controlY * scale, step.x * scale, step.y * scale)
            is PathStep.Cubic -> path.cubicTo(step.firstX * scale, step.firstY * scale, step.secondX * scale, step.secondY * scale, step.x * scale, step.y * scale)
            PathStep.Close -> path.close()
        }
    }
}

/**
 * Contours as the smooth path [StrokeOutline.pathData] keeps them, `scale`
 * pixels to a unit, into `path`: the stroke being drawn looks as it will
 * once it's kept.
 */
fun contoursPath(contours: List<List<SketchPoint>>, scale: Float, path: Path = Path()): Path {
    path.rewind()
    path.fillType = PathFillType.NonZero

    for (contour in contours) {
        val count = contour.size

        if (count < 3) {
            continue
        }

        val last = contour[count - 1]
        val first = contour[0]
        path.moveTo((last.x + first.x) / 2 * scale, (last.y + first.y) / 2 * scale)

        for (index in 0 until count) {
            val corner = contour[index]
            val next = contour[(index + 1) % count]
            path.quadraticTo(corner.x * scale, corner.y * scale, (corner.x + next.x) / 2 * scale, (corner.y + next.y) / 2 * scale)
        }

        path.close()
    }

    return path
}
