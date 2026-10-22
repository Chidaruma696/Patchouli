package com.patchouli.app.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.patchouli.app.BuildConfig
import com.patchouli.app.R
import com.patchouli.app.library.Mark
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.PatchouliTheme
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.File

/**
 * libVLC-backed player of local files. Gets a playlist (the videos of a folder, in order) and an
 * index, or a single uri when another app hands us a video. Remembers where each file was left,
 * picks up subtitle files lying next to the video, and shrinks to a small floating window
 * (picture in picture) when asked with the button.
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
	/** True while the activity is the small floating window: no controls, no gestures. */
	var inPip by mutableStateOf(false)
		private set
	/** Subtitle files found next to the current video, in the order they were added. */
	var subtitleFiles by mutableStateOf<List<File>>(emptyList())
		private set

	/** The file on disk being played, or null when another app handed us a content uri (a TV cannot fetch that). */
	val currentFile: String? get() = currentPath

	val count: Int get() = playlist.size
	val hasPrevious: Boolean get() = index > 0
	val hasNext: Boolean get() = index < playlist.lastIndex
	private val currentPath: String? get() = playlist.getOrNull(index)

	private val pickSubtitleLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) addSubtitleFrom(uri)
	}

	/** The play/pause button on the floating window talks back through this. */
	private val pipReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (intent.action != ACTION_PIP) return
			if (player.isPlaying) player.pause() else player.play()
			updatePipParams()
		}
	}

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

		val filter = IntentFilter(ACTION_PIP)
		if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(pipReceiver, filter)

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
		subtitleFiles = path?.let { attachSubtitles(File(it)) }.orEmpty()
		Log.i(TAG, "play ${path ?: contentUri} hw=$hardware from=$start subs=${subtitleFiles.size}")
		if (autoplay) player.play()
	}

	// --- subtitles ---

	/**
	 * Subtitle files lying next to the video: the same name with .srt/.ass/.ssa/.vtt/.sub, or any
	 * such file whose name starts with the video's. The first one is switched on.
	 */
	private fun attachSubtitles(video: File): List<File> {
		val base = video.nameWithoutExtension
		val found = runCatching {
			video.parentFile?.listFiles { f -> f.isFile && f.extension.lowercase() in SUBTITLE_EXTENSIONS && f.nameWithoutExtension.startsWith(base, ignoreCase = true) }
		}.getOrNull().orEmpty().sortedWith(compareBy({ it.nameWithoutExtension.length }, { it.name }))
		found.forEachIndexed { n, f -> player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(f), n == 0) }
		return found
	}

	/** Opens the system picker for a subtitle file anywhere on the phone. */
	fun pickSubtitle() {
		runCatching { pickSubtitleLauncher.launch(arrayOf("*/*")) }.onFailure { Log.w(TAG, "subtitle picker", it) }
	}

	/** A picked document is copied into the cache (libVLC wants a path) and switched on. */
	private fun addSubtitleFrom(uri: Uri) {
		runCatching {
			val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
				val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
				if (c.moveToFirst() && i >= 0) c.getString(i) else null
			} ?: "subtitle.srt"
			val dir = File(cacheDir, "subs").apply { mkdirs() }
			val copy = File(dir, name)
			contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
			player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(copy), true)
			subtitleFiles = subtitleFiles + copy
		}.onFailure { Log.w(TAG, "subtitle from $uri", it) }
	}

	// --- picture in picture ---

	private val pipSupported: Boolean
		get() = Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

	private fun pipParams(): PictureInPictureParams {
		val track = runCatching { player.currentVideoTrack }.getOrNull()
		val ratio = if (track != null && track.width > 0 && track.height > 0) Rational(track.width, track.height) else Rational(16, 9)
		// the system refuses anything wider than 2.39:1 or taller than 1:2.39
		val safe = if (ratio.toFloat() > 2.39f) Rational(239, 100) else if (ratio.toFloat() < 1 / 2.39f) Rational(100, 239) else ratio
		val playing = runCatching { player.isPlaying }.getOrDefault(false)
		val toggle = RemoteAction(
			Icon.createWithResource(this, if (playing) R.drawable.ic_pause else R.drawable.ic_play),
			getString(if (playing) R.string.pause else R.string.play),
			getString(if (playing) R.string.pause else R.string.play),
			PendingIntent.getBroadcast(this, 1, Intent(ACTION_PIP).setPackage(packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
		)
		// never on its own when the user leaves: only from the button
		return PictureInPictureParams.Builder().setAspectRatio(safe).setActions(listOf(toggle)).apply {
			if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(false)
		}.build()
	}

	/** Keeps the system's copy of the params current (the button, the auto-enter flag). */
	fun updatePipParams() {
		if (!pipSupported) return
		runCatching { setPictureInPictureParams(pipParams()) }.onFailure { Log.w(TAG, "pip params", it) }
	}

	/** Shrinks into the floating window now. */
	fun enterPip() {
		if (!pipSupported) return
		runCatching { enterPictureInPictureMode(pipParams()) }.onFailure { Log.w(TAG, "pip", it) }
	}

	override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
		inPip = isInPictureInPictureMode
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
		// the floating window is "paused" to Android but must keep playing
		if (!inPip && ::player.isInitialized && player.isPlaying) player.pause()
	}

	override fun onStop() {
		super.onStop()
		// the floating window was swiped away: we are done
		if (inPip) finish()
	}

	override fun onDestroy() {
		super.onDestroy()
		runCatching { unregisterReceiver(pipReceiver) }
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
		private const val ACTION_PIP = "com.patchouli.app.PIP_TOGGLE"
		private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub")

		fun intent(context: Context, playlist: List<String>, index: Int): Intent =
			Intent(context, PlayerActivity::class.java)
				.putStringArrayListExtra(EXTRA_PLAYLIST, ArrayList(playlist))
				.putExtra(EXTRA_INDEX, index)
	}
}
