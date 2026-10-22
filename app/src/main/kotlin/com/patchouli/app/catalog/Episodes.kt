package com.patchouli.app.catalog

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import com.patchouli.app.ui.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The names of the episodes of a series, by number, from MyAnimeList through Jikan (AniList does
 * not keep them). One fetch per series, cached on disk; a series still airing is asked again
 * after a week, since the cached list is then shorter than the count the catalogue gives.
 */
object Episodes {
	private const val TAG = "Episodes"
	private const val REFRESH_AFTER_MS = 7L * 24 * 3600 * 1000
	private const val FAILURE_BACKOFF_MS = 60_000L

	@Serializable
	private data class Stored(val malId: Int, val at: Long, val titles: Map<Int, String>)

	private lateinit var dir: File
	private val json = Json { ignoreUnknownKeys = true }
	private val cache = mutableStateMapOf<Int, Stored>()
	private val inflight = HashSet<Int>()
	private val failures = HashMap<Int, Long>()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	fun init(context: Context) {
		dir = File(context.filesDir, "episodes").also { it.mkdirs() }
		dir.listFiles { f -> f.extension == "json" }?.forEach { file ->
			runCatching { json.decodeFromString<Stored>(file.readText()) }
				.onSuccess { cache[it.malId] = it }
				.onFailure { file.delete() }
		}
	}

	/** The titles of a series by episode number, fetched the first time they are asked for; empty until then. */
	@Composable
	fun rememberTitles(anime: Anime): Map<Int, String> {
		val malId = anime.malId
		LaunchedEffect(anime.id, anime.source, malId, AppPrefs.coversOnline) { ensure(anime) }
		return malId?.let { cache[it]?.titles }.orEmpty()
	}

	fun ensure(anime: Anime) {
		if (!AppPrefs.coversOnline) return
		val malId = anime.malId
		if (malId == null) {
			// an AniList entry looked up before the MAL id was asked for: fetch the id, then the titles follow
			if (anime.source == "anilist") resolveMalId(anime)
			return
		}
		val have = cache[malId]
		val fresh = have != null && (System.currentTimeMillis() - have.at < REFRESH_AFTER_MS || (anime.episodes != null && have.titles.size >= anime.episodes))
		if (fresh) return
		synchronized(inflight) {
			val failedAt = failures[malId]
			if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_BACKOFF_MS) return
			if (!inflight.add(malId)) return
		}
		scope.launch {
			try {
				val titles = LinkedHashMap<Int, String>()
				var page = 1
				while (true) {
					val (chunk, more) = Jikan.episodes(malId, page)
					titles += chunk
					if (!more || page >= 20) break
					page++
				}
				val stored = Stored(malId, System.currentTimeMillis(), titles)
				cache[malId] = stored
				runCatching { File(dir, "$malId.json").writeText(json.encodeToString(stored)) }
			} catch (e: Exception) {
				Log.w(TAG, "episodes of $malId failed: ${e.message}")
				synchronized(inflight) { failures[malId] = System.currentTimeMillis() }
			} finally {
				synchronized(inflight) { inflight.remove(malId) }
			}
		}
	}

	private val resolving = HashSet<Int>()

	private fun resolveMalId(anime: Anime) {
		synchronized(resolving) {
			val failedAt = failures[-anime.id]
			if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_BACKOFF_MS) return
			if (!resolving.add(anime.id)) return
		}
		scope.launch {
			try {
				val malId = AniList.malId(anime.id)
				if (malId != null) Metadata.attachMalId(anime.id, malId)
				else synchronized(resolving) { failures[-anime.id] = System.currentTimeMillis() + REFRESH_AFTER_MS } // the catalogue has no MAL entry for it
			} catch (e: Exception) {
				Log.w(TAG, "mal id of ${anime.id} failed: ${e.message}")
				synchronized(resolving) { failures[-anime.id] = System.currentTimeMillis() }
			} finally {
				synchronized(resolving) { resolving.remove(anime.id) }
			}
		}
	}
}
