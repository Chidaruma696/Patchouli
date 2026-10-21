/*
 * Drawings of the kit: the check motif, rounded focus rings and a soft press.
 */
package com.patchouli.app.ui.kit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** The shape of the style, rounded at the given corner. */
fun kitShape(corner: Dp): Shape = RoundedCornerShape(corner)

/** Soft shadow tinted with the ink, instead of the old offset block. */
fun Modifier.softShadow(elevation: Dp, shape: Shape, color: Color): Modifier =
	if (elevation <= 0.dp) this
	else shadow(elevation = elevation, shape = shape, clip = false, ambientColor = color.copy(alpha = 0.18f), spotColor = color.copy(alpha = 0.22f))

/** A press that sinks a little instead of moving an ink stamp. */
fun Modifier.softPress(pressProgress: () -> Float): Modifier =
	scale(1f - 0.03f * pressProgress())

/** The check: alternating squares over the whole area, faint. */
fun Modifier.screentoneFill(color: Color, opacity: Float, cell: Dp = 10.dp): Modifier =
	drawBehind { drawChecker(color.copy(alpha = opacity), cell.toPx(), Offset.Zero, size, fade = null) }

/** The check in the top-right corner, fading out towards the middle. */
fun Modifier.screentoneCorner(
	color: Color,
	opacity: Float,
	boost: Float = 1f,
	regionWidth: Dp = 120.dp,
	regionHeight: Dp = 90.dp,
	cell: Dp = 9.dp,
): Modifier =
	drawBehind {
		val w = regionWidth.toPx().coerceAtMost(size.width)
		val h = regionHeight.toPx().coerceAtMost(size.height)
		drawChecker(color.copy(alpha = (opacity * boost).coerceAtMost(1f)), cell.toPx(), Offset(size.width - w, 0f), Size(w, h), fade = Offset(size.width, 0f))
	}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(color: Color, cell: Float, origin: Offset, area: Size, fade: Offset?) {
	val reach = hypot(area.width, area.height) * 0.85f
	var row = 0
	var y = origin.y
	while (y < origin.y + area.height) {
		var col = 0
		var x = origin.x
		while (x < origin.x + area.width) {
			if ((row + col) % 2 == 0) {
				val alpha = if (fade == null) 1f else (1f - hypot(fade.x - x, fade.y - y) / reach).coerceIn(0f, 1f)
				if (alpha > 0f) drawRect(color = color.copy(alpha = color.alpha * alpha), topLeft = Offset(x, y), size = Size(cell, cell))
			}
			x += cell
			col++
		}
		y += cell
		row++
	}
}

/** The page behind everything: the check, very faint, or nothing. */
fun Modifier.motifBackground(motif: Motif, color: Color, opacity: Float): Modifier =
	when (motif) {
		Motif.None -> this
		Motif.Checker -> screentoneFill(color, opacity, cell = 22.dp)
	}

/** Focus ring that follows the rounded shape. */
fun Modifier.inkFocusRing(focused: () -> Boolean, color: Color, corner: Dp = 12.dp, width: Dp = 2.dp, gap: Dp = 3.dp): Modifier =
	drawWithCache {
		val strokePx = width.toPx()
		val inset = -(gap.toPx() + strokePx / 2f)
		val radius = corner.toPx() + gap.toPx()
		onDrawWithContent {
			drawContent()
			if (focused()) {
				drawRoundRect(
					color = color,
					topLeft = Offset(inset, inset),
					size = Size(size.width - 2f * inset, size.height - 2f * inset),
					cornerRadius = CornerRadius(radius, radius),
					style = Stroke(width = strokePx),
				)
			}
		}
	}

/** A checker square of 2 × 2, the headline marker of the style. */
fun Modifier.checkerMark(first: Color, second: Color): Modifier =
	drawWithContent {
		val half = Size(size.width / 2f, size.height / 2f)
		drawRect(first, Offset.Zero, half)
		drawRect(second, Offset(half.width, 0f), half)
		drawRect(second, Offset(0f, half.height), half)
		drawRect(first, Offset(half.width, half.height), half)
	}