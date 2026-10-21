package com.patchouli.app.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable private data class KtTitles(val en: String? = null, val en_jp: String? = null, val ja_jp: String? = null)
@Serializable private data class KtPoster(val large: String? = null, val original: String? = null, val medium: String? = null)
@Serializable private data class KtAttributes(
	val canonicalTitle: String? = null,
	val titles: KtTitles? = null,
	val synopsis: String? = null,
	val episodeCount: Int? = null,
	val startDate: String? = null,
	val subtype: String? = null,
	val averageRating: String? = null,
	val posterImage: KtPoster? = null,
)
@Serializable private data class KtEntry(val id: String, val attributes: KtAttributes? = null)
@Serializable private data class KtResponse(val data: List<KtEntry> = emptyList())

/** Kitsu (kitsu.io), the third catalogue: public JSON:API, no key, for the days the other two are down. */
object Kitsu {
	private const val URL = "https://kitsu.io/api/edge/anime"

	private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
	private val client: OkHttpClient by lazy {
		OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
	}

	suspend fun search(query: String, limit: Int = 5): List<Anime> = withContext(Dispatchers.IO) {
		val url = URL.toHttpUrl().newBuilder()
			.addQueryParameter("filter[text]", query.trim())
			.addQueryParameter("page[limit]", limit.toString())
			.build()
		val request = Request.Builder().url(url)
			.header("Accept", "application/vnd.api+json")
			.header("User-Agent", "Patchouli/${com.patchouli.app.BuildConfig.VERSION_NAME}")
			.build()
		val (code, body) = client.newCall(request).execute().use { it.code to it.body.string() }
		if (code != 200) error("Kitsu: HTTP $code")
		json.decodeFromString<KtResponse>(body).data.mapNotNull { e ->
			val a = e.attributes ?: return@mapNotNull null
			Anime(
				id = e.id.toIntOrNull() ?: return@mapNotNull null,
				source = "kitsu",
				title = a.titles?.en_jp ?: a.canonicalTitle ?: a.titles?.en ?: "?",
				titleEnglish = a.titles?.en,
				cover = a.posterImage?.large ?: a.posterImage?.original ?: a.posterImage?.medium,
				synopsis = a.synopsis?.trim(),
				episodes = a.episodeCount,
				year = a.startDate?.take(4)?.toIntOrNull(),
				format = a.subtype,
				score = a.averageRating?.toDoubleOrNull()?.toInt(),
			)
		}
	}
}
