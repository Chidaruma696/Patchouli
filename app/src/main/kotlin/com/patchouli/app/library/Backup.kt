package com.patchouli.app.library

import android.content.Context
import android.net.Uri
import com.patchouli.app.catalog.Lookup
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.player.TrackPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the app remembers that is not on the disk already: where each video was left,
 * which tracks each series plays with, which catalogue entry each folder was matched to.
 * One JSON file, written where the user says and read back on another phone.
 */
object Backup {
	@Serializable
	data class File(
		val app: String = "Patchouli",
		val version: Int = 1,
		val at: Long,
		val progress: Map<String, String>,
		val tracks: Map<String, String>,
		val lookups: Map<String, Lookup>,
	)

	/** What came back: how many bookmarks and how many folder matches were taken in. */
	data class Restored(val bookmarks: Int, val lookups: Int)

	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

	suspend fun write(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
		val file = File(at = System.currentTimeMillis(), progress = Progress.export(), tracks = TrackPrefs.export(), lookups = Metadata.export())
		context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToString(file).toByteArray()) } ?: error("cannot open")
	}

	suspend fun read(context: Context, uri: Uri): Restored = withContext(Dispatchers.IO) {
		val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("cannot open")
		val file = json.decodeFromString<File>(text)
		if (file.app != "Patchouli") error("not a Patchouli backup")
		val bookmarks = Progress.import(file.progress)
		TrackPrefs.import(file.tracks)
		val lookups = Metadata.import(file.lookups)
		Restored(bookmarks, lookups)
	}
}
