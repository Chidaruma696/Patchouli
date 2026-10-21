package com.patchouli.app.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable private data class JkImage(val large_image_url: String? = null, val image_url: String? = null)
@Serializable private data class JkImages(val jpg: JkImage? = null)
@Serializable private data class JkAnime(
	val mal_id: Int,
	val title: String? = null,
	val title_english: String? = null,
	val images: JkImages? = null,
	val synopsis: String? = null,
	val episodes: Int? = null,
	val year: Int? = null,
	val type: String? = null,
	val score: Double? = null,
)
@Serializable private data class JkResponse(val data: List<JkAnime> = emptyList())

/**
 * Jikan (api.jikan.moe), the unofficial MyAnimeList API: the fallback when AniList is down,
 * which happens. No key; three requests a second, sixty a minute, so calls are spaced.
 */
object Jikan {
	private const val URL = "https://api.jikan.moe/v4/anime"
	private const val SPACING_MS = 1_100L

	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
	private val client: OkHttpClient by lazy {
		OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
	}
	private val gate = Mutex()
	private var lastCall = 0L

	suspend fun search(query: String, limit: Int = 5): List<Anime> = withContext(Dispatchers.IO) {
		gate.withLock {
			val wait = SPACING_MS - (System.currentTimeMillis() - lastCall)
			if (wait > 0) delay(wait)
			lastCall = System.currentTimeMillis()
		}
		val url = URL.toHttpUrl().newBuilder()
			.addQueryParameter("q", query.trim())
			.addQueryParameter("limit", limit.toString())
			.addQueryParameter("sfw", "true")
			.build()
		val request = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Patchouli/${com.patchouli.app.BuildConfig.VERSION_NAME}").build()
		val (code, body) = client.newCall(request).execute().use { it.code to it.body.string() }
		if (code != 200) error("Jikan: HTTP $code")
		json.decodeFromString<JkResponse>(body).data.map { a ->
			Anime(
				id = a.mal_id,
				source = "mal",
				title = a.title ?: a.title_english ?: "?",
				titleEnglish = a.title_english,
				cover = a.images?.jpg?.large_image_url ?: a.images?.jpg?.image_url,
				synopsis = a.synopsis?.removeSuffix("[Written by MAL Rewrite]")?.trim(),
				episodes = a.episodes,
				year = a.year,
				format = a.type,
				score = a.score?.let { (it * 10).toInt() },
			)
		}
	}
}
