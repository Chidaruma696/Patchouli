package com.patchouli.app.catalog

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** What the library keeps of an AniList entry. */
@Serializable
data class Anime(
	val id: Int,
	/** Where [id] lives: "anilist" or "mal". */
	val source: String = "anilist",
	val title: String,
	val titleEnglish: String? = null,
	val cover: String? = null,
	val banner: String? = null,
	val synopsis: String? = null,
	val episodes: Int? = null,
	val year: Int? = null,
	val format: String? = null,
	val score: Int? = null,
	/** The same series on MyAnimeList, when the catalogue knows it; the episode names come from there. */
	val idMal: Int? = null,
) {
	/** The MyAnimeList id, whichever catalogue the entry came from. */
	val malId: Int? get() = if (source == "mal") id else idMal
}

@Serializable private data class AlTitle(val romaji: String? = null, val english: String? = null, val native: String? = null)
@Serializable private data class AlCover(val large: String? = null, val extraLarge: String? = null)
@Serializable private data class AlMedia(
	val id: Int,
	val idMal: Int? = null,
	val title: AlTitle? = null,
	val coverImage: AlCover? = null,
	val bannerImage: String? = null,
	val description: String? = null,
	val episodes: Int? = null,
	val seasonYear: Int? = null,
	val format: String? = null,
	val averageScore: Int? = null,
)
@Serializable private data class AlPage(val media: List<AlMedia> = emptyList())
@Serializable private data class AlData(val Page: AlPage? = null, val Media: AlMedia? = null)
@Serializable private data class AlError(val message: String? = null)
@Serializable private data class AlResponse(val data: AlData? = null, val errors: List<AlError> = emptyList())

/**
 * AniList (graphql.anilist.co): no key for public reads. One search per folder, spaced out, and a
 * 429 waits what the server says.
 */
object AniList {

	private const val URL = "https://graphql.anilist.co"
	private const val TAG = "AniList"
	private const val SPACING_MS = 700L
	private const val RETRIES = 2
	private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Patchouli/${com.patchouli.app.BuildConfig.VERSION_NAME}"

	private const val SEARCH = """
		query(${'$'}s: String, ${'$'}n: Int) {
			Page(perPage: ${'$'}n) {
				media(search: ${'$'}s, type: ANIME, isAdult: false, sort: SEARCH_MATCH) {
					id idMal title { romaji english native } coverImage { large extraLarge } bannerImage
					description(asHtml: false) episodes seasonYear format averageScore
				}
			}
		}
	"""

	private const val MAL_ID = """
		query(${'$'}id: Int) { Media(id: ${'$'}id, type: ANIME) { id idMal } }
	"""

	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
	private val client: OkHttpClient by lazy {
		OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
	}
	private val gate = Mutex()
	private var lastCall = 0L

	suspend fun search(query: String, limit: Int = 5): List<Anime> {
		val vars = buildJsonObject { put("s", query.trim()); put("n", limit) }
		return post(SEARCH, vars).data?.Page?.media.orEmpty().map { it.toAnime() }
	}

	/** The MyAnimeList id of an AniList entry, for the ones looked up before the search asked for it. */
	suspend fun malId(id: Int): Int? {
		val vars = buildJsonObject { put("id", id) }
		return post(MAL_ID, vars).data?.Media?.idMal
	}

	private fun AlMedia.toAnime() = Anime(
		id = id,
		idMal = idMal,
		title = title?.romaji ?: title?.english ?: title?.native ?: "?",
		titleEnglish = title?.english,
		cover = coverImage?.extraLarge ?: coverImage?.large,
		banner = bannerImage,
		synopsis = description?.replace(Regex("<br\\s*/?>"), "\n")?.replace(Regex("<[^>]+>"), "")?.trim(),
		episodes = episodes,
		year = seasonYear,
		format = when (format) { "TV", "TV_SHORT" -> "TV"; "MOVIE" -> "Movie"; "SPECIAL" -> "Special"; "OVA" -> "OVA"; "ONA" -> "ONA"; else -> format },
		score = averageScore,
	)

	private suspend fun post(query: String, variables: JsonObject): AlResponse = withContext(Dispatchers.IO) {
		val payload = buildJsonObject { put("query", query); put("variables", variables) }.toString()
		repeat(RETRIES + 1) { attempt ->
			gate.withLock {
				val wait = SPACING_MS - (System.currentTimeMillis() - lastCall)
				if (wait > 0) delay(wait)
				lastCall = System.currentTimeMillis()
			}
			val request = Request.Builder().url(URL)
				.header("Accept", "application/json")
				// OkHttp's own user agent gets a 403 from AniList's edge; a browser-shaped one does not
				.header("User-Agent", USER_AGENT)
				.post(payload.toRequestBody("application/json".toMediaType()))
				.build()
			val (code, retryAfter, body) = client.newCall(request).execute().use { r -> Triple(r.code, r.header("Retry-After")?.toLongOrNull(), r.body.string()) }
			if (code == 200) {
				val parsed = json.decodeFromString<AlResponse>(body)
				parsed.errors.firstOrNull()?.message?.let { error("AniList: $it") }
				return@withContext parsed
			}
			if (code != 429 && code < 500) error("AniList: HTTP $code")
			Log.w(TAG, "HTTP $code, retry ${attempt + 1}")
			delay(((retryAfter ?: 2L) * 1000L).coerceAtMost(15_000L))
		}
		error("AniList is not answering right now")
	}
}
