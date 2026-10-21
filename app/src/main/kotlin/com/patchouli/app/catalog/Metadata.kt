package com.patchouli.app.catalog

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Names
import com.patchouli.app.ui.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** What we asked AniList about a folder and what it said; [manual] when the user answered instead. */
@Serializable
data class Lookup(val query: String, val anime: Anime? = null, val at: Long, val manual: Boolean = false)

/**
 * Covers and summaries for the folders, one AniList search per folder name, cached on disk so a
 * folder is asked about once. Folders with the names every phone has (Camera, Download…) are never asked.
 */
object Metadata {
	private const val TAG = "Metadata"
	private const val RETRY_AFTER_MS = 7L * 24 * 3600 * 1000

	private lateinit var dir: File
	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
	private val cache = mutableStateMapOf<String, Lookup>()
	private val inflight = HashSet<String>()
	private val failures = HashMap<String, Long>()
	/** The last error per folder, for the folder's menu to show why there is no cover. */
	val errors = mutableStateMapOf<String, String>()
	private const val FAILURE_BACKOFF_MS = 60_000L
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private val skipNames = setOf(
		"download", "downloads", "dcim", "camera", "whatsapp", "telegram", "screenshots", "screen recordings",
		"movies", "video", "videos", "pictures", "recordings", "android", "media", "sent", "private", "status",
		"images", "music", "documents", "bluetooth", "seija", "hatate", "patchouli", "recorder", "wallpapers",
	)

	fun init(context: Context) {
		dir = File(context.filesDir, "anilist").also { it.mkdirs() }
		dir.listFiles { f -> f.extension == "json" }?.forEach { file ->
			runCatching { json.decodeFromString<Stored>(file.readText()) }
				.onSuccess { cache[it.path] = it.lookup }
				.onFailure { file.delete() }
		}
	}

	operator fun get(folder: Folder): Lookup? = cache[folder.path]

	/** Where a folder's cover stands, for the cards to say so instead of showing a frame in silence. */
	enum class Status { Off, Found, None, Skipped, Pending, Failed }

	fun status(folder: Folder): Status {
		if (!AppPrefs.coversOnline) return Status.Off
		val lookup = cache[folder.path]
		return when {
			lookup?.anime != null -> Status.Found
			lookup?.manual == true -> Status.Skipped
			lookup != null -> Status.None
			errors.containsKey(folder.path) -> Status.Failed
			folder.name.lowercase() in skipNames || folder.total == 0 -> Status.Skipped
			else -> Status.Pending
		}
	}

	/** Forgets the failures and asks again for every folder given. */
	fun retry(folders: List<Folder>) {
		synchronized(inflight) { failures.clear() }
		errors.clear()
		folders.forEach { ensure(it) }
	}

	/** The entry for a folder, asking AniList the first time it is shown. */
	@Composable
	fun rememberAnime(folder: Folder): Anime? {
		val lookup = cache[folder.path]
		LaunchedEffect(folder.path, AppPrefs.coversOnline) { ensure(folder) }
		return lookup?.anime
	}

	fun ensure(folder: Folder) {
		if (!AppPrefs.coversOnline) return
		val current = cache[folder.path]
		if (current != null && (current.anime != null || current.manual || System.currentTimeMillis() - current.at < RETRY_AFTER_MS)) return
		if (folder.name.lowercase() in skipNames || folder.total == 0) return
		synchronized(inflight) {
			// a failed lookup (no network, a 403) waits a minute before trying again
			val failedAt = failures[folder.path]
			if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_BACKOFF_MS) return
			if (!inflight.add(folder.path)) return
		}
		val query = Names.series(folder.name)
		scope.launch {
			try {
				val hit = try {
					AniList.search(query).firstOrNull()
				} catch (e: Exception) {
					// AniList goes down now and then; MyAnimeList through Jikan knows the same shows
					Log.w(TAG, "AniList failed for '$query' (${e.message}), asking Jikan")
					Jikan.search(query).firstOrNull()
				}
				store(folder.path, Lookup(query, hit, System.currentTimeMillis()))
				synchronized(inflight) { errors.remove(folder.path) }
			} catch (e: Exception) {
				// the network is not there: leave it unanswered, the next visit asks again
				Log.w(TAG, "lookup of '$query' failed: ${e.message}")
				synchronized(inflight) { failures[folder.path] = System.currentTimeMillis() }
				errors[folder.path] = e.message ?: "?"
			} finally {
				synchronized(inflight) { inflight.remove(folder.path) }
			}
		}
	}

	/** The user says this folder is not a series (or is one after all). */
	fun setNotSeries(folder: Folder, notSeries: Boolean) {
		val query = Names.series(folder.name)
		if (notSeries) store(folder.path, Lookup(query, null, System.currentTimeMillis(), manual = true))
		else { cache.remove(folder.path); fileFor(folder.path).delete(); ensure(folder) }
	}

	@Serializable
	private data class Stored(val path: String, val lookup: Lookup)

	private fun store(path: String, lookup: Lookup) {
		cache[path] = lookup
		runCatching { fileFor(path).writeText(json.encodeToString(Stored(path, lookup))) }
	}

	private fun fileFor(path: String): File {
		val digest = MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
		return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
	}
}
