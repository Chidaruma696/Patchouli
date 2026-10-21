package com.patchouli.app.library

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.edit
import com.patchouli.app.PatchouliApp

/** Where a video was left, and when. */
data class Mark(val positionMs: Long, val lengthMs: Long, val at: Long) {
	val fraction: Float get() = if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
	val finished: Boolean get() = fraction >= FINISHED
	val started: Boolean get() = positionMs > RESUME_MIN_MS && !finished

	companion object {
		const val FINISHED = 0.95f
		const val RESUME_MIN_MS = 5_000L
	}
}

/** Playback positions by file path, kept in SharedPreferences and observed by Compose. */
object Progress {
	private const val FILE = "progress"
	private val marks = mutableStateMapOf<String, Mark>()

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		p.all.forEach { (path, raw) ->
			val parts = (raw as? String)?.split('|') ?: return@forEach
			if (parts.size == 3) {
				val pos = parts[0].toLongOrNull() ?: return@forEach
				val len = parts[1].toLongOrNull() ?: return@forEach
				val at = parts[2].toLongOrNull() ?: return@forEach
				marks[path] = Mark(pos, len, at)
			}
		}
	}

	private fun prefs() = PatchouliApp.instance.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	operator fun get(path: String): Mark? = marks[path]

	fun update(path: String, positionMs: Long, lengthMs: Long) {
		if (lengthMs <= 0) return
		val mark = Mark(positionMs, lengthMs, System.currentTimeMillis())
		marks[path] = mark
		prefs().edit { putString(path, "${mark.positionMs}|${mark.lengthMs}|${mark.at}") }
	}

	fun forget(path: String) {
		marks.remove(path)
		prefs().edit { remove(path) }
	}

	/** The videos left half-way, newest first. */
	fun continueWatching(limit: Int = 12): List<Pair<String, Mark>> =
		marks.entries.filter { it.value.started }.sortedByDescending { it.value.at }.take(limit).map { it.key to it.value }
}
