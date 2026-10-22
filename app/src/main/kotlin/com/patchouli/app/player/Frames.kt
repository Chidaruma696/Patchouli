package com.patchouli.app.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Frames of the file for the preview while seeking: one every five seconds, small, kept in
 * memory once pulled. The retriever is slow on big files and not thread-safe, so one at a time.
 */
class FramePreview(path: String) : AutoCloseable {
	private val retriever = MediaMetadataRetriever().apply { setDataSource(path) }
	private val cache = LruCache<Long, Bitmap>(48)
	private val lock = Mutex()
	@Volatile private var closed = false

	/** The nearest frame to [ms], or null when the file will not give one. */
	suspend fun at(ms: Long): Bitmap? {
		val slot = ms / STEP_MS
		cache.get(slot)?.let { return it }
		return withContext(Dispatchers.IO) {
			lock.withLock {
				if (closed) return@withLock null
				cache.get(slot) ?: runCatching {
					val us = slot * STEP_MS * 1000
					if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, WIDTH * 9 / 16)
					else retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, WIDTH, (WIDTH * it.height / it.width.coerceAtLeast(1)), true) }
				}.getOrNull()?.also { cache.put(slot, it) }
			}
		}
	}

	override fun close() {
		closed = true
		runCatching { retriever.release() }
	}

	companion object {
		const val STEP_MS = 5_000L
		private const val WIDTH = 256
	}
}
