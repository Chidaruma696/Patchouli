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

	/** A file was renamed: its bookmark follows. */
	fun move(from: String, to: String) {
		val mark = marks.remove(from) ?: return
		marks[to] = mark
		prefs().edit { remove(from); putString(to, "${mark.positionMs}|${mark.lengthMs}|${mark.at}") }
	}

	/** A folder was renamed: every bookmark under it follows. */
	fun movePrefix(fromDir: String, toDir: String) {
		val prefix = fromDir.trimEnd('/') + "/"
		marks.keys.filter { it.startsWith(prefix) }.toList().forEach { old -> move(old, toDir.trimEnd('/') + "/" + old.removePrefix(prefix)) }
	}

	fun forget(path: String) {
		marks.remove(path)
		prefs().edit { remove(path) }
	}

	/** The user says this one is watched: the bookmark sits at the end, with the length the file or the old mark knows. */
	fun markWatched(video: Video) {
		val length = video.durationMs.takeIf { it > 0 } ?: marks[video.path]?.lengthMs ?: return
		update(video.path, length, length)
	}

	/** The user says this one is not watched after all: no bookmark at all, as if never opened. */
	fun markUnwatched(video: Video) = forget(video.path)

	/** Every video under a folder, watched or not, in one go. */
	fun markFolder(folder: Folder, watched: Boolean) {
		fun walk(f: Folder) {
			f.videos.forEach { if (watched) markWatched(it) else markUnwatched(it) }
			f.folders.forEach(::walk)
		}
		walk(folder)
	}

	/** Watched videos under a folder, counting its subfolders too. */
	fun watchedIn(folder: Folder): Int = folder.videos.count { marks[it.path]?.finished == true } + folder.folders.sumOf { watchedIn(it) }

	/** When something under a folder was last played, for "most recent first"; 0 when nothing was. */
	fun lastPlayedIn(folder: Folder): Long = maxOf(folder.videos.maxOfOrNull { marks[it.path]?.at ?: 0L } ?: 0L, folder.folders.maxOfOrNull { lastPlayedIn(it) } ?: 0L)

	/** Every bookmark as stored, for the backup. */
	fun export(): Map<String, String> = marks.entries.associate { (path, m) -> path to "${m.positionMs}|${m.lengthMs}|${m.at}" }

	/** Bookmarks from a backup: each one taken when it is newer than ours. Returns how many came in. */
	fun import(data: Map<String, String>): Int {
		var taken = 0
		data.forEach { (path, raw) ->
			val parts = raw.split('|')
			if (parts.size != 3) return@forEach
			val pos = parts[0].toLongOrNull() ?: return@forEach
			val len = parts[1].toLongOrNull() ?: return@forEach
			val at = parts[2].toLongOrNull() ?: return@forEach
			val have = marks[path]
			if (have == null || have.at < at) {
				marks[path] = Mark(pos, len, at)
				prefs().edit { putString(path, raw) }
				taken++
			}
		}
		return taken
	}

	/** The videos left half-way, newest first. */
	fun continueWatching(limit: Int = 12): List<Pair<String, Mark>> =
		marks.entries.filter { it.value.started }.sortedByDescending { it.value.at }.take(limit).map { it.key to it.value }
}
