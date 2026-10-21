package com.patchouli.app.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.patchouli.app.BuildConfig
import com.patchouli.app.library.Mark
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.PatchouliTheme
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * libVLC-backed player of local files. Gets a playlist (the videos of a folder, in order) and an
 * index, or a single uri when another app hands us a video. Remembers where each file was left.
 */
class PlayerActivity : ComponentActivity() {

	private lateinit var libVlc: LibVLC
	lateinit var player: MediaPlayer
		private set

	private var playlist: List<String> = emptyList()
	private var contentUri: Uri? = null
	private var descriptor: ParcelFileDescriptor? = null

	var index by mutableStateOf(0)
		private set
	var title by mutableStateOf("")
		private set
	var hardware by mutableStateOf(AppPrefs.hardwareDecoder)
		private set
	var orientation by mutableStateOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
		private set

	val count: Int get() = playlist.size
	val hasPrevious: Boolean get() = index > 0
	val hasNext: Boolean get() = index < playlist.lastIndex
	private val currentPath: String? get() = playlist.getOrNull(index)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		WindowInsetsControllerCompat(window, window.decorView).apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}
		requestedOrientation = orientation

		val list = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
		when {
			!list.isNullOrEmpty() -> {
				playlist = list
				index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, list.lastIndex)
			}
			intent.data != null -> {
				val uri = intent.data!!
				if (uri.scheme == "file" && uri.path != null) playlist = listOf(uri.path!!) else contentUri = uri
			}
			else -> { finish(); return }
		}

		val options = arrayListOf("--audio-time-stretch") // keeps the pitch when playing faster
		if (BuildConfig.DEBUG) options += "-vv"
		libVlc = LibVLC(this, options)
		player = MediaPlayer(libVlc)
		load(index, autoplay = false, fromMs = null)

		setContent {
			PatchouliTheme { PlayerScreen(this) }
		}
	}

	/** Points the player at entry [i]; [fromMs] overrides the remembered position (used when reloading). */
	private fun load(i: Int, autoplay: Boolean, fromMs: Long?) {
		saveProgress()
		index = i.coerceIn(0, maxOf(playlist.lastIndex, 0))
		val path = currentPath
		val media = if (contentUri != null) {
			descriptor?.close()
			val pfd = contentResolver.openFileDescriptor(contentUri!!, "r") ?: run { finish(); return }
			descriptor = pfd
			Media(libVlc, pfd.fileDescriptor)
		} else {
			Media(libVlc, path!!)
		}
		media.setHWDecoderEnabled(hardware, false)
		val start = fromMs ?: path?.let { Progress[it] }?.takeIf { AppPrefs.resume && it.started }?.positionMs
		if (start != null && start > Mark.RESUME_MIN_MS) media.addOption(":start-time=${start / 1000.0}")
		player.stop()
		player.media = media
		media.release()
		title = path?.let { Names.title(it.substringAfterLast('/')) } ?: contentUri?.lastPathSegment?.let { Names.title(it) }.orEmpty()
		Log.i(TAG, "play ${path ?: contentUri} hw=$hardware from=$start")
		if (autoplay) player.play()
	}

	fun next() { if (hasNext) load(index + 1, autoplay = true, fromMs = null) }
	fun previous() { if (hasPrevious) load(index - 1, autoplay = true, fromMs = null) }

	/** Switches between hardware and software decoding; the file reopens where it was. */
	fun toggleDecoder() {
		hardware = !hardware
		load(index, autoplay = true, fromMs = player.time.coerceAtLeast(0))
	}

	fun cycleOrientation() {
		orientation = when (orientation) {
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
			else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
		}
		requestedOrientation = orientation
	}

	/** Called when a file ends: the next one, or out. */
	fun onEnded() {
		currentPath?.let { Progress.update(it, player.length, player.length) }
		if (AppPrefs.autoplayNext && hasNext) next() else finish()
	}

	fun saveProgress() {
		if (!::player.isInitialized) return
		val path = currentPath ?: return
		val length = player.length
		val time = player.time
		if (length > 0 && time >= 0) Progress.update(path, time, length)
	}

	override fun onPause() {
		super.onPause()
		saveProgress()
		if (::player.isInitialized && player.isPlaying) player.pause()
	}

	override fun onDestroy() {
		super.onDestroy()
		if (::player.isInitialized) {
			saveProgress()
			player.stop()
			player.detachViews()
			player.release()
			libVlc.release()
		}
		descriptor?.close()
	}

	companion object {
		private const val TAG = "Player"
		private const val EXTRA_PLAYLIST = "playlist"
		private const val EXTRA_INDEX = "index"

		fun intent(context: Context, playlist: List<String>, index: Int): Intent =
			Intent(context, PlayerActivity::class.java)
				.putStringArrayListExtra(EXTRA_PLAYLIST, ArrayList(playlist))
				.putExtra(EXTRA_INDEX, index)
	}
}
