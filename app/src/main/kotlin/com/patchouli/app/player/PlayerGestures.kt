package com.patchouli.app.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** What the surface of the player answers to. Fractions are of the surface size. */
class GestureCallbacks(
	val onTap: () -> Unit,
	/** [xFraction] of the width where the second tap landed. */
	val onDoubleTap: (xFraction: Float) -> Unit,
	val onLongPressStart: () -> Unit,
	val onLongPressEnd: () -> Unit,
	/** Vertical drag on the left ([side] 0) or right (1) half; [delta] positive going up. */
	val onVerticalDrag: (side: Int, delta: Float) -> Unit,
	val onVerticalEnd: () -> Unit,
	/** Horizontal drag; [total] is the distance from where the drag began, as a fraction of the width. */
	val onSeekDrag: (total: Float) -> Unit,
	val onSeekEnd: () -> Unit,
	/** Two fingers: the ratio between this distance and the previous one. */
	val onZoom: (factor: Float) -> Unit,
)

private const val NONE = 0
private const val VERTICAL = 1
private const val SEEK = 2
private const val ZOOM = 3

/**
 * The gestures of a video player in one detector, so they do not fight each other: tap, double tap,
 * hold, vertical drag per half (brightness, volume), horizontal drag (seek) and pinch (zoom).
 */
suspend fun PointerInputScope.playerGestures(cb: GestureCallbacks) = awaitEachGesture {
	val down = awaitFirstDown(requireUnconsumed = false)
	val slop = viewConfiguration.touchSlop
	val side = if (down.position.x < size.width / 2f) 0 else 1

	// phase one: a release is a tap, movement past the slop is a drag, silence is a hold
	val first: PointerEvent? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
		var result: PointerEvent? = null
		while (result == null) {
			val event = awaitPointerEvent()
			val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
			if (event.changes.count { it.pressed } >= 2) result = event
			else if (!change.pressed) result = event
			else if ((change.position - down.position).getDistance() > slop) result = event
		}
		result
	}

	if (first == null) {
		cb.onLongPressStart()
		waitForRelease()
		cb.onLongPressEnd()
		return@awaitEachGesture
	}

	val pressedNow = first.changes.filter { it.pressed }
	if (pressedNow.isEmpty()) {
		// a tap; a second one soon after makes it double
		val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) }
		if (second == null) {
			cb.onTap()
		} else {
			waitForRelease()
			cb.onDoubleTap(second.position.x / size.width)
		}
		return@awaitEachGesture
	}

	var mode: Int
	var lastDistance = 0f
	var last = down.position
	if (pressedNow.size >= 2) {
		mode = ZOOM
		lastDistance = distance(pressedNow)
	} else {
		val d = pressedNow.first().position - down.position
		mode = if (abs(d.x) > abs(d.y)) SEEK else VERTICAL
		last = pressedNow.first().position
		if (mode == SEEK) cb.onSeekDrag(d.x / size.width) else cb.onVerticalDrag(side, -d.y / size.height)
	}

	while (true) {
		val event = awaitPointerEvent()
		val pressed = event.changes.filter { it.pressed }
		if (pressed.isEmpty()) break
		if (pressed.size >= 2) {
			val dist = distance(pressed)
			if (mode != ZOOM) {
				// the drag that was going on ends where the second finger lands
				if (mode == SEEK) cb.onSeekEnd() else cb.onVerticalEnd()
				mode = ZOOM
			} else if (lastDistance > 0f) {
				cb.onZoom(dist / lastDistance)
			}
			lastDistance = dist
			pressed.forEach { it.consume() }
			continue
		}
		if (mode == ZOOM) { lastDistance = 0f; continue } // one finger left after a pinch: ignore the rest
		val c = pressed.first()
		when (mode) {
			VERTICAL -> cb.onVerticalDrag(side, (last.y - c.position.y) / size.height)
			SEEK -> cb.onSeekDrag((c.position.x - down.position.x) / size.width)
		}
		last = c.position
		c.consume()
	}
	when (mode) {
		VERTICAL -> cb.onVerticalEnd()
		SEEK -> cb.onSeekEnd()
	}
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForRelease() {
	while (true) {
		val event = awaitPointerEvent()
		if (event.changes.none { it.pressed }) return
	}
}

private fun distance(changes: List<PointerInputChange>): Float {
	val a: Offset = changes[0].position
	val b: Offset = changes[1].position
	return (a - b).getDistance()
}
