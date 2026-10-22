/*
 * Drawings of the kit, the manga look of Komi Store (kurikomi-labs/komi-store, Apache 2.0) carried
 * over to Patchouli: the hard ink shadow behind every panel, the press that lands the panel on it,
 * tone sheets of dots, speed lines, grid paper, the skewed stamp and the focus ring.
 */
package com.patchouli.app.ui.kit

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** The shape of the style: square panels, cut with the knife. A corner of 0 is the rule. */
fun kitShape(corner: Dp): Shape = if (corner <= 0.dp) RectangleShape else RoundedCornerShape(corner)

/** The headline stamp: a parallelogram leaning forward, like a stamp pressed in a hurry. */
val SkewStampShape: Shape =
	GenericShape { size, _ ->
		val skew = size.height * 0.30f
		moveTo(skew, 0f)
		lineTo(size.width, 0f)
		lineTo(size.width - skew, size.height)
		lineTo(0f, size.height)
		close()
	}

// A repeating dot tile baked into a tiny ImageBitmap once, exposed as a tiling shader brush, so a
// tone sheet fills any area with a single drawRect instead of hundreds of circles per frame.
private fun dotTileBrush(color: Color, opacity: Float, spacing: Float, radius: Float): ShaderBrush {
	val side = spacing.roundToInt().coerceAtLeast(1)
	val tile = ImageBitmap(side, side)
	val canvas = Canvas(tile)
	val paint = Paint().apply {
		isAntiAlias = true
		this.color = color.copy(alpha = opacity)
	}
	canvas.drawCircle(Offset(side / 2f, side / 2f), radius, paint)
	return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/** A solid block of [color] the shape of the panel, shifted by [offset]: the shadow of a badly registered print. */
fun Modifier.hardShadow(offset: DpOffset, color: Color, shape: Shape = RectangleShape): Modifier =
	drawBehind {
		val dx = offset.x.toPx()
		val dy = offset.y.toPx()
		val outline = shape.createOutline(size, layoutDirection, this)
		translate(dx, dy) { drawOutline(outline = outline, color = color) }
	}

/** The same block with an offset read at draw time, for the press that collapses it. */
fun Modifier.hardShadow(color: Color, shape: Shape = RectangleShape, offset: DrawScope.() -> Offset): Modifier =
	drawWithCache {
		val outline = shape.createOutline(size, layoutDirection, this)
		onDrawBehind {
			val o = offset()
			translate(o.x, o.y) { drawOutline(outline = outline, color = color) }
		}
	}

/**
 * Stamp-down: while the finger is down the face moves to (shadow - [pressInset]) on each axis and
 * the hard shadow shrinks to nothing, so the face lands on its shadow. Buttons use an inset of 1,
 * panels 2. [pressProgress] is read in the layout and draw phases, never in composition.
 */
fun Modifier.inkPress(
	pressProgress: () -> Float,
	shadow: DpOffset,
	shadowColor: Color,
	shape: Shape,
	pressInset: Dp = 1.dp,
): Modifier =
	this
		.offset {
			val pp = pressProgress()
			val pressX = maxOf(shadow.x.toPx() - pressInset.toPx(), 1.dp.toPx())
			val pressY = maxOf(shadow.y.toPx() - pressInset.toPx(), 1.dp.toPx())
			IntOffset((pressX * pp).roundToInt(), (pressY * pp).roundToInt())
		}
		.hardShadow(color = shadowColor, shape = shape) {
			val pp = pressProgress()
			Offset(x = shadow.x.toPx() * (1f - pp), y = shadow.y.toPx() * (1f - pp))
		}

/** An even field of dots behind the content: the tone sheet of a panel. */
fun Modifier.screentoneFill(color: Color, opacity: Float, dotRadius: Float = 1f, spacing: Float = 6f): Modifier =
	drawWithCache {
		val brush = dotTileBrush(color, opacity, spacing, dotRadius)
		onDrawBehind { drawRect(brush = brush) }
	}

/**
 * Top-right corner wash: a dot lattice masked from the corner, solid there and gone by 70 % of the
 * region's diagonal. Baked into a bitmap once per size.
 */
fun Modifier.screentoneCorner(
	color: Color,
	opacity: Float,
	boost: Float = 1f,
	regionWidth: Dp = 120.dp,
	regionHeight: Dp = 90.dp,
	dotRadius: Float = 1.1f,
	spacing: Float = 5f,
): Modifier =
	drawWithCache {
		val wPx = regionWidth.toPx()
		val hPx = regionHeight.toPx()
		val maskRadius = hypot(wPx, hPx) * 0.7f
		val bw = wPx.coerceAtMost(size.width).roundToInt().coerceAtLeast(1)
		val bh = hPx.coerceAtMost(size.height).roundToInt().coerceAtLeast(1)
		val region = ImageBitmap(bw, bh)
		val canvas = Canvas(region)
		val paint = Paint().apply { isAntiAlias = true }
		var y = 0f
		while (y <= bh) {
			var x = 0f
			while (x <= bw) {
				val falloff = (1f - hypot(bw - x, y) / maskRadius).coerceIn(0f, 1f)
				if (falloff > 0f) {
					paint.color = color.copy(alpha = (opacity * boost * falloff).coerceAtMost(1f))
					canvas.drawCircle(Offset(x, y), dotRadius, paint)
				}
				x += spacing
			}
			y += spacing
		}
		onDrawBehind {
			val left = (size.width - bw).coerceAtLeast(0f)
			drawImage(image = region, topLeft = Offset(left, 0f))
		}
	}

/** Dots over the content with multiply, so they sit in the picture like a printed tone. */
fun Modifier.halftoneOverlay(color: Color, opacity: Float, spacing: Dp = 4.dp, dotRadius: Dp = 1.1.dp): Modifier =
	drawWithCache {
		val brush = dotTileBrush(color, opacity, spacing.toPx(), dotRadius.toPx())
		onDrawWithContent {
			drawContent()
			drawRect(brush = brush, blendMode = BlendMode.Multiply)
		}
	}

/** Speed lines from the middle out, for an emphasized button. */
fun Modifier.speedLines(color: Color, spokes: Int = 120, opacity: Float = 0.12f, strokeWidth: Float = 1.2f): Modifier =
	drawWithCache {
		val cx = size.width / 2f
		val cy = size.height / 2f
		val reach = hypot(size.width, size.height)
		onDrawBehind {
			for (i in 0 until spokes) {
				val angle = (2.0 * PI * i / spokes).toFloat()
				drawLine(color = color, start = Offset(cx, cy), end = Offset(cx + cos(angle) * reach, cy + sin(angle) * reach), strokeWidth = strokeWidth, alpha = opacity)
			}
		}
	}

/** A burst of speed lines fading out of the top-right corner: the wash behind the masthead. */
fun Modifier.speedLineWash(color: Color, alpha: Float = 0.16f, spokes: Int = 56): Modifier =
	drawBehind {
		val center = Offset(size.width - 46.dp.toPx(), 4.dp.toPx())
		val reach = 130.dp.toPx()
		for (i in 0 until spokes) {
			val angle = (2.0 * PI * i / spokes).toFloat()
			val end = Offset(center.x + cos(angle) * reach, center.y + sin(angle) * reach)
			drawLine(
				brush = Brush.linearGradient(colors = listOf(color.copy(alpha = alpha), Color.Transparent), start = center, end = end),
				start = center,
				end = end,
				strokeWidth = 1.1f,
			)
		}
	}

/** Grid paper: faint 1 px lines on a square cell, the paper the panels sit on. */
fun Modifier.gridPaper(color: Color, opacity: Float, cell: Dp = 26.dp): Modifier =
	drawBehind {
		val step = cell.toPx()
		val stroke = 1.dp.toPx()
		var x = 0f
		while (x <= size.width) {
			drawLine(color = color, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = stroke, alpha = opacity)
			x += step
		}
		var y = 0f
		while (y <= size.height) {
			drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = stroke, alpha = opacity)
			y += step
		}
	}

/** The page behind everything. */
fun Modifier.motifBackground(motif: Motif, color: Color, opacity: Float): Modifier =
	when (motif) {
		Motif.None -> this
		Motif.Grid -> gridPaper(color, opacity)
	}

/** Focus ring for keyboards and remotes, square like everything else. */
fun Modifier.inkFocusRing(focused: () -> Boolean, color: Color, corner: Dp = 0.dp, width: Dp = 2.dp, gap: Dp = 3.dp): Modifier =
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
