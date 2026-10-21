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

/** The application context for the one caller without one of its own. */
internal object PatchouliAppContext {
	lateinit var app: Context
	fun filesDir(): File = app.filesDir
}

/** What we asked AniList about a folder and what it said; [manual] when the user answered instead. */
@Serializable
data class Lookup(
	val query: String,
	val anime: Anime? = null,
	val at: Long,
	val manual: Boolean = false,
	/** An image the user picked from the phone, copied into the app's files; wins over [anime]'s cover. */
	val customCover: String? = null,
)

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
		"images", "music", "documents", "bluetooth", "patchouli", "recorder", "wallpapers",
	)

	fun init(context: Context) {
		PatchouliAppContext.app = context.applicationContext
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
		synchronized(down) { down.clear() }
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

	/** The image to show for a folder: the one the user picked, else the catalogue's. */
	fun cover(folder: Folder): Any? = cache[folder.path]?.let { it.customCover?.let { p -> File(p) } ?: it.anime?.cover }

	fun ensure(folder: Folder) {
		if (!AppPrefs.coversOnline) return
		val current = cache[folder.path]
		if (current != null && (current.anime != null || current.manual || current.customCover != null || System.currentTimeMillis() - current.at < RETRY_AFTER_MS)) return
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
				// AniList first; it goes down now and then, and so does Jikan, so three catalogues in a row.
				// One that just failed is skipped for a while: Jikan's timeout alone is fifteen seconds.
				val hit = ask("anilist", query) { AniList.search(it) }
					?: ask("jikan", query) { Jikan.search(it) }
					?: ask("kitsu", query) { Kitsu.search(it) }
					?: throw lastError ?: IllegalStateException("no catalogue answered")
				store(folder.path, Lookup(query, hit.takeIf { it.id != 0 }, System.currentTimeMillis()))
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

	private val down = HashMap<String, Long>()
	private const val DOWN_FOR_MS = 5 * 60_000L
	@Volatile private var lastError: Exception? = null

	/** One catalogue's answer, or null when it is down (remembered five minutes) or it failed. */
	private suspend fun ask(name: String, query: String, search: suspend (String) -> List<Anime>): Anime? {
		synchronized(down) { down[name]?.let { if (System.currentTimeMillis() - it < DOWN_FOR_MS) return null else down.remove(name) } }
		return try {
			// an empty answer is an answer: the catalogue does not know the name
			search(query).firstOrNull() ?: Anime(id = 0, source = name, title = "")
		} catch (e: Exception) {
			Log.w(TAG, "$name failed for '$query': ${e.message}")
			lastError = e
			synchronized(down) { down[name] = System.currentTimeMillis() }
			null
		}
	}

	/** The user says this folder is not a series (or is one after all). */
	fun setNotSeries(folder: Folder, notSeries: Boolean) {
		val query = Names.series(folder.name)
		if (notSeries) store(folder.path, Lookup(query, null, System.currentTimeMillis(), manual = true))
		else { cache.remove(folder.path); fileFor(folder.path).delete(); ensure(folder) }
	}

	/** Every candidate the catalogues have for a query, from the first one that answers. */
	suspend fun candidates(query: String): List<Anime> {
		for ((name, search) in listOf<Pair<String, suspend (String) -> List<Anime>>>("anilist" to { AniList.search(it, 10) }, "jikan" to { Jikan.search(it, 10) }, "kitsu" to { Kitsu.search(it, 10) })) {
			val skip = synchronized(down) {
				val since = down[name]
				if (since != null && System.currentTimeMillis() - since < DOWN_FOR_MS) true else { down.remove(name); false }
			}
			if (skip) continue
			try {
				val found = search(query)
				if (found.isNotEmpty()) return found
			} catch (e: Exception) {
				Log.w(TAG, "$name failed for '$query': ${e.message}")
				synchronized(down) { down[name] = System.currentTimeMillis() }
			}
		}
		return emptyList()
	}

	/** The user picked one of the candidates. */
	fun choose(folder: Folder, anime: Anime) {
		val current = cache[folder.path]
		store(folder.path, Lookup(current?.query ?: Names.series(folder.name), anime, System.currentTimeMillis(), customCover = current?.customCover))
	}

	/** The user picked an image from the phone: copied into our files so the original can go. */
	fun setCustomCover(folder: Folder, context: Context, uri: android.net.Uri): Boolean {
		val target = File(File(context.filesDir, "covers").also { it.mkdirs() }, fileFor(folder.path).nameWithoutExtension + ".jpg")
		val ok = runCatching {
			context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: error("no stream")
		}.isSuccess
		if (!ok) return false
		val current = cache[folder.path]
		store(folder.path, Lookup(current?.query ?: Names.series(folder.name), current?.anime, System.currentTimeMillis(), manual = current?.manual == true, customCover = target.absolutePath))
		return true
	}

	/** An image found on the web, the first of [urls] that downloads and decodes (the original, then the thumbnail). */
	suspend fun setCustomCoverFromUrl(folder: Folder, urls: List<String>, referer: String?): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
		val target = File(File(PatchouliAppContext.filesDir(), "covers").also { it.mkdirs() }, fileFor(folder.path).nameWithoutExtension + ".jpg")
		val client = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
		val ok = urls.any { url ->
			runCatching {
				val request = okhttp3.Request.Builder().url(url)
					.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
					.apply { if (referer != null) header("Referer", referer) }
					.build()
				client.newCall(request).execute().use { r ->
					if (r.code != 200) error("HTTP ${r.code}")
					target.outputStream().use { r.body.byteStream().copyTo(it) }
				}
				android.graphics.BitmapFactory.decodeFile(target.absolutePath) ?: error("not an image")
			}.onFailure { Log.w(TAG, "cover from $url failed: ${it.message}") }.isSuccess
		}
		if (!ok) { target.delete(); return@withContext false }
		val current = cache[folder.path]
		store(folder.path, Lookup(current?.query ?: Names.series(folder.name), current?.anime, System.currentTimeMillis(), manual = current?.manual == true, customCover = target.absolutePath))
		true
	}

	fun clearCustomCover(folder: Folder) {
		val current = cache[folder.path] ?: return
		current.customCover?.let { File(it).delete() }
		store(folder.path, current.copy(customCover = null))
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
