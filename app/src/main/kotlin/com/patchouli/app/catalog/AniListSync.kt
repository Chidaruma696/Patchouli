package com.patchouli.app.catalog

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.patchouli.app.library.Library
import com.patchouli.app.library.Video
import com.patchouli.app.ui.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The user's own AniList list: an episode watched to the end is written there as progress.
 * Login is AniList's implicit grant (a token good for a year, no secret in the app); what could
 * not be sent (no network) waits in a queue and goes the next time.
 */
object AniListSync {
	private const val TAG = "AniListSync"
	private const val FILE = "anilist"

	/**
	 * The API client registered at anilist.co/settings/developer for Patchouli, with [REDIRECT] as
	 * its redirect URL. Public by nature (the implicit grant has no secret).
	 */
	const val CLIENT_ID = ""
	const val REDIRECT = "patchouli://anilist"

	val authorizeUrl: String get() = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
	val configured: Boolean get() = CLIENT_ID.isNotEmpty()

	/** The name of the account connected, or null. */
	var userName by mutableStateOf<String?>(null)
		private set
	val connected: Boolean get() = token != null
	/** How many episodes wait to be sent. */
	var pendingCount by mutableStateOf(0)
		private set

	private lateinit var prefs: SharedPreferences
	private var token: String? = null
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val flushing = Mutex()

	fun init(context: Context) {
		prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		token = prefs.getString("token", null)
		userName = prefs.getString("user", null)
		pendingCount = pending().size
		if (connected) flush()
	}

	/** A token came back from the login page: kept, and the account's name fetched to show it. */
	suspend fun connect(newToken: String): Boolean {
		val name = runCatching { AniList.viewerName(newToken) }.onFailure { Log.w(TAG, "viewer", it) }.getOrNull() ?: return false
		token = newToken
		userName = name
		prefs.edit { putString("token", newToken); putString("user", name) }
		flush()
		return true
	}

	fun disconnect() {
		token = null
		userName = null
		prefs.edit { remove("token"); remove("user") }
	}

	/** An episode was watched to the end: queued for the list, and sent now if the network is there. */
	fun onWatched(video: Video) {
		if (!connected || !AppPrefs.anilistSync) return
		val episode = video.episode ?: return
		val anime = Library.find(video.folderPath)?.let { Metadata[it] }?.anime ?: return
		if (anime.source == "kitsu") return
		val entry = "${anime.source}:${anime.id}:$episode"
		val queue = pending() + entry
		prefs.edit { putStringSet("pending", queue) }
		pendingCount = queue.size
		flush()
	}

	private fun pending(): Set<String> = prefs.getStringSet("pending", emptySet()).orEmpty()

	/** Sends whatever waits, one by one; a failure leaves the rest for later. */
	fun flush() {
		val auth = token ?: return
		scope.launch {
			flushing.withLock {
				for (entry in pending().sorted()) {
					val parts = entry.split(':')
					val source = parts.getOrNull(0) ?: continue
					val id = parts.getOrNull(1)?.toIntOrNull() ?: continue
					val episode = parts.getOrNull(2)?.toIntOrNull() ?: continue
					try {
						AniList.setProgress(auth, if (source == "anilist") id else null, if (source == "mal") id else null, episode)
						val left = pending() - entry
						prefs.edit { putStringSet("pending", left) }
						pendingCount = left.size
						Log.i(TAG, "sent $entry")
					} catch (e: Exception) {
						Log.w(TAG, "$entry failed: ${e.message}")
						if (e.message?.contains("Invalid token") == true || e.message?.contains("HTTP 401") == true) disconnect()
						break
					}
				}
			}
		}
	}
}
