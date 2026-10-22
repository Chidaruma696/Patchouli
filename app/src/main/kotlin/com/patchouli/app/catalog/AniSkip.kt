package com.patchouli.app.catalog

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** A stretch of an episode worth skipping: the opening, the ending, a recap. */
@Serializable
data class SkipInterval(val startMs: Long, val endMs: Long, val type: String) {
	val isOpening: Boolean get() = type.endsWith("op")
	val isEnding: Boolean get() = type.endsWith("ed")
}

/**
 * AniSkip (api.aniskip.com): where the opening and the ending of an episode are, by MyAnimeList
 * id and episode number, as other viewers marked them. No key. The answer is cached on disk;
 * an episode nobody marked yet is asked again after a day.
 */
object AniSkip {
	private const val TAG = "AniSkip"
	private const val URL = "https://api.aniskip.com/v2/skip-times"
	private const val KEEP_FOUND_MS = 30L * 24 * 3600 * 1000
	private const val KEEP_EMPTY_MS = 24L * 3600 * 1000

	@Serializable private data class Interval(val startTime: Double, val endTime: Double)
	@Serializable private data class Result(val interval: Interval, val skipType: String, val episodeLength: Double = 0.0)
	@Serializable private data class Response(val found: Boolean = false, val results: List<Result> = emptyList())
	@Serializable private data class Stored(val at: Long, val intervals: List<SkipInterval>)

	private lateinit var dir: File
	private val json = Json { ignoreUnknownKeys = true; isLenient = true }
	private val cache = HashMap<String, Stored>()
	private val client: OkHttpClient by lazy {
		OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
	}

	fun init(context: Context) {
		dir = File(context.filesDir, "aniskip").also { it.mkdirs() }
	}

	/** The intervals of one episode, from the cache or the service; empty when nobody marked it (or the network is away). */
	suspend fun intervals(malId: Int, episode: Int, lengthMs: Long): List<SkipInterval> = withContext(Dispatchers.IO) {
		val key = "$malId-$episode"
		val file = File(dir, "$key.json")
		val have = synchronized(cache) { cache[key] } ?: runCatching { json.decodeFromString<Stored>(file.readText()) }.getOrNull()?.also { synchronized(cache) { cache[key] = it } }
		if (have != null) {
			val keep = if (have.intervals.isEmpty()) KEEP_EMPTY_MS else KEEP_FOUND_MS
			if (System.currentTimeMillis() - have.at < keep) return@withContext have.intervals
		}
		// the service keeps the marks of every cut of an episode and answers for the one whose length matches ours
		val length = if (lengthMs > 0) lengthMs / 1000.0 else 0.0
		var results = fetch(malId, episode, length)
		if (results.isEmpty() && length > 0) {
			// no cut of that exact length: of every cut it has, the one closest to ours, if it is close enough to trust
			val all = fetch(malId, episode, 0.0)
			val nearest = all.minByOrNull { abs(it.episodeLength - length) }
			if (nearest != null && abs(nearest.episodeLength - length) <= length * LENGTH_TOLERANCE) {
				results = all.filter { abs(it.episodeLength - nearest.episodeLength) < 1.0 }
			}
		}
		val intervals = results.map { SkipInterval((it.interval.startTime * 1000).toLong(), (it.interval.endTime * 1000).toLong(), it.skipType) }.sortedBy { it.startMs }
		Log.i(TAG, "$key: ${intervals.size} intervals")
		val stored = Stored(System.currentTimeMillis(), intervals)
		synchronized(cache) { cache[key] = stored }
		runCatching { file.writeText(json.encodeToString(stored)) }
		intervals
	}

	/** How far from ours the length of a cut may be and still be taken for the same one (3 %: a preview or a sponsor card). */
	private const val LENGTH_TOLERANCE = 0.03

	private fun fetch(malId: Int, episode: Int, length: Double): List<Result> {
		val url = "$URL/$malId/$episode?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed&types[]=recap&episodeLength=$length"
		val request = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Patchouli/${com.patchouli.app.BuildConfig.VERSION_NAME}").build()
		val (code, body) = client.newCall(request).execute().use { it.code to it.body.string() }
		return when (code) {
			200 -> json.decodeFromString<Response>(body).results
			404 -> emptyList()
			else -> error("AniSkip: HTTP $code")
		}
	}
}
